package ru.voropaev.event_driven_marketplace.inventory.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record InventoryReserved(
        UUID orderId,
        UUID customerId,
        BigDecimal totalAmount,
        Instant occurredAt
) { }
