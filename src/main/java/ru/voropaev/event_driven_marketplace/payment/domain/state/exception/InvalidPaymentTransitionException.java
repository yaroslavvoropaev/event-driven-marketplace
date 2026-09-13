package ru.voropaev.event_driven_marketplace.payment.domain.state.exception;

import ru.voropaev.event_driven_marketplace.payment.domain.state.PaymentStatus;

public class InvalidPaymentTransitionException extends RuntimeException {
    public InvalidPaymentTransitionException(PaymentStatus from, String attemptedAction) {
        super("Cannot " + attemptedAction + " payment in status " + from);
    }
}
