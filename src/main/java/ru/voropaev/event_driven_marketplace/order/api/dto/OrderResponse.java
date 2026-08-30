package ru.voropaev.event_driven_marketplace.order.api.dto;

import ru.voropaev.event_driven_marketplace.order.domain.state.OrderStatus;

import java.math.BigDecimal;
import java.util.UUID;

public record OrderResponse(
        UUID id,
        String customerId,
        OrderStatus orderStatus,
        BigDecimal totalAmount
) {}
