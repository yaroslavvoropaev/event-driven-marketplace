package ru.voropaev.event_driven_marketplace.order.event;

import ru.voropaev.event_driven_marketplace.common.messaging.Message;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record OrderConfirmed(
        UUID orderId,
        UUID customerId,
        BigDecimal totalAmount,
        Instant occurredAt
) implements Message {
}
