package ru.voropaev.event_driven_marketplace.order.domain.state;

public interface OrderState {
    OrderStatus code();
    OrderStatus confirm();
    OrderStatus cancel();
    OrderStatus startProcessing();
}
