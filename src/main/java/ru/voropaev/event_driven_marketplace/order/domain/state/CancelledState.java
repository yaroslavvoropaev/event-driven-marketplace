package ru.voropaev.event_driven_marketplace.order.domain.state;

import org.springframework.stereotype.Component;

@Component
public class CancelledState implements OrderState {
    @Override
    public OrderStatus code() {
        return OrderStatus.CANCELLED;
    }

    @Override
    public OrderStatus confirm() {
        throw new InvalidOrderTransitionException(OrderStatus.CANCELLED, "confirm");
    }

    @Override
    public OrderStatus cancel() {
        throw new InvalidOrderTransitionException(OrderStatus.CANCELLED, "cancel");
    }

    @Override
    public OrderStatus startProcessing() {
        throw new InvalidOrderTransitionException(OrderStatus.CANCELLED, "startProcessing");
    }
}
