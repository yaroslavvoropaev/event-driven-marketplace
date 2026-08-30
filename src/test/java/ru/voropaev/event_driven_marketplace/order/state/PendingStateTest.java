package ru.voropaev.event_driven_marketplace.order.state;

import org.junit.jupiter.api.Test;
import ru.voropaev.event_driven_marketplace.order.domain.InvalidOrderTransitionException;
import ru.voropaev.event_driven_marketplace.order.domain.OrderStatus;
import ru.voropaev.event_driven_marketplace.order.domain.state.PendingState;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class PendingStateTest {
    private final PendingState state = new PendingState();

    @Test
    void codeIsPending() {
        assertEquals(OrderStatus.PENDING, state.code());
    }

    @Test
    void confirmMovesToConfirmed() {
        assertEquals(OrderStatus.CONFIRMED, state.confirm());
    }

    @Test
    void cancelMovesToCancelled() {
        assertEquals(OrderStatus.CANCELLED, state.cancel());
    }

    @Test
    void startProcessingIsNotAllowed() {
        assertThrows(InvalidOrderTransitionException.class, state::startProcessing);
    }

}
