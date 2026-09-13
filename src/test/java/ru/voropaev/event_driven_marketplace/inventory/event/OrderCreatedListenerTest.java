package ru.voropaev.event_driven_marketplace.inventory.event;

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
import ru.voropaev.event_driven_marketplace.inventory.service.InventoryService;
import ru.voropaev.event_driven_marketplace.order.event.OrderCreated;

import java.math.BigDecimal;
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

@ExtendWith(MockitoExtension.class)
class OrderCreatedListenerTest {

    private static final int MAX_ATTEMPTS = 3;

    @Mock
    private InventoryService inventoryService;
    @Mock
    private ApplicationEventPublisher applicationEventPublisher;

    private OrderCreatedListener listener;

    /**
     * Ретраер здесь настоящий, а не мок: повторные попытки — часть наблюдаемого
     * поведения листенера, и замоканный runWithRetry просто не вызвал бы лямбду.
     * Нулевой backoff, чтобы тесты не спали по-настоящему.
     */
    @BeforeEach
    void setUp() {
        listener = new OrderCreatedListener(
                inventoryService,
                applicationEventPublisher,
                new OptimisticLockRetrier(MAX_ATTEMPTS, 0)
        );
    }

    private ObjectOptimisticLockingFailureException versionConflict() {
        return new ObjectOptimisticLockingFailureException(Stock.class, UUID.randomUUID());
    }

    private OrderCreated orderCreated(int quantity) {
        return new OrderCreated(
                UUID.randomUUID(), UUID.randomUUID(), BigDecimal.valueOf(100),
                List.of(new OrderCreated.OrderItemPayload(UUID.randomUUID(), quantity)),
                Instant.now()
        );
    }

    @Test
    void publishesInventoryReserved_whenReservationSucceeds() {
        OrderCreated event = orderCreated(1);

        listener.on(event);

        ArgumentCaptor<InventoryReserved> captor = ArgumentCaptor.forClass(InventoryReserved.class);
        verify(applicationEventPublisher).publishEvent(captor.capture());

        InventoryReserved published = captor.getValue();
        assertEquals(event.orderId(), published.orderId());
        assertEquals(event.customerId(), published.customerId());
        assertEquals(event.totalAmount(), published.totalAmount());
    }

    @Test
    void publishesInventoryReservationFailed_whenReservationThrows() {
        OrderCreated event = orderCreated(5);
        doThrow(new InsufficientStockException(5, 1))
                .when(inventoryService).reserveForOrder(event);

        listener.on(event);

        ArgumentCaptor<InventoryReservationFailed> captor = ArgumentCaptor.forClass(InventoryReservationFailed.class);
        verify(applicationEventPublisher).publishEvent(captor.capture());

        InventoryReservationFailed published = captor.getValue();
        assertEquals(event.orderId(), published.orderId());
        assertEquals("Cannot reserve 5, only 1 are available", published.reason());
    }

    /**
     * Бизнес-отказ повторять нечего: товара не хватает и на второй попытке.
     * Ретрай здесь только задержал бы ответ клиенту.
     */
    @Test
    @Timeout(5)
    void doesNotRetry_whenReservationFailsForBusinessReason() {
        OrderCreated event = orderCreated(5);
        doThrow(new InsufficientStockException(5, 1))
                .when(inventoryService).reserveForOrder(event);

        listener.on(event);

        verify(inventoryService, times(1)).reserveForOrder(event);
    }

    /**
     * Проигрыш в гонке за версию строки stock — не отказ, а повод перечитать
     * актуальное состояние и посчитать заново. Заказ при этом не должен
     * отменяться: со второй попытки резерв проходит.
     */
    @Test
    @Timeout(5)
    void retriesAfterVersionConflict_andPublishesReserved() {
        OrderCreated event = orderCreated(1);
        doThrow(versionConflict())
                .doNothing()
                .when(inventoryService).reserveForOrder(event);

        listener.on(event);

        verify(inventoryService, times(2)).reserveForOrder(event);
        verify(applicationEventPublisher).publishEvent(any(InventoryReserved.class));
        verify(applicationEventPublisher, never()).publishEvent(any(InventoryReservationFailed.class));
    }

    /**
     * Ретраи исчерпаны. Резерв так и не сделан, поэтому саге надо сообщить об отказе,
     * иначе заказ навсегда останется в CREATED.
     */
    @Test
    @Timeout(5)
    void publishesReservationFailed_afterMaxAttempts() {
        OrderCreated event = orderCreated(1);
        doThrow(versionConflict()).when(inventoryService).reserveForOrder(event);

        listener.on(event);

        verify(inventoryService, times(MAX_ATTEMPTS)).reserveForOrder(event);

        ArgumentCaptor<InventoryReservationFailed> captor = ArgumentCaptor.forClass(InventoryReservationFailed.class);
        verify(applicationEventPublisher).publishEvent(captor.capture());
        assertEquals(event.orderId(), captor.getValue().orderId());
        verify(applicationEventPublisher, never()).publishEvent(any(InventoryReserved.class));
    }

    /**
     * Ключевой тест на границу try: публикация InventoryReserved запускает всю
     * оставшуюся сагу синхронно, в этом же потоке. Если она окажется внутри try,
     * то optimistic lock, прилетевший из payment, будет пойман здешним catch и
     * опубликован как "не удалось зарезервировать" — хотя резерв прошёл, и
     * InventoryReserved уже ушёл. Заказ получил бы оба события сразу.
     * Здесь мы требуем: чужая ошибка наружу проходит как есть и причиной отказа
     * резерва не притворяется.
     */
    @Test
    @Timeout(5)
    void doesNotReportReservationFailure_whenDownstreamSagaFails() {
        OrderCreated event = orderCreated(1);
        doThrow(versionConflict())
                .when(applicationEventPublisher).publishEvent(any(InventoryReserved.class));

        assertThrows(ObjectOptimisticLockingFailureException.class, () -> listener.on(event));

        verify(inventoryService, times(1)).reserveForOrder(event);
        verify(applicationEventPublisher, never()).publishEvent(any(InventoryReservationFailed.class));
    }
}
