package ru.voropaev.event_driven_marketplace.inventory.domain.exception;

public class ReservationFailedException extends RuntimeException {
    public ReservationFailedException(String message) {
        super(message);
    }
}
