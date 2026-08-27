package ru.voropaev.event_driven_marketplace.order.domain.state;

import org.springframework.stereotype.Component;
import ru.voropaev.event_driven_marketplace.order.domain.InvalidOrderTransitionException;
import ru.voropaev.event_driven_marketplace.order.domain.OrderState;
import ru.voropaev.event_driven_marketplace.order.domain.OrderStatus;

@Component
public class PendingState implements OrderState {

    @Override
    public OrderStatus code() {
        return OrderStatus.PENDING;
    }

    @Override
    public OrderStatus confirm() {
        return OrderStatus.CONFIRMED;
    }

    @Override
    public OrderStatus cancel() {
        return OrderStatus.CANCELLED;
    }

    @Override
    public OrderStatus startProcessing() {
        throw new InvalidOrderTransitionException(OrderStatus.PENDING, "startProcessing");
    }
}
