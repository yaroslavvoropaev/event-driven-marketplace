package ru.voropaev.event_driven_marketplace.order;

import org.junit.jupiter.api.Test;
import ru.voropaev.event_driven_marketplace.order.domain.InvalidOrderTransitionException;
import ru.voropaev.event_driven_marketplace.order.domain.OrderStatus;
import ru.voropaev.event_driven_marketplace.order.domain.state.CancelledState;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class CancelledStateTest {
    private final CancelledState state = new CancelledState();

    @Test
    void codeIsCancelled() {
        assertEquals(OrderStatus.CANCELLED, state.code());
    }

    @Test
    void confirmIsNotAllowed() {
        assertThrows(InvalidOrderTransitionException.class, state::confirm);
    }

    @Test
    void cancelIsNotAllowed() {
        assertThrows(InvalidOrderTransitionException.class, state::cancel);
    }

    @Test
    void startProcessingIsNotAllowed() {
        assertThrows(InvalidOrderTransitionException.class, state::startProcessing);
    }

}
