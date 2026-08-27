package ru.voropaev.event_driven_marketplace.order.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.voropaev.event_driven_marketplace.order.domain.Order;

import java.util.UUID;

public interface OrderRepository extends JpaRepository<Order, UUID> {

}
