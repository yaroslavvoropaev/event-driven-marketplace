package ru.voropaev.event_driven_marketplace.order.service;

import java.util.UUID;

public class UnknownProductException extends RuntimeException {
    public UnknownProductException(UUID productId) {
        super("Unknown product: " + productId);
    }
}
