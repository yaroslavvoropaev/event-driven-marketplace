package ru.voropaev.event_driven_marketplace.order.service;

import ru.voropaev.event_driven_marketplace.order.api.CreateOrderRequest;
import ru.voropaev.event_driven_marketplace.order.api.OrderResponse;

import java.util.UUID;

public interface OrderService {
    OrderResponse createOrder(CreateOrderRequest request);
    OrderResponse getOrder(UUID id);
    OrderResponse cancelOrder(UUID id);
}
