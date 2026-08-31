package ru.voropaev.event_driven_marketplace.inventory.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import ru.voropaev.event_driven_marketplace.inventory.domain.exception.IllegalReservationTransitionException;

import java.time.Instant;
import java.util.UUID;

@Entity
@Getter
@Table(name = "reservation", schema = "inventory")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Reservation {
    @Id
    private UUID id;
    private UUID orderId;
    private UUID productId;
    private int quantity;
    @Enumerated(EnumType.STRING)
    private ReservationStatus reservationStatus;
    private Instant createdAt;

    private Reservation(UUID orderId, UUID productId, int quantity) {
        this.id = UUID.randomUUID();
        this.orderId = orderId;
        this.productId = productId;
        this.quantity = quantity;
        this.reservationStatus = ReservationStatus.RESERVED;
        this.createdAt = Instant.now();
    }

    public static Reservation reserved(UUID orderId, UUID productId, int quantity) {
        return new Reservation(orderId, productId, quantity);
    }

    public void release() {
        if (reservationStatus != ReservationStatus.RESERVED) {
            throw new IllegalReservationTransitionException(reservationStatus, "release");
        }
        reservationStatus = ReservationStatus.RELEASED;
    }

    public void confirm() {
        if (reservationStatus != ReservationStatus.RESERVED) {
            throw new IllegalReservationTransitionException(reservationStatus, "confirm");
        }
        reservationStatus = ReservationStatus.CONFIRMED;
    }
}
