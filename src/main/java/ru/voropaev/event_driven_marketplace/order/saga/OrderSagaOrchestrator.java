package ru.voropaev.event_driven_marketplace.order.saga;


import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import ru.voropaev.event_driven_marketplace.common.retry.OptimisticLockRetrier;
import ru.voropaev.event_driven_marketplace.inventory.command.ConfirmReservation;
import ru.voropaev.event_driven_marketplace.inventory.command.ReleaseInventory;
import ru.voropaev.event_driven_marketplace.inventory.command.ReserveInventory;
import ru.voropaev.event_driven_marketplace.inventory.event.InventoryReservationFailed;
import ru.voropaev.event_driven_marketplace.inventory.event.InventoryReserved;
import ru.voropaev.event_driven_marketplace.order.domain.Order;
import ru.voropaev.event_driven_marketplace.order.domain.state.OrderStateResolver;
import ru.voropaev.event_driven_marketplace.order.event.CancellationReason;
import ru.voropaev.event_driven_marketplace.order.event.OrderCancelled;
import ru.voropaev.event_driven_marketplace.order.event.OrderConfirmed;
import ru.voropaev.event_driven_marketplace.order.event.OrderCreated;
import ru.voropaev.event_driven_marketplace.order.repository.OrderRepository;
import ru.voropaev.event_driven_marketplace.payment.command.ChargePayment;
import ru.voropaev.event_driven_marketplace.payment.event.PaymentCompleted;
import ru.voropaev.event_driven_marketplace.payment.event.PaymentFailed;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Component
@Slf4j
public class OrderSagaOrchestrator {
    private final OrderRepository orderRepository;
    private final OrderStateResolver orderStateResolver;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final OptimisticLockRetrier retrier;
    private final TransactionTemplate transactionTemplate;

    public OrderSagaOrchestrator(OrderRepository orderRepository,
                                 OrderStateResolver orderStateResolver,
                                 ApplicationEventPublisher applicationEventPublisher,
                                 OptimisticLockRetrier retrier,
                                 PlatformTransactionManager transactionManager) {
        this.orderRepository = orderRepository;
        this.orderStateResolver = orderStateResolver;
        this.applicationEventPublisher = applicationEventPublisher;

        this.retrier = retrier;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }



    @EventListener
    public void on(InventoryReserved event) {
        runStep("InventoryReserved", event.orderId(), () -> handle(event));
    }


    @EventListener
    public void on(InventoryReservationFailed event) {
        runStep("InventoryReservationFailed", event.orderId(), () -> handle(event));
    }


    @EventListener
    public void on(PaymentCompleted event) {
        runStep("PaymentCompleted", event.orderId(), () -> handle(event));
    }


    @EventListener
    public void on(PaymentFailed event) {
        runStep("PaymentFailed", event.orderId(), () -> handle(event));
    }

    @EventListener
    public void on(OrderCreated event) {
        applicationEventPublisher.publishEvent(
                new ReserveInventory(
                        event.orderId(),
                        event.items().stream().map(item -> new ReserveInventory.Item(item.productId(), item.quantity())).toList(),
                        Instant.now())
        );
    }


    private Optional<Order> findOrder(String replyType, UUID orderId) {
        Optional<Order> order = orderRepository.findById(orderId);
        if (order.isEmpty()) {
            log.error("{} for unknown order {}, dropped", replyType, orderId);
        }
        return order;
    }

    private void runStep(String replyType, UUID orderId, Runnable step) {
        try {
            retrier.runWithRetry(() -> transactionTemplate.executeWithoutResult(_ -> step.run()));
        } catch (ObjectOptimisticLockingFailureException exception) {
            log.error("{} for order {} not processed: version conflict, retries exhausted",
                    replyType, orderId, exception);
        }
    }

