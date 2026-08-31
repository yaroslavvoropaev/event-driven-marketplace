package ru.voropaev.event_driven_marketplace.inventory.event;

import java.time.Instant;
import java.util.UUID;

public record InventoryReservationFailed(UUID orderId, String reason, Instant occurredAt) { }
