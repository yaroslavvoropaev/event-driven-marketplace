package ru.voropaev.event_driven_marketplace.payment.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PaymentCompleted(
        UUID orderId,
        UUID paymentId,
        UUID customerId,
        BigDecimal amount,
        String gatewayTransactionId,
        Instant occurredAt
) {
}
