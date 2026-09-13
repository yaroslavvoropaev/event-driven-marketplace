package ru.voropaev.event_driven_marketplace.payment.gateway.exception;

public class PaymentGatewayUnavailableException extends RuntimeException {
    public PaymentGatewayUnavailableException(String message) {
        super(message);
    }
}
