package ru.voropaev.event_driven_marketplace.inventory.command;

import java.time.Instant;
import java.util.UUID;

public record ReleaseInventory(UUID orderId, Instant occurredAt) {}

