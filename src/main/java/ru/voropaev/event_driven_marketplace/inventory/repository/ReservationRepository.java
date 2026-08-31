package ru.voropaev.event_driven_marketplace.inventory.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.voropaev.event_driven_marketplace.inventory.domain.Reservation;

import java.util.UUID;

public interface ReservationRepository extends JpaRepository<Reservation, UUID> { }