    private void handle(InventoryReserved event) {
        Optional<Order> found = findOrder("InventoryReserved", event.orderId());
        if (found.isEmpty()) {
            return;
        }
        Order order = found.get();

        switch (order.getOrderStatus()) {
            case CREATED -> {
                order.updateStatus(orderStateResolver.resolve(order.getOrderStatus()).startProcessing());
                applicationEventPublisher.publishEvent(
                        new ChargePayment(
                                order.getId(),
                                order.getCustomerId(),
                                order.getTotalAmount(),
                                Instant.now()
                        )
                );
            }
            case PENDING, CONFIRMED ->
                    log.info("Duplicate InventoryReserved for order {} in {}, ignored",
                            order.getId(), order.getOrderStatus());
            case CANCELLED -> {
                log.warn("Late InventoryReserved for cancelled order {}, releasing stock", order.getId());
                applicationEventPublisher.publishEvent(new ReleaseInventory(order.getId(), Instant.now()));
            }
        }
    }

    private void handle(InventoryReservationFailed event) {
        Optional<Order> found = findOrder("InventoryReservationFailed", event.orderId());
        if (found.isEmpty()) {
            return;
        }
        Order order = found.get();

        switch (order.getOrderStatus()) {
            case CREATED -> {
                order.updateStatus(orderStateResolver.resolve(order.getOrderStatus()).cancel());
                applicationEventPublisher.publishEvent(
                        new OrderCancelled(
                                order.getId(),
                                order.getCustomerId(),
                                order.getTotalAmount(),
                                CancellationReason.RESERVATION_FAILED,
                                Instant.now()
                        )
                );
            }
            case CANCELLED ->
                    log.info("Duplicate InventoryReservationFailed for order {} in {}, ignored",
                            order.getId(), order.getOrderStatus());
            case PENDING, CONFIRMED ->
                    log.error("InventoryReservationFailed for order {} in {} contradicts its reservation, ignored",
                            order.getId(), order.getOrderStatus());
        }
    }

    private void handle(PaymentCompleted event) {
        Optional<Order> found = findOrder("PaymentCompleted", event.orderId());
        if (found.isEmpty()) {
            return;
        }
        Order order = found.get();

        switch (order.getOrderStatus()) {
            case PENDING -> {
                order.updateStatus(orderStateResolver.resolve(order.getOrderStatus()).confirm());
                applicationEventPublisher.publishEvent(
                        new OrderConfirmed(
                                order.getId(),
                                order.getCustomerId(),
                                order.getTotalAmount(),
                                Instant.now()
                        )
                );
                applicationEventPublisher.publishEvent(
                        new ConfirmReservation(
                                order.getId(),
                                Instant.now()
                        )
                );
            }
            case CONFIRMED ->
                    log.info("Duplicate PaymentCompleted for order {} in {}, ignored",
                            order.getId(), order.getOrderStatus());
            case CREATED ->
                    log.error("PaymentCompleted for order {} in {}: payment was never requested, ignored",
                            order.getId(), order.getOrderStatus());
            case CANCELLED ->
                    log.error("PaymentCompleted for cancelled order {}: payment {} charged, refund required",
                            order.getId(), event.paymentId());
        }
    }

    private void handle(PaymentFailed event) {
        Optional<Order> found = findOrder("PaymentFailed", event.orderId());
        if (found.isEmpty()) {
            return;
        }
        Order order = found.get();

        switch (order.getOrderStatus()) {
            case PENDING -> {
                order.updateStatus(orderStateResolver.resolve(order.getOrderStatus()).cancel());
                applicationEventPublisher.publishEvent(
                        new OrderCancelled(
                                order.getId(),
                                order.getCustomerId(),
                                order.getTotalAmount(),
                                CancellationReason.PAYMENT_FAILED,
                                Instant.now()
                        )
                );
                applicationEventPublisher.publishEvent(
                        new ReleaseInventory(
                                order.getId(),
                                Instant.now()
                        )
                );
            }
            case CANCELLED ->
                    log.info("Duplicate PaymentFailed for order {} in {}, ignored",
                            order.getId(), order.getOrderStatus());
            case CREATED, CONFIRMED ->
                    log.error("PaymentFailed for order {} in {} contradicts saga state, ignored",
                            order.getId(), order.getOrderStatus());
        }
    }
}
