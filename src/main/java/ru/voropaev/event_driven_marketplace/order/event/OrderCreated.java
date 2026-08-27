package ru.voropaev.event_driven_marketplace.order.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record OrderCreated(
        UUID orderId,
        String customerId,
        BigDecimal totalAmount,
        List<OrderItemPayload> items,
        Instant occurredAt
) {
    public record OrderItemPayload(UUID productId, int quantity) {}
}
