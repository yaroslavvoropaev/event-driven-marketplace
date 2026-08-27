package ru.voropaev.event_driven_marketplace.order;

import org.junit.jupiter.api.Test;
import ru.voropaev.event_driven_marketplace.order.domain.InvalidOrderTransitionException;
import ru.voropaev.event_driven_marketplace.order.domain.OrderStatus;
import ru.voropaev.event_driven_marketplace.order.domain.state.CreatedState;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class CreatedStateTest {
    private final CreatedState state = new CreatedState();

    @Test
    void codeIsCreated() {
        assertEquals(OrderStatus.CREATED, state.code());
    }

    @Test
    void startProcessingMovesToPending() {
        assertEquals(OrderStatus.PENDING, state.startProcessing());
    }

    @Test
    void cancelMovesToCancelled() {
        assertEquals(OrderStatus.CANCELLED, state.cancel());
    }

    @Test
    void confirmIsNotAllowed() {
        assertThrows(InvalidOrderTransitionException.class, state::confirm);
    }

}
