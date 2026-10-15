package ru.voropaev.event_driven_marketplace.inventory.event;

import ru.voropaev.event_driven_marketplace.common.messaging.Message;

import java.time.Instant;
import java.util.UUID;

public record InventoryReservationFailed(UUID orderId, String reason, Instant occurredAt)
        implements Message { }
