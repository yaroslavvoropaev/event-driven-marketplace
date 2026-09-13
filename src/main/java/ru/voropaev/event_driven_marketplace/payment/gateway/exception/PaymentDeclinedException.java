package ru.voropaev.event_driven_marketplace.payment.gateway.exception;

public class PaymentDeclinedException extends RuntimeException {
    public PaymentDeclinedException(String message) {
        super(message);
    }
}
