package ru.voropaev.event_driven_marketplace.inventory.command;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import ru.voropaev.event_driven_marketplace.common.retry.OptimisticLockRetrier;
import ru.voropaev.event_driven_marketplace.inventory.domain.exception.ReservationFailedException;
import ru.voropaev.event_driven_marketplace.inventory.event.InventoryOrderListener;
import ru.voropaev.event_driven_marketplace.inventory.event.InventoryReservationFailed;
import ru.voropaev.event_driven_marketplace.inventory.event.InventoryReserved;
import ru.voropaev.event_driven_marketplace.inventory.service.InventoryService;
import ru.voropaev.event_driven_marketplace.order.event.OrderCancelled;
import ru.voropaev.event_driven_marketplace.order.event.OrderConfirmed;
import ru.voropaev.event_driven_marketplace.order.event.OrderCreated;

import java.time.Instant;

@Slf4j
@Component
public class InventoryCommandHandler {

    private final ApplicationEventPublisher applicationEventPublisher;
    private final InventoryService inventoryService;
    private final OptimisticLockRetrier retrier;

    public InventoryCommandHandler(ApplicationEventPublisher applicationEventPublisher, InventoryService inventoryService, OptimisticLockRetrier retrier) {
        this.applicationEventPublisher = applicationEventPublisher;
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

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(OrderCreated event) {
        try {
            retrier.runWithRetry(() -> inventoryService.reserveForOrder(event));
        } catch (ObjectOptimisticLockingFailureException exception) {
            applicationEventPublisher.publishEvent(new InventoryReservationFailed(
                    event.orderId(),
                    "Concurrent modification while reserving stock, retries exhausted",
                    Instant.now()
            ));
            return;
        } catch (ReservationFailedException exception) {
            applicationEventPublisher.publishEvent(new InventoryReservationFailed(
                    event.orderId(),
                    exception.getMessage(),
                    Instant.now()
            ));
            return;
        }

        applicationEventPublisher.publishEvent(new InventoryReserved(
                event.orderId(),
                Instant.now()
        ));
    }
}
