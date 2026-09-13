package ru.voropaev.event_driven_marketplace.payment.state;

import org.junit.jupiter.api.Test;
import ru.voropaev.event_driven_marketplace.payment.domain.state.PaymentStatus;
import ru.voropaev.event_driven_marketplace.payment.domain.state.PendingState;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class PendingStateTest {
    private final PendingState state = new PendingState();

    @Test
    void codeIsPending() {
        assertEquals(PaymentStatus.PENDING, state.code());
    }

    @Test
    void succeedMovesToSucceeded() {
        assertEquals(PaymentStatus.SUCCEEDED, state.succeed());
    }

    @Test
    void failMovesToFailed() {
        assertEquals(PaymentStatus.FAILED, state.fail());
    }

}
