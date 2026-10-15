package ru.voropaev.event_driven_marketplace.payment.state;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import ru.voropaev.event_driven_marketplace.payment.domain.state.FailedState;
import ru.voropaev.event_driven_marketplace.payment.domain.state.PaymentState;
import ru.voropaev.event_driven_marketplace.payment.domain.state.PaymentStateResolver;
import ru.voropaev.event_driven_marketplace.payment.domain.state.PaymentStatus;
import ru.voropaev.event_driven_marketplace.payment.domain.state.PendingState;
import ru.voropaev.event_driven_marketplace.payment.domain.state.SucceedState;
import ru.voropaev.event_driven_marketplace.payment.domain.state.exception.InvalidPaymentTransitionException;

import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static ru.voropaev.event_driven_marketplace.payment.domain.state.PaymentStatus.FAILED;
import static ru.voropaev.event_driven_marketplace.payment.domain.state.PaymentStatus.PENDING;
import static ru.voropaev.event_driven_marketplace.payment.domain.state.PaymentStatus.SUCCEEDED;

class PaymentStateMachineTest {

    private final PaymentStateResolver resolver = new PaymentStateResolver(
            List.of(new PendingState(), new SucceedState(), new FailedState()));

    enum Action { SUCCEED, FAIL }

    static Stream<Arguments> transitions() {
        return Stream.of(
                arguments(PENDING, Action.SUCCEED, SUCCEEDED),
                arguments(PENDING, Action.FAIL, FAILED),
                arguments(SUCCEEDED, Action.SUCCEED, null),
                arguments(SUCCEEDED, Action.FAIL, null),
                arguments(FAILED, Action.SUCCEED, null),
                arguments(FAILED, Action.FAIL, null)
        );
    }

    @ParameterizedTest(name = "{0}.{1} -> {2}")
    @MethodSource("transitions")
    void followsTransitionTable(PaymentStatus from, Action action, PaymentStatus expected) {
        PaymentState state = resolver.resolve(from);
        assertEquals(from, state.code());

        if (expected == null) {
            assertThrows(InvalidPaymentTransitionException.class, () -> apply(state, action));
        } else {
            assertEquals(expected, apply(state, action));
        }
    }

    private PaymentStatus apply(PaymentState state, Action action) {
        return switch (action) {
            case SUCCEED -> state.succeed();
            case FAIL -> state.fail();
        };
    }
}
