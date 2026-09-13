package ru.voropaev.event_driven_marketplace.inventory.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Component;
import ru.voropaev.event_driven_marketplace.common.retry.OptimisticLockRetrier;
import ru.voropaev.event_driven_marketplace.inventory.service.InventoryService;
import ru.voropaev.event_driven_marketplace.payment.event.PaymentCompleted;
import ru.voropaev.event_driven_marketplace.payment.event.PaymentFailed;

@Component
public class InventoryPaymentListener {
    private static final Logger log = LoggerFactory.getLogger(InventoryPaymentListener.class);
    private final InventoryService inventoryService;
    private final OptimisticLockRetrier retrier;

    public InventoryPaymentListener(InventoryService inventoryService, OptimisticLockRetrier retrier) {
        this.inventoryService = inventoryService;
        this.retrier = retrier;
    }


    @EventListener
    public void on(PaymentCompleted event) {
        try {
            retrier.runWithRetry(() -> inventoryService.confirmReservations(event.orderId()));
        } catch (ObjectOptimisticLockingFailureException exception) {
            log.error("Failed to confirm reservations for order {}, "
                    + "payment succeeded but stock is not written off", event.orderId(), exception);
        }
    }


    @EventListener
    public void on(PaymentFailed event) {
        try {
            retrier.runWithRetry(() -> inventoryService.releaseReservations(event.orderId()));
        } catch (ObjectOptimisticLockingFailureException exception) {
            log.error("Failed to release reservations for order {}, "
                + "payment failed but stock is still reserved", event.orderId(), exception);
        }
    }
}
