package ru.voropaev.event_driven_marketplace.payment.gateway;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ru.voropaev.event_driven_marketplace.payment.gateway.exception.PaymentDeclinedException;
import ru.voropaev.event_driven_marketplace.payment.gateway.exception.PaymentGatewayUnavailableException;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FakePaymentGatewayTest {

    private static final UUID PAYMENT_ID = UUID.randomUUID();
    private static final UUID CUSTOMER_ID = UUID.randomUUID();

    private final FakePaymentGateway gateway = new FakePaymentGateway();

    enum Outcome { CHARGED, DECLINED, UNAVAILABLE }

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({
            "199.00, CHARGED",
            "200, CHARGED",
            "199.1, CHARGED",
            "199.10, CHARGED",
            "199.13, DECLINED",
            "199.130, DECLINED",
            "0.13, DECLINED",
            "199.99, UNAVAILABLE",
            "199.990, UNAVAILABLE",
            "0.99, UNAVAILABLE"
    })
    void decidesOutcomeByCents(String amount, Outcome outcome) {
        BigDecimal value = new BigDecimal(amount);
        switch (outcome) {
            case CHARGED -> assertEquals("fake-tx-" + PAYMENT_ID, gateway.charge(PAYMENT_ID, CUSTOMER_ID, value));
            case DECLINED -> assertThrows(PaymentDeclinedException.class,
                    () -> gateway.charge(PAYMENT_ID, CUSTOMER_ID, value));
            case UNAVAILABLE -> assertThrows(PaymentGatewayUnavailableException.class,
                    () -> gateway.charge(PAYMENT_ID, CUSTOMER_ID, value));
        }
    }
}
