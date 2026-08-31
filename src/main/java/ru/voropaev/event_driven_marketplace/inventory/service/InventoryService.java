package ru.voropaev.event_driven_marketplace.inventory.service;

import ru.voropaev.event_driven_marketplace.order.event.OrderCreated;

import java.math.BigDecimal;
import java.util.UUID;

public interface InventoryService {
    BigDecimal getPrice(UUID productId);
    void reserveForOrder(OrderCreated event);
}
