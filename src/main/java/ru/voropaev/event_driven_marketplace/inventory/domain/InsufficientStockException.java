package ru.voropaev.event_driven_marketplace.inventory.domain;

public class InsufficientStockException extends RuntimeException {
    public InsufficientStockException(int quantity, int availableQuantity) {
        super("Cannot reserve " + quantity + ", only " + availableQuantity + " are available");
    }
}
