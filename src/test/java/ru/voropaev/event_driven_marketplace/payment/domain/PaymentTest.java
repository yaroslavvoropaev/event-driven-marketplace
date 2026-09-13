package ru.voropaev.event_driven_marketplace.payment.domain;

import org.junit.jupiter.api.Test;
import ru.voropaev.event_driven_marketplace.payment.domain.state.PaymentStatus;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

public class PaymentTest {
    private static final UUID ORDER_ID = UUID.randomUUID();
    private static final UUID CUSTOMER_ID = UUID.randomUUID();
    private static final BigDecimal AMOUNT = new BigDecimal("199.99");

    private Payment newPayment() {
        return Payment.pending(ORDER_ID, CUSTOMER_ID, AMOUNT);
    }

    @Test
    void newPaymentIsPendingWithoutGatewayResult() {
        Payment payment = newPayment();

        assertNotNull(payment.getId());
        assertEquals(ORDER_ID, payment.getOrderId());
        assertEquals(CUSTOMER_ID, payment.getCustomerId());
        assertEquals(AMOUNT, payment.getAmount());
        assertEquals(PaymentStatus.PENDING, payment.getPaymentStatus());
        assertNull(payment.getGatewayTransactionId());
        assertNull(payment.getFailureReason());
    }

    @Test
    void newPaymentHasNotBeenModifiedYet() {
        Payment payment = newPayment();

        assertEquals(payment.getCreatedAt(), payment.getUpdatedAt());
    }

    @Test
    void applySuccessStoresGatewayTransactionId() {
        Payment payment = newPayment();

        payment.applySuccess(PaymentStatus.SUCCEEDED, "tx-42");

        assertEquals(PaymentStatus.SUCCEEDED, payment.getPaymentStatus());
        assertEquals("tx-42", payment.getGatewayTransactionId());
        assertNull(payment.getFailureReason());
        assertFalse(payment.getUpdatedAt().isBefore(payment.getCreatedAt()));
    }

    @Test
    void applyFailureStoresReason() {
        Payment payment = newPayment();

        payment.applyFailure(PaymentStatus.FAILED, "insufficient funds");

        assertEquals(PaymentStatus.FAILED, payment.getPaymentStatus());
        assertEquals("insufficient funds", payment.getFailureReason());
        assertNull(payment.getGatewayTransactionId());
        assertFalse(payment.getUpdatedAt().isBefore(payment.getCreatedAt()));
    }

}
