package ru.voropaev.event_driven_marketplace.inventory.command;

import ru.voropaev.event_driven_marketplace.common.messaging.Message;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ReserveInventory(UUID orderId, List<Item> items, Instant occurredAt)  implements Message {
    public record Item(UUID productId, int quantity) {}
}
