package ru.voropaev.event_driven_marketplace.inventory.command;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import ru.voropaev.event_driven_marketplace.common.retry.OptimisticLockRetrier;
import ru.voropaev.event_driven_marketplace.inventory.domain.Stock;
import ru.voropaev.event_driven_marketplace.inventory.domain.exception.InsufficientStockException;
import ru.voropaev.event_driven_marketplace.inventory.event.InventoryReservationFailed;
import ru.voropaev.event_driven_marketplace.inventory.event.InventoryReserved;
import ru.voropaev.event_driven_marketplace.inventory.service.InventoryService;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class InventoryCommandHandlerTest {

    private static final int MAX_ATTEMPTS = 3;
    private static final UUID ORDER_ID = UUID.randomUUID();

    @Mock
    private InventoryService inventoryService;
    @Mock
    private ApplicationEventPublisher applicationEventPublisher;

    private InventoryCommandHandler handler;

    private final ConfirmReservation confirm = new ConfirmReservation(ORDER_ID, Instant.now());
    private final ReleaseInventory release = new ReleaseInventory(ORDER_ID, Instant.now());

    /**
     * Ретраер настоящий, а не мок: повторные попытки — часть наблюдаемого поведения
     * обработчика, а замоканный runWithRetry просто не вызвал бы лямбду.
     * Нулевой backoff, чтобы тесты не спали по-настоящему.
     */
    @BeforeEach
    void setUp() {
        handler = new InventoryCommandHandler(
                applicationEventPublisher,
                inventoryService,
                new OptimisticLockRetrier(MAX_ATTEMPTS, 0)
        );
    }

    private ObjectOptimisticLockingFailureException versionConflict() {
        return new ObjectOptimisticLockingFailureException(Stock.class, UUID.randomUUID());
    }

    private ReserveInventory reserve(int quantity) {
        return new ReserveInventory(
                UUID.randomUUID(),
                List.of(new ReserveInventory.Item(UUID.randomUUID(), quantity)),
                Instant.now()
        );
    }

    // --- ReserveInventory ---

    @Test
    void publishesInventoryReserved_whenReservationSucceeds() {
        ReserveInventory command = reserve(1);

        handler.on(command);

        ArgumentCaptor<InventoryReserved> captor = ArgumentCaptor.forClass(InventoryReserved.class);
        verify(applicationEventPublisher).publishEvent(captor.capture());
        assertEquals(command.orderId(), captor.getValue().orderId());
    }

    @Test
    void publishesInventoryReservationFailed_whenReservationThrows() {
        ReserveInventory command = reserve(5);
        doThrow(new InsufficientStockException(5, 1))
                .when(inventoryService).reserveForOrder(command);

        handler.on(command);

        ArgumentCaptor<InventoryReservationFailed> captor = ArgumentCaptor.forClass(InventoryReservationFailed.class);
        verify(applicationEventPublisher).publishEvent(captor.capture());
        assertEquals(command.orderId(), captor.getValue().orderId());
        assertEquals("Cannot reserve 5, only 1 are available", captor.getValue().reason());
    }

    /**
     * Бизнес-отказ повторять нечего: товара не хватает и на второй попытке.
     * Ретрай здесь только задержал бы ответ.
     */
    @Test
    @Timeout(5)
    void doesNotRetry_whenReservationFailsForBusinessReason() {
        ReserveInventory command = reserve(5);
        doThrow(new InsufficientStockException(5, 1))
                .when(inventoryService).reserveForOrder(command);

        handler.on(command);

        verify(inventoryService, times(1)).reserveForOrder(command);
    }

    /**
     * Проигрыш в гонке за версию строки stock — не отказ, а повод перечитать
     * актуальное состояние и посчитать заново. Со второй попытки резерв проходит.
     */
    @Test
    @Timeout(5)
    void retriesAfterVersionConflict_andPublishesReserved() {
        ReserveInventory command = reserve(1);
        doThrow(versionConflict())
                .doNothing()
                .when(inventoryService).reserveForOrder(command);

        handler.on(command);

        verify(inventoryService, times(2)).reserveForOrder(command);
        verify(applicationEventPublisher).publishEvent(any(InventoryReserved.class));
        verify(applicationEventPublisher, never()).publishEvent(any(InventoryReservationFailed.class));
    }

    /**
     * Ретраи исчерпаны. Резерв так и не сделан, поэтому оркестратору надо сообщить
     * об отказе, иначе заказ навсегда останется в CREATED.
     */
    @Test
    @Timeout(5)
    void publishesReservationFailed_afterMaxAttempts() {
        ReserveInventory command = reserve(1);
        doThrow(versionConflict()).when(inventoryService).reserveForOrder(command);

        handler.on(command);

        verify(inventoryService, times(MAX_ATTEMPTS)).reserveForOrder(command);
        ArgumentCaptor<InventoryReservationFailed> captor = ArgumentCaptor.forClass(InventoryReservationFailed.class);
        verify(applicationEventPublisher).publishEvent(captor.capture());
        assertEquals(command.orderId(), captor.getValue().orderId());
        verify(applicationEventPublisher, never()).publishEvent(any(InventoryReserved.class));
    }

    /**
     * Граница try: публикация InventoryReserved синхронно запускает остаток саги в этом
     * же потоке. Если она окажется внутри try, чужой optimistic lock из оркестратора или
     * payment будет пойман здешним catch и выдан за «не удалось зарезервировать» — хотя
     * резерв прошёл и InventoryReserved уже ушёл. Чужая ошибка должна пройти наружу как есть.
     */
    @Test
    @Timeout(5)
    void doesNotReportReservationFailure_whenDownstreamSagaFails() {
        ReserveInventory command = reserve(1);
        doThrow(versionConflict())
                .when(applicationEventPublisher).publishEvent(any(InventoryReserved.class));

        assertThrows(ObjectOptimisticLockingFailureException.class, () -> handler.on(command));

        verify(inventoryService, times(1)).reserveForOrder(command);
        verify(applicationEventPublisher, never()).publishEvent(any(InventoryReservationFailed.class));
    }

    // --- ConfirmReservation ---

    @Test
    void confirmsReservationsOfTheOrder() {
        handler.on(confirm);

        verify(inventoryService).confirmReservations(ORDER_ID);
        verifyNoInteractions(applicationEventPublisher);
    }

    @Test
    void retriesConfirmAfterVersionConflict_andSucceedsOnSecondAttempt() {
        doThrow(versionConflict())
                .doNothing()
                .when(inventoryService).confirmReservations(ORDER_ID);

        handler.on(confirm);

        verify(inventoryService, times(2)).confirmReservations(ORDER_ID);
    }

    /**
     * Ретраи исчерпаны. Компенсировать нечего — деньги уже списаны, заказ подтверждён, —
     * поэтому обработчик обязан сдаться, записать ошибку и выйти, не пробрасывая исключение
     * в поток оркестратора. Таймаут стоит не для скорости: без выхода из цикла тест бы завис.
     */
    @Test
    @Timeout(5)
    void givesUpConfirm_afterMaxAttempts_withoutPropagating() {
        doThrow(versionConflict()).when(inventoryService).confirmReservations(ORDER_ID);

        handler.on(confirm);

        verify(inventoryService, times(MAX_ATTEMPTS)).confirmReservations(ORDER_ID);
    }

    // --- ReleaseInventory ---

    @Test
    void releasesReservationsOfTheOrder() {
        handler.on(release);

        verify(inventoryService).releaseReservations(ORDER_ID);
        verifyNoInteractions(applicationEventPublisher);
    }

    @Test
    void retriesReleaseAfterVersionConflict_andSucceedsOnSecondAttempt() {
        doThrow(versionConflict())
                .doNothing()
                .when(inventoryService).releaseReservations(ORDER_ID);

        handler.on(release);

        verify(inventoryService, times(2)).releaseReservations(ORDER_ID);
    }

    /**
     * Компенсация не удалась: заказ отменён, а товар висит в резерве. Обработчик сдаётся
     * и оставляет след в логе — чинится это вручную или будущим Outbox'ом.
     */
    @Test
    @Timeout(5)
    void givesUpRelease_afterMaxAttempts_withoutPropagating() {
        doThrow(versionConflict()).when(inventoryService).releaseReservations(ORDER_ID);

        handler.on(release);

        verify(inventoryService, times(MAX_ATTEMPTS)).releaseReservations(ORDER_ID);
    }
}
