package ru.voropaev.event_driven_marketplace.order.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.voropaev.event_driven_marketplace.inventory.domain.exception.StockNotFoundException;
import ru.voropaev.event_driven_marketplace.inventory.service.InventoryService;
import ru.voropaev.event_driven_marketplace.order.api.dto.CreateOrderRequest;
import ru.voropaev.event_driven_marketplace.order.api.dto.OrderResponse;
import ru.voropaev.event_driven_marketplace.order.domain.*;
import ru.voropaev.event_driven_marketplace.order.domain.state.OrderStateResolver;
import ru.voropaev.event_driven_marketplace.order.domain.state.OrderStatus;
import ru.voropaev.event_driven_marketplace.order.event.CancellationReason;
import ru.voropaev.event_driven_marketplace.order.event.OrderCancelled;
import ru.voropaev.event_driven_marketplace.order.event.OrderCreated;
import ru.voropaev.event_driven_marketplace.order.repository.OrderRepository;
import ru.voropaev.event_driven_marketplace.order.service.exception.OrderNotFoundException;
import ru.voropaev.event_driven_marketplace.order.service.exception.UnknownProductException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
public class OrderServiceImpl implements OrderService {

    private final OrderRepository orderRepository;
    private final OrderStateResolver orderStateResolver;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final InventoryService inventoryService;

    public OrderServiceImpl(OrderRepository orderRepository, OrderStateResolver orderStateResolver, ApplicationEventPublisher applicationEventPublisher, InventoryService inventoryService) {
        this.orderRepository = orderRepository;
        this.orderStateResolver = orderStateResolver;
        this.applicationEventPublisher = applicationEventPublisher;
        this.inventoryService = inventoryService;
    }

    @Override
    @Transactional
    public OrderResponse createOrder(UUID customerId, CreateOrderRequest request) {
        List<OrderItem> orderItems = request.items().stream()
                .map(item -> new OrderItem(item.productId(), item.quantity(), priceOf(item.productId())))
                .toList();

        Order order = new Order(customerId);

        for (OrderItem orderItem : orderItems) {
            order.addItem(orderItem);
        }

        orderRepository.save(order);
        List<OrderCreated.OrderItemPayload> itemsForEvent = order.getItems().stream()
                .map(item -> new OrderCreated.OrderItemPayload(item.getProductId(), item.getQuantity()))
                .toList();

        applicationEventPublisher.publishEvent(new OrderCreated(
                order.getId(),
                customerId,
                order.getTotalAmount(),
                itemsForEvent,
                order.getCreatedAt()
        ));

        return toResponse(order);
    }

    @Override
    @Transactional(readOnly = true)
    public OrderResponse getOrder(UUID id, UUID customerId) {
        Order order = getOwnOrderById(id, customerId);

        return toResponse(order);

    }

    @Override
    @Transactional
    public OrderResponse cancelOrder(UUID id, UUID customerId) {
        Order order = getOwnOrderById(id, customerId);
        OrderStatus newStatus = orderStateResolver.resolve(order.getOrderStatus()).cancelByCustomer();
        return doCancel(order, newStatus, CancellationReason.CUSTOMER_REQUEST);
    }


    private OrderResponse doCancel(Order order, OrderStatus newStatus, CancellationReason reason) {
        order.updateStatus(newStatus);
        applicationEventPublisher.publishEvent(new OrderCancelled(
                order.getId(),
                order.getCustomerId(),
                order.getTotalAmount(),
                reason,
                Instant.now()
        ));
        return toResponse(order);
    }

    private BigDecimal priceOf(UUID productId) {
        try {
            return inventoryService.getPrice(productId);
        } catch (StockNotFoundException exception) {
            throw new UnknownProductException(productId);
        }
    }

    private Order getOrderById(UUID id) {
        return orderRepository.findById(id)
                .orElseThrow(() -> new OrderNotFoundException(id));
    }

    private Order getOwnOrderById(UUID id, UUID customerId) {
        Order order = getOrderById(id);
        if (!order.getCustomerId().equals(customerId)) {
            log.warn("Customer {} attempted to access order {} owned by {}",
                    customerId, id, order.getCustomerId());
            throw new OrderNotFoundException(id);
        }
        return order;
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
