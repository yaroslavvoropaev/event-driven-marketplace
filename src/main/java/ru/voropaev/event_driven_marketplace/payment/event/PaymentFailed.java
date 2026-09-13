package ru.voropaev.event_driven_marketplace.payment.event;


import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PaymentFailed(
        UUID orderId,
        UUID paymentId,
        UUID customerId,
        BigDecimal amount,
        String reason,
        Instant occurredAt
) {
}
