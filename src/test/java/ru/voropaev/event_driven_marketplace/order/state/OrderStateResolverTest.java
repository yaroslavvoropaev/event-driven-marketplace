package ru.voropaev.event_driven_marketplace.order.state;

import org.junit.jupiter.api.Test;
import ru.voropaev.event_driven_marketplace.order.domain.state.OrderStateResolver;
import ru.voropaev.event_driven_marketplace.order.domain.state.OrderStatus;
import ru.voropaev.event_driven_marketplace.order.domain.state.CancelledState;
import ru.voropaev.event_driven_marketplace.order.domain.state.ConfirmedState;
import ru.voropaev.event_driven_marketplace.order.domain.state.CreatedState;
import ru.voropaev.event_driven_marketplace.order.domain.state.PendingState;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

public class OrderStateResolverTest {
    private final OrderStateResolver resolver = new OrderStateResolver(
            List.of(new CreatedState(), new PendingState(), new ConfirmedState(), new CancelledState())
    );

    @Test
    void resolvesCreated() {
        assertInstanceOf(CreatedState.class, resolver.resolve(OrderStatus.CREATED));
    }

    @Test
    void resolvesPending() {
        assertInstanceOf(PendingState.class, resolver.resolve(OrderStatus.PENDING));
    }

    @Test
    void resolvesConfirmed() {
        assertInstanceOf(ConfirmedState.class, resolver.resolve(OrderStatus.CONFIRMED));
    }

    @Test
    void resolvesCancelled() {
        assertInstanceOf(CancelledState.class, resolver.resolve(OrderStatus.CANCELLED));
    }

}
