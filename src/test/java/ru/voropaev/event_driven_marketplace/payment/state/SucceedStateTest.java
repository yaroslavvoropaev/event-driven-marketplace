package ru.voropaev.event_driven_marketplace.payment.state;

import org.junit.jupiter.api.Test;
import ru.voropaev.event_driven_marketplace.payment.domain.state.PaymentStatus;
import ru.voropaev.event_driven_marketplace.payment.domain.state.SucceedState;
import ru.voropaev.event_driven_marketplace.payment.domain.state.exception.InvalidPaymentTransitionException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SucceedStateTest {
    private final SucceedState state = new SucceedState();

    @Test
    void codeIsSucceeded() {
        assertEquals(PaymentStatus.SUCCEEDED, state.code());
    }

    @Test
    void succeedIsNotAllowed() {
        InvalidPaymentTransitionException exception =
                assertThrows(InvalidPaymentTransitionException.class, state::succeed);

        assertTrue(exception.getMessage().contains(PaymentStatus.SUCCEEDED.name()));
    }

    @Test
    void failIsNotAllowed() {
        InvalidPaymentTransitionException exception =
                assertThrows(InvalidPaymentTransitionException.class, state::fail);

        assertTrue(exception.getMessage().contains(PaymentStatus.SUCCEEDED.name()));
    }

}
