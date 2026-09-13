package ru.voropaev.event_driven_marketplace.payment.state;

import org.junit.jupiter.api.Test;
import ru.voropaev.event_driven_marketplace.payment.domain.state.FailedState;
import ru.voropaev.event_driven_marketplace.payment.domain.state.PaymentStateResolver;
import ru.voropaev.event_driven_marketplace.payment.domain.state.PaymentStatus;
import ru.voropaev.event_driven_marketplace.payment.domain.state.PendingState;
import ru.voropaev.event_driven_marketplace.payment.domain.state.SucceedState;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

public class PaymentStateResolverTest {
    private final PaymentStateResolver resolver = new PaymentStateResolver(
            List.of(new PendingState(), new SucceedState(), new FailedState())
    );

    @Test
    void resolvesPending() {
        assertInstanceOf(PendingState.class, resolver.resolve(PaymentStatus.PENDING));
    }

    @Test
    void resolvesSucceeded() {
        assertInstanceOf(SucceedState.class, resolver.resolve(PaymentStatus.SUCCEEDED));
    }

    @Test
    void resolvesFailed() {
        assertInstanceOf(FailedState.class, resolver.resolve(PaymentStatus.FAILED));
    }

}
