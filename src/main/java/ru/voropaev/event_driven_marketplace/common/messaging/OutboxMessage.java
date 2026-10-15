package ru.voropaev.event_driven_marketplace.common.messaging;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "outbox", schema = "messaging")
public class OutboxMessage {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String messageType;
    private UUID orderId;
    private String payload;
    private Instant createdAt;
    private Instant sentAt;
    private int attempts;
    private String lastError;

    public OutboxMessage(String messageType, UUID orderId, String payload, Instant createdAt) {
        this.messageType = messageType;
        this.orderId = orderId;
        this.payload = payload;
        this.createdAt = createdAt;
    }

}
