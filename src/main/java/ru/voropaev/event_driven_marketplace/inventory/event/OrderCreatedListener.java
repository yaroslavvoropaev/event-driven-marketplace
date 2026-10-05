package ru.voropaev.event_driven_marketplace.inventory.event;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import ru.voropaev.event_driven_marketplace.common.retry.OptimisticLockRetrier;
import ru.voropaev.event_driven_marketplace.inventory.domain.exception.ReservationFailedException;
import ru.voropaev.event_driven_marketplace.inventory.service.InventoryService;
import ru.voropaev.event_driven_marketplace.order.event.OrderCreated;

import java.time.Instant;

@Component
public class OrderCreatedListener {
    private final InventoryService inventoryService;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final OptimisticLockRetrier retrier;

    public OrderCreatedListener(InventoryService inventoryService, ApplicationEventPublisher applicationEventPublisher, OptimisticLockRetrier retrier) {
        this.inventoryService = inventoryService;
        this.applicationEventPublisher = applicationEventPublisher;
        this.retrier = retrier;
    }


}
