package ru.voropaev.event_driven_marketplace.payment.service;

import ru.voropaev.event_driven_marketplace.payment.domain.Payment;

import java.math.BigDecimal;
import java.util.UUID;

public interface PaymentService {
    Payment createPending(UUID orderId, UUID customerId, BigDecimal amount);
    void markSucceeded(UUID paymentId, String gatewayTransactionId);
    void markFailed(UUID paymentId, String reason);
}


