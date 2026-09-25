package ru.voropaev.event_driven_marketplace.payment.gateway;

import org.junit.jupiter.api.Test;
import ru.voropaev.event_driven_marketplace.payment.gateway.exception.PaymentDeclinedException;
import ru.voropaev.event_driven_marketplace.payment.gateway.exception.PaymentGatewayUnavailableException;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class FakePaymentGatewayTest {
    private static final UUID PAYMENT_ID = UUID.randomUUID();
    private static final UUID CUSTOMER_ID = UUID.randomUUID();

    private final FakePaymentGateway gateway = new FakePaymentGateway();

    private String charge(String amount) {
        return gateway.charge(PAYMENT_ID, CUSTOMER_ID, new BigDecimal(amount));
    }

    @Test
    void chargeSucceedsForOrdinaryAmount() {
        assertEquals("fake-tx-" + PAYMENT_ID, charge("199.00"));
    }

    @Test
    void transactionIdCarriesPaymentId() {
        assertTrue(charge("50.00").contains(PAYMENT_ID.toString()));
    }

    @Test
    void chargeIsDeclinedWhenCentsAre13() {
        assertThrows(PaymentDeclinedException.class, () -> charge("199.13"));
    }

    @Test
    void gatewayIsUnavailableWhenCentsAre99() {
        assertThrows(PaymentGatewayUnavailableException.class, () -> charge("199.99"));
    }

    @Test
    void rulesApplyToAmountsBelowOne() {
        assertThrows(PaymentDeclinedException.class, () -> charge("0.13"));
        assertThrows(PaymentGatewayUnavailableException.class, () -> charge("0.99"));
    }

    @Test
    void trailingZeroDoesNotChangeTheOutcome() {
        assertEquals("fake-tx-" + PAYMENT_ID, charge("199.10"));
        assertEquals("fake-tx-" + PAYMENT_ID, charge("199.1"));
    }

    @Test
    void extraScaleDoesNotChangeTheOutcome() {
        assertThrows(PaymentDeclinedException.class, () -> charge("199.130"));
        assertThrows(PaymentGatewayUnavailableException.class, () -> charge("199.990"));
    }

    @Test
    void wholeAmountSucceeds() {
        assertEquals("fake-tx-" + PAYMENT_ID, charge("200"));
    }

}
