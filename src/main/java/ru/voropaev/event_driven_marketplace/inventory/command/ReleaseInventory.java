package ru.voropaev.event_driven_marketplace.inventory.command;

import ru.voropaev.event_driven_marketplace.common.messaging.Message;

import java.time.Instant;
import java.util.UUID;

public record ReleaseInventory(UUID orderId, Instant occurredAt) implements Message {}

