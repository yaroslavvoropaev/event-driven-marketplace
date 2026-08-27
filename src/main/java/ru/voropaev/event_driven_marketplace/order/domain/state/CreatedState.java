package ru.voropaev.event_driven_marketplace.order.domain.state;

import org.springframework.stereotype.Component;
import ru.voropaev.event_driven_marketplace.order.domain.InvalidOrderTransitionException;
import ru.voropaev.event_driven_marketplace.order.domain.OrderState;
import ru.voropaev.event_driven_marketplace.order.domain.OrderStatus;

@Component
public class CreatedState implements OrderState {
    @Override
    public OrderStatus startProcessing() {
        return OrderStatus.PENDING;
    }

    @Override
    public OrderStatus cancel() {
        return OrderStatus.CANCELLED;
    }

    @Override
    public OrderStatus code() {
        return OrderStatus.CREATED;
    }

    @Override
    public OrderStatus confirm() {
        throw new InvalidOrderTransitionException(OrderStatus.CREATED, "confirm");
    }
}
