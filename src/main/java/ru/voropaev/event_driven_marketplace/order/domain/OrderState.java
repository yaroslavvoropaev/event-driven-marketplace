package ru.voropaev.event_driven_marketplace.order.domain;

public interface OrderState {
    OrderStatus code();
    OrderStatus confirm();
    OrderStatus cancel();
    OrderStatus startProcessing();
}
