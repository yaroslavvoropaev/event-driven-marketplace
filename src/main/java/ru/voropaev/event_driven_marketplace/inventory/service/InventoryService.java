package ru.voropaev.event_driven_marketplace.inventory.service;

import ru.voropaev.event_driven_marketplace.order.event.OrderCreated;

public interface InventoryService {
    void reserveForOrder(OrderCreated event);
}
