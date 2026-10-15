package ru.voropaev.event_driven_marketplace.order.event;

import ru.voropaev.event_driven_marketplace.common.messaging.Message;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record OrderCreated(
        UUID orderId,
        UUID customerId,
        BigDecimal totalAmount,
        List<OrderItemPayload> items,
        Instant occurredAt
) implements Message {
    public record OrderItemPayload(UUID productId, int quantity) {}
}
