package ru.voropaev.event_driven_marketplace.inventory.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Component;
import ru.voropaev.event_driven_marketplace.inventory.service.InventoryService;
import ru.voropaev.event_driven_marketplace.payment.event.PaymentCompleted;

@Component
public class InventoryPaymentListener {
    private static final int MAX_ATTEMPTS = 3;
    private static final Logger log = LoggerFactory.getLogger(InventoryPaymentListener.class);
    private final InventoryService inventoryService;

    public InventoryPaymentListener(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @EventListener
    public void on(PaymentCompleted event) {
        int attempts = 0;

        while (true) {
            attempts++;
            try {
                inventoryService.confirmReservations(event.orderId());
                return;
            } catch (ObjectOptimisticLockingFailureException exception) {
                if (attempts >= MAX_ATTEMPTS) {
                    log.error("Failed to confirm reservations for order {} after {} attempts, "
                                + "payment succeeded but stock is not written off", event.orderId(), attempts, exception);
                    return;
                }
            }
        }
    }
}
