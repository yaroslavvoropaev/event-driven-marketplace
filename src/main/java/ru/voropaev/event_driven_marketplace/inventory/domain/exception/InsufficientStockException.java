package ru.voropaev.event_driven_marketplace.inventory.domain.exception;

public class InsufficientStockException extends ReservationFailedException {
    public InsufficientStockException(int quantity, int availableQuantity) {
        super("Cannot reserve " + quantity + ", only " + availableQuantity + " are available");
    }
}
