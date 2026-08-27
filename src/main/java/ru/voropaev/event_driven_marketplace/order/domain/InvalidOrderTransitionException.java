package ru.voropaev.event_driven_marketplace.order.domain;

public class InvalidOrderTransitionException extends RuntimeException {
    public InvalidOrderTransitionException(OrderStatus from, String attemptedAction) {
        super("Cannot " + attemptedAction + " order in status " + from);
    }
}
