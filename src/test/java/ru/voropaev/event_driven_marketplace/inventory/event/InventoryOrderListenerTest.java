package ru.voropaev.event_driven_marketplace.inventory.event;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import ru.voropaev.event_driven_marketplace.common.retry.OptimisticLockRetrier;
import ru.voropaev.event_driven_marketplace.inventory.domain.Stock;
import ru.voropaev.event_driven_marketplace.inventory.service.InventoryService;
import ru.voropaev.event_driven_marketplace.order.event.CancellationReason;
import ru.voropaev.event_driven_marketplace.order.event.OrderCancelled;
import ru.voropaev.event_driven_marketplace.order.event.OrderConfirmed;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class InventoryOrderListenerTest {

    private static final int MAX_ATTEMPTS = 3;

    private static final UUID ORDER_ID = UUID.randomUUID();

    @Mock
    private InventoryService inventoryService;

    private InventoryOrderListener listener;

    /**
     * Ретраер настоящий, а не мок: число попыток — часть наблюдаемого поведения
     * листенера, а замоканный runWithRetry не вызвал бы лямбду вовсе.
     * Нулевой backoff, чтобы тесты не спали.
     */
    @BeforeEach
    void setUp() {
        listener = new InventoryOrderListener(
                inventoryService,
                new OptimisticLockRetrier(MAX_ATTEMPTS, 0)
        );
    }

    /**
     * Резерв списывается и отпускается по событиям order, а не payment: inventory
     * реагирует на судьбу заказа, а платёж — лишь одна из её причин.
     */
    private final OrderConfirmed event = new OrderConfirmed(
            ORDER_ID,
            UUID.randomUUID(),
            new BigDecimal("199.00"),
            Instant.now()
    );

    private final OrderCancelled orderCancelled = new OrderCancelled(
            ORDER_ID,
            UUID.randomUUID(),
            new BigDecimal("199.13"),
            CancellationReason.PAYMENT_FAILED,
            Instant.now()
    );

    private ObjectOptimisticLockingFailureException versionConflict() {
        return new ObjectOptimisticLockingFailureException(Stock.class, UUID.randomUUID());
    }

    @Test
    void confirmsReservationsOfThePaidOrder() {
        listener.on(event);

        verify(inventoryService).confirmReservations(ORDER_ID);
    }

    @Test
    void retriesAfterVersionConflict_andSucceedsOnSecondAttempt() {
        doThrow(versionConflict())
                .doNothing()
                .when(inventoryService).confirmReservations(ORDER_ID);

        listener.on(event);

        verify(inventoryService, times(2)).confirmReservations(ORDER_ID);
    }

    /**
     * Ретраи исчерпаны. Компенсировать нечего — деньги уже списаны, заказ подтверждается
     * параллельно, — поэтому листенер обязан сдаться, записать ошибку и выйти.
     * Таймаут стоит не для скорости: без выхода из цикла тест не упал бы, а завис.
     */
    @Test
    @Timeout(5)
    void givesUpAfterMaxAttempts_whenVersionConflictPersists() {
        doThrow(versionConflict()).when(inventoryService).confirmReservations(ORDER_ID);

        listener.on(event);

        verify(inventoryService, times(MAX_ATTEMPTS)).confirmReservations(ORDER_ID);
    }

    @Test
    void releasesReservationsOfTheCancelledOrder() {
        listener.on(orderCancelled);

        verify(inventoryService).releaseReservations(ORDER_ID);
    }

    @Test
    void retriesReleaseAfterVersionConflict_andSucceedsOnSecondAttempt() {
        doThrow(versionConflict())
                .doNothing()
                .when(inventoryService).releaseReservations(ORDER_ID);

        listener.on(orderCancelled);

        verify(inventoryService, times(2)).releaseReservations(ORDER_ID);
    }

    /**
     * Компенсация не удалась. Выхода нет: заказ отменён, а товар остаётся висеть
     * в резерве и никому не доступен. Листенер обязан сдаться и оставить след в
     * логе — чинится это только вручную или будущим Outbox'ом.
     */
    @Test
    @Timeout(5)
    void givesUpRelease_afterMaxAttempts() {
        doThrow(versionConflict()).when(inventoryService).releaseReservations(ORDER_ID);

        listener.on(orderCancelled);

        verify(inventoryService, times(MAX_ATTEMPTS)).releaseReservations(ORDER_ID);
    }

    @Test
    @Timeout(5)
    void doesNotPropagateExceptionToThePublisher() {
        doThrow(versionConflict()).when(inventoryService).confirmReservations(ORDER_ID);

        // исключение не должно выйти наружу: оно бы поднялось в поток издателя события
        // и сломало бы обработку у остальных подписчиков PaymentCompleted
        listener.on(event);
    }

}
