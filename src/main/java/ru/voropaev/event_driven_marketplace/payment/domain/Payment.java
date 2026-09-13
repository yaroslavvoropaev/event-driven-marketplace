package ru.voropaev.event_driven_marketplace.payment.domain;


import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import ru.voropaev.event_driven_marketplace.payment.domain.state.PaymentStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Getter
@Table(name = "payment", schema = "payment_service")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Payment {
    @Id
    private UUID id;

    private UUID orderId;
    private UUID customerId;
    private BigDecimal amount;
    @Enumerated(EnumType.STRING)
    private PaymentStatus paymentStatus;
    private String gatewayTransactionId;
    private String failureReason;

    private Instant createdAt;
    private Instant updatedAt;

    private Payment(UUID orderId, UUID customerId, BigDecimal amount) {
        this.id = UUID.randomUUID();
        this.orderId = orderId;
        this.customerId = customerId;
        this.amount = amount;
        this.paymentStatus = PaymentStatus.PENDING;
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    public static Payment pending(UUID orderId, UUID customerId, BigDecimal amount) {
        return new Payment(orderId, customerId, amount);
    }


    public void applySuccess(PaymentStatus newStatus, String gatewayTransactionId) {
        paymentStatus = newStatus;
        this.gatewayTransactionId = gatewayTransactionId;
        updatedAt = Instant.now();
    }

    public void applyFailure(PaymentStatus newStatus, String failureReason) {
        paymentStatus = newStatus;
        this.failureReason = failureReason;
        updatedAt = Instant.now();
    }
}
