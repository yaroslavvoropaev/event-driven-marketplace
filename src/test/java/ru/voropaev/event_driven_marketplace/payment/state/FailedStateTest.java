package ru.voropaev.event_driven_marketplace.payment.state;

import org.junit.jupiter.api.Test;
import ru.voropaev.event_driven_marketplace.payment.domain.state.FailedState;
import ru.voropaev.event_driven_marketplace.payment.domain.state.PaymentStatus;
import ru.voropaev.event_driven_marketplace.payment.domain.state.exception.InvalidPaymentTransitionException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class FailedStateTest {
    private final FailedState state = new FailedState();

    @Test
    void codeIsFailed() {
        assertEquals(PaymentStatus.FAILED, state.code());
    }

    @Test
    void succeedIsNotAllowed() {
        InvalidPaymentTransitionException exception =
                assertThrows(InvalidPaymentTransitionException.class, state::succeed);

        assertTrue(exception.getMessage().contains(PaymentStatus.FAILED.name()));
    }

    @Test
    void failIsNotAllowed() {
        InvalidPaymentTransitionException exception =
                assertThrows(InvalidPaymentTransitionException.class, state::fail);

        assertTrue(exception.getMessage().contains(PaymentStatus.FAILED.name()));
    }

}
