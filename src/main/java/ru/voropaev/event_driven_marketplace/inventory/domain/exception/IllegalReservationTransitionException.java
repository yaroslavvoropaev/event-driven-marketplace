package ru.voropaev.event_driven_marketplace.inventory.domain.exception;

import ru.voropaev.event_driven_marketplace.inventory.domain.ReservationStatus;

public class IllegalReservationTransitionException extends RuntimeException {
    public IllegalReservationTransitionException(ReservationStatus from, String attemptedAction) {
        super("Cannot " + attemptedAction + " reservation in status " + from);
    }
}
