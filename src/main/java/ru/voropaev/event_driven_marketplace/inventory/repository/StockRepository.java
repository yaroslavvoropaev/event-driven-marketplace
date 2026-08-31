package ru.voropaev.event_driven_marketplace.inventory.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.voropaev.event_driven_marketplace.inventory.domain.Stock;

import java.util.Optional;
import java.util.UUID;

public interface StockRepository extends JpaRepository<Stock, UUID> {
    Optional<Stock> findByProductId(UUID productId);
}
