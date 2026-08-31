package ru.voropaev.event_driven_marketplace.inventory.domain;

public class IllegalReservationTransitionException extends RuntimeException {
    public IllegalReservationTransitionException(ReservationStatus from, String attemptedAction) {
        super("Cannot " + attemptedAction + " reservation in status " + from);
    }
}
