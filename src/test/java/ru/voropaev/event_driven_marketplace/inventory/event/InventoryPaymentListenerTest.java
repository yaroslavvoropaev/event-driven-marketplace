package ru.voropaev.event_driven_marketplace.inventory.event;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import ru.voropaev.event_driven_marketplace.inventory.domain.Stock;
import ru.voropaev.event_driven_marketplace.inventory.service.InventoryService;
import ru.voropaev.event_driven_marketplace.payment.event.PaymentCompleted;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class InventoryPaymentListenerTest {

    private static final int MAX_ATTEMPTS = 3;

    private static final UUID ORDER_ID = UUID.randomUUID();

    @Mock
    private InventoryService inventoryService;

    @InjectMocks
    private InventoryPaymentListener listener;

    private final PaymentCompleted event = new PaymentCompleted(
            ORDER_ID,
            UUID.randomUUID(),
            UUID.randomUUID(),
            new BigDecimal("199.00"),
            "fake-tx-1",
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
    @Timeout(5)
    void doesNotPropagateExceptionToThePublisher() {
        doThrow(versionConflict()).when(inventoryService).confirmReservations(ORDER_ID);

        // исключение не должно выйти наружу: оно бы поднялось в поток издателя события
        // и сломало бы обработку у остальных подписчиков PaymentCompleted
        listener.on(event);
    }

}
