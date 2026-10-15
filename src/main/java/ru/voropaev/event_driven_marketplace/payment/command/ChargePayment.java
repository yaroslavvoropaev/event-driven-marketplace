package ru.voropaev.event_driven_marketplace.payment.command;

import ru.voropaev.event_driven_marketplace.common.messaging.Message;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ChargePayment(
        UUID orderId,
        UUID customerId,
        BigDecimal amount,
        Instant occurredAt
) implements Message {
}
