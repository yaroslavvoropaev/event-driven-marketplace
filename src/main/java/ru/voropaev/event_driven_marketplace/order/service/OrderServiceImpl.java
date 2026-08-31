package ru.voropaev.event_driven_marketplace.order.service;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.voropaev.event_driven_marketplace.order.api.dto.CreateOrderRequest;
import ru.voropaev.event_driven_marketplace.order.api.dto.OrderResponse;
import ru.voropaev.event_driven_marketplace.order.domain.*;
import ru.voropaev.event_driven_marketplace.order.domain.state.OrderStateResolver;
import ru.voropaev.event_driven_marketplace.order.domain.state.OrderStatus;
import ru.voropaev.event_driven_marketplace.order.event.OrderCreated;
import ru.voropaev.event_driven_marketplace.order.repository.OrderRepository;

import java.util.List;
import java.util.UUID;

@Service
public class OrderServiceImpl implements OrderService {

    private final OrderRepository orderRepository;
    private final OrderStateResolver orderStateResolver;
    private final ApplicationEventPublisher applicationEventPublisher;

    public OrderServiceImpl(OrderRepository orderRepository, OrderStateResolver orderStateResolver, ApplicationEventPublisher applicationEventPublisher) {
        this.orderRepository = orderRepository;
        this.orderStateResolver = orderStateResolver;
        this.applicationEventPublisher = applicationEventPublisher;
    }

    @Override
    @Transactional
    public OrderResponse createOrder(CreateOrderRequest request) {
        List<OrderItem> orderItems = request.items().stream()
                .map(item -> new OrderItem(item.productId(), item.quantity(), item.unitPrice()))
                .toList();

        Order order = new Order(request.customerId());

        for (OrderItem orderItem : orderItems) {
            order.addItem(orderItem);
        }

        orderRepository.save(order);
        List<OrderCreated.OrderItemPayload> itemsForEvent = order.getItems().stream()
                .map(item -> new OrderCreated.OrderItemPayload(item.getProductId(), item.getQuantity()))
                .toList();

        applicationEventPublisher.publishEvent(new OrderCreated(
                order.getId(),
                order.getCustomerId(),
                order.getTotalAmount(),
                itemsForEvent,
                order.getCreatedAt())
        );

        return toResponse(order);
    }

    @Override
    public OrderResponse getOrder(UUID id) {
        Order order = getOrderById(id);

        return toResponse(order);

    }

    @Override
    @Transactional
    public OrderResponse cancelOrder(UUID id) {
        Order order = getOrderById(id);
        OrderStatus newStatus = orderStateResolver.resolve(order.getOrderStatus()).cancel();
        order.updateStatus(newStatus);
        return toResponse(order);
    }

    private Order getOrderById(UUID id) {
        return orderRepository.findById(id)
                .orElseThrow(() -> new OrderNotFoundException(id));
    }

    private OrderResponse toResponse(Order order) {
        return new OrderResponse(
                order.getId(),
                order.getCustomerId(),
                order.getOrderStatus(),
                order.getTotalAmount()
        );
    }

}
