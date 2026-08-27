package ru.voropaev.event_driven_marketplace.order.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Order {
    @Id
    UUID id;

    String customerId;

    @Enumerated(EnumType.STRING)
    OrderStatus orderStatus;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    List<OrderItem> items = new ArrayList<>();

    Instant createdAt;

    public Order(String customerId) {
        this.id = UUID.randomUUID();
        this.customerId = customerId;
        this.orderStatus = OrderStatus.CREATED;
        this.createdAt = Instant.now();
    }

    public BigDecimal getTotalAmount() {
        return items.stream()
                .map(OrderItem::subtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public void updateStatus(OrderStatus newStatus) {
        this.orderStatus = newStatus;
    }


    public void addItem(OrderItem item) {
        items.add(item);
        item.assignTo(this);
    }

    public List<OrderItem> getItems() {
        return Collections.unmodifiableList(items);
    }
}
