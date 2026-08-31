package ru.voropaev.event_driven_marketplace.inventory.domain;


import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import ru.voropaev.event_driven_marketplace.inventory.domain.exception.InsufficientStockException;

import java.util.UUID;

@Entity
@Getter
@Table(name = "stock", schema = "inventory")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Stock {
    @Id
    private UUID id;
    private UUID productId;
    private int availableQuantity;
    private int reservedQuantity;
    @Version
    private Long version;

    public Stock(UUID productId, int availableQuantity, int reservedQuantity) {
        this.id = UUID.randomUUID();
        this.productId = productId;
        this.availableQuantity = availableQuantity;
        this.reservedQuantity = reservedQuantity;
    }

    public boolean canReserve(int quantity) {
        return quantity <= availableQuantity;
    }

    public void reserve(int quantity) {
        if (!canReserve(quantity)) {
            throw new InsufficientStockException(quantity, availableQuantity);
        }
        availableQuantity -= quantity;
        reservedQuantity += quantity;
    }

    public void release(int quantity) {
        reservedQuantity -= quantity;
        availableQuantity += quantity;
    }

    public void confirm(int quantity) {
        reservedQuantity -= quantity;
    }

}
