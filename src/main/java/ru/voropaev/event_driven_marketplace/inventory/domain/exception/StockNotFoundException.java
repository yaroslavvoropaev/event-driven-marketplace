package ru.voropaev.event_driven_marketplace.inventory.domain.exception;

import java.util.UUID;

public class StockNotFoundException extends ReservationFailedException {
    public StockNotFoundException(UUID productId) {
        super("No stock found for product " + productId);
    }
}
