package ru.voropaev.event_driven_marketplace.order.api.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record OrderItemRequest(
        UUID productId,
        int quantity,
        BigDecimal unitPrice
) {
}
