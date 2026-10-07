package ru.voropaev.event_driven_marketplace.inventory.service;

import ru.voropaev.event_driven_marketplace.inventory.command.ReserveInventory;

import java.math.BigDecimal;
import java.util.UUID;

public interface InventoryService {
    BigDecimal getPrice(UUID productId);
    void reserveForOrder(ReserveInventory event);
    void confirmReservations(UUID orderId);
    void releaseReservations(UUID orderId);
}
