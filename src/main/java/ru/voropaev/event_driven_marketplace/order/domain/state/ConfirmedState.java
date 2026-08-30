package ru.voropaev.event_driven_marketplace.order.domain.state;

import org.springframework.stereotype.Component;

@Component
public class ConfirmedState implements OrderState {
    @Override
    public OrderStatus code() {
        return OrderStatus.CONFIRMED;
    }

    @Override
    public OrderStatus confirm() {
        throw new InvalidOrderTransitionException(OrderStatus.CONFIRMED, "confirm");
    }

    @Override
    public OrderStatus cancel() {
        throw new InvalidOrderTransitionException(OrderStatus.CONFIRMED, "cancel");
    }

    @Override
    public OrderStatus startProcessing() {
        throw new InvalidOrderTransitionException(OrderStatus.CONFIRMED, "startProcessing");
    }
}
