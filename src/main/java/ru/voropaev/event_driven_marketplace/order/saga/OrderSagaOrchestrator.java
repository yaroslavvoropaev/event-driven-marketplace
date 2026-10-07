package ru.voropaev.event_driven_marketplace.order.saga;


import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
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
import ru.voropaev.event_driven_marketplace.order.service.exception.OrderNotFoundException;
import ru.voropaev.event_driven_marketplace.payment.command.ChargePayment;
import ru.voropaev.event_driven_marketplace.payment.event.PaymentCompleted;
import ru.voropaev.event_driven_marketplace.payment.event.PaymentFailed;

import java.time.Instant;
import java.util.UUID;

@Component
public class OrderSagaOrchestrator {
    private final OrderRepository orderRepository;
    private final OrderStateResolver orderStateResolver;
    private final ApplicationEventPublisher applicationEventPublisher;


    public OrderSagaOrchestrator(OrderRepository orderRepository, OrderStateResolver orderStateResolver, ApplicationEventPublisher applicationEventPublisher) {
        this.orderRepository = orderRepository;
        this.orderStateResolver = orderStateResolver;
        this.applicationEventPublisher = applicationEventPublisher;
    }

    @EventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void on(InventoryReserved event) {
        Order order = loadOrder(event.orderId());
        order.updateStatus(
                orderStateResolver.resolve(order.getOrderStatus()).startProcessing()
        );

        applicationEventPublisher.publishEvent(
                new ChargePayment(
                        order.getId(),
                        order.getCustomerId(),
                        order.getTotalAmount(),
                        Instant.now()
                )
        );
    }

    @EventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void on(InventoryReservationFailed event) {
        Order order = loadOrder(event.orderId());
        order.updateStatus(
                orderStateResolver.resolve(order.getOrderStatus()).cancel()
        );

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

    @EventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void on(PaymentCompleted event) {
        Order order = loadOrder(event.orderId());
        order.updateStatus(
                orderStateResolver.resolve(order.getOrderStatus()).confirm()
        );

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


    @EventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void on(PaymentFailed event) {
        Order order = loadOrder(event.orderId());
        order.updateStatus(
                orderStateResolver.resolve(order.getOrderStatus()).cancel()
        );

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

    @EventListener
    public void on(OrderCreated event) {
        applicationEventPublisher.publishEvent(
                new ReserveInventory(
                        event.orderId(),
                        event.items().stream().map(item -> new ReserveInventory.Item(item.productId(), item.quantity())).toList(),
                        Instant.now())
        );
    }


    private Order loadOrder(UUID orderId) {
        return orderRepository.findById(orderId).orElseThrow(
                () -> new OrderNotFoundException(orderId)
        );
    }
}
