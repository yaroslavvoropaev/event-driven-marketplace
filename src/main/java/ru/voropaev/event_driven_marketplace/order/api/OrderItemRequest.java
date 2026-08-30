package ru.voropaev.event_driven_marketplace.order.api;

import java.math.BigDecimal;
import java.util.UUID;

public record OrderItemRequest(
        UUID productId,
        int quantity,
        BigDecimal unitPrice
) {
}
