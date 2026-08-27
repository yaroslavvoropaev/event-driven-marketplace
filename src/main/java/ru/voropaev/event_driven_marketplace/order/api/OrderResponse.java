package ru.voropaev.event_driven_marketplace.order.api;

import ru.voropaev.event_driven_marketplace.order.domain.OrderStatus;

import java.math.BigDecimal;
import java.util.UUID;

public record OrderResponse(
        UUID id,
        String customerId,
        OrderStatus orderStatus,
        BigDecimal totalAmount
) {}
