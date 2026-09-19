package ru.voropaev.event_driven_marketplace.order.event;

public enum CancellationReason {
    CUSTOMER_REQUEST,
    RESERVATION_FAILED,
    PAYMENT_FAILED
}
