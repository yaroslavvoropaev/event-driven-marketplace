package ru.voropaev.event_driven_marketplace.inventory.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import ru.voropaev.event_driven_marketplace.common.retry.OptimisticLockRetrier;
import ru.voropaev.event_driven_marketplace.inventory.service.InventoryService;
import ru.voropaev.event_driven_marketplace.order.event.OrderCancelled;
import ru.voropaev.event_driven_marketplace.order.event.OrderConfirmed;

@Component
public class InventoryOrderListener {
    private static final Logger log = LoggerFactory.getLogger(InventoryOrderListener.class);
    private final InventoryService inventoryService;
    private final OptimisticLockRetrier retrier;

    public InventoryOrderListener(InventoryService inventoryService, OptimisticLockRetrier retrier) {
        this.inventoryService = inventoryService;
        this.retrier = retrier;
    }


    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(OrderConfirmed event) {
        try {
            retrier.runWithRetry(() -> inventoryService.confirmReservations(event.orderId()));
        } catch (ObjectOptimisticLockingFailureException exception) {
            log.error("Failed to confirm reservations for order {}", event.orderId(), exception);
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(OrderCancelled event) {
        try {
            retrier.runWithRetry(() -> inventoryService.releaseReservations(event.orderId()));
        } catch (ObjectOptimisticLockingFailureException exception) {
            log.error("Failed to release reservations for order {}, "
                + "order is cancelled but stock is still reserved", event.orderId(), exception);
        }
    }
}
