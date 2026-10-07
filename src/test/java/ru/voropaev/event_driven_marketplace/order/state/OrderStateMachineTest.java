package ru.voropaev.event_driven_marketplace.order.state;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import ru.voropaev.event_driven_marketplace.order.domain.state.CancelledState;
import ru.voropaev.event_driven_marketplace.order.domain.state.ConfirmedState;
import ru.voropaev.event_driven_marketplace.order.domain.state.CreatedState;
import ru.voropaev.event_driven_marketplace.order.domain.state.OrderState;
import ru.voropaev.event_driven_marketplace.order.domain.state.OrderStateResolver;
import ru.voropaev.event_driven_marketplace.order.domain.state.OrderStatus;
import ru.voropaev.event_driven_marketplace.order.domain.state.PendingState;
import ru.voropaev.event_driven_marketplace.order.domain.state.exception.InvalidOrderTransitionException;

import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static ru.voropaev.event_driven_marketplace.order.domain.state.OrderStatus.CANCELLED;
import static ru.voropaev.event_driven_marketplace.order.domain.state.OrderStatus.CONFIRMED;
import static ru.voropaev.event_driven_marketplace.order.domain.state.OrderStatus.CREATED;
import static ru.voropaev.event_driven_marketplace.order.domain.state.OrderStatus.PENDING;

class OrderStateMachineTest {

    private final OrderStateResolver resolver = new OrderStateResolver(
            List.of(new CreatedState(), new PendingState(), new ConfirmedState(), new CancelledState()));

    enum Action { START_PROCESSING, CONFIRM, CANCEL, CANCEL_BY_CUSTOMER }

    static Stream<Arguments> transitions() {
        return Stream.of(
                arguments(CREATED, Action.START_PROCESSING, PENDING),
                arguments(CREATED, Action.CONFIRM, null),
                arguments(CREATED, Action.CANCEL, CANCELLED),
                arguments(CREATED, Action.CANCEL_BY_CUSTOMER, CANCELLED),

                arguments(PENDING, Action.START_PROCESSING, null),
                arguments(PENDING, Action.CONFIRM, CONFIRMED),
                arguments(PENDING, Action.CANCEL, CANCELLED),
                arguments(PENDING, Action.CANCEL_BY_CUSTOMER, null),

                arguments(CONFIRMED, Action.START_PROCESSING, null),
                arguments(CONFIRMED, Action.CONFIRM, null),
                arguments(CONFIRMED, Action.CANCEL, null),
                arguments(CONFIRMED, Action.CANCEL_BY_CUSTOMER, null),

                arguments(CANCELLED, Action.START_PROCESSING, null),
                arguments(CANCELLED, Action.CONFIRM, null),
                arguments(CANCELLED, Action.CANCEL, null),
                arguments(CANCELLED, Action.CANCEL_BY_CUSTOMER, null)
        );
    }

    @ParameterizedTest(name = "{0}.{1} -> {2}")
    @MethodSource("transitions")
    void followsTransitionTable(OrderStatus from, Action action, OrderStatus expected) {
        OrderState state = resolver.resolve(from);
        assertEquals(from, state.code());

        if (expected == null) {
            assertThrows(InvalidOrderTransitionException.class, () -> apply(state, action));
        } else {
            assertEquals(expected, apply(state, action));
        }
    }

    private OrderStatus apply(OrderState state, Action action) {
        return switch (action) {
            case START_PROCESSING -> state.startProcessing();
            case CONFIRM -> state.confirm();
            case CANCEL -> state.cancel();
            case CANCEL_BY_CUSTOMER -> state.cancelByCustomer();
        };
    }
}
