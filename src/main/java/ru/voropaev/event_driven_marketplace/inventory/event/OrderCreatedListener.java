package ru.voropaev.event_driven_marketplace.inventory.event;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import ru.voropaev.event_driven_marketplace.inventory.domain.exception.ReservationFailedException;
import ru.voropaev.event_driven_marketplace.inventory.service.InventoryService;
import ru.voropaev.event_driven_marketplace.order.event.OrderCreated;

import java.time.Instant;

@Component
public class OrderCreatedListener {
    private final InventoryService inventoryService;
    private final ApplicationEventPublisher applicationEventPublisher;

    public OrderCreatedListener(InventoryService inventoryService, ApplicationEventPublisher applicationEventPublisher) {
        this.inventoryService = inventoryService;
        this.applicationEventPublisher = applicationEventPublisher;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(OrderCreated event) {
        try {
            inventoryService.reserveForOrder(event);
            applicationEventPublisher.publishEvent(new InventoryReserved(
                    event.orderId(),
                    Instant.now()
            ));
        } catch (ReservationFailedException exception) {
            applicationEventPublisher.publishEvent(new InventoryReservationFailed(
                    event.orderId(),
                    exception.getMessage(),
                    Instant.now()
            ));
        }

    }
}
