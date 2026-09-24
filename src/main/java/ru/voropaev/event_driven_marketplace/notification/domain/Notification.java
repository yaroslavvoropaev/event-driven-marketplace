package ru.voropaev.event_driven_marketplace.notification.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Getter
@Table(name = "notifications", schema = "notification_service")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notification {
    @Id
    private UUID id;


    private UUID orderId;
    private UUID customerId;
    @Enumerated(EnumType.STRING)
    private NotificationType type;

    private String recipient;
    private String subject;
    private String body;
    @Enumerated(EnumType.STRING)
    private NotificationStatus status;

    private String failureReason;
    private Instant createdAt;
    private Instant sentAt;

    private Notification(UUID orderId, UUID customerId,
                        NotificationType type, String recipient,
                        String subject, String body) {
        this.id = UUID.randomUUID();
        this.orderId = orderId;
        this.customerId = customerId;
        this.type = type;
        this.recipient = recipient;
        this.subject = subject;
        this.body = body;
        this.status = NotificationStatus.PENDING;
        this.createdAt = Instant.now();
    }

    public static Notification pending(UUID orderId, UUID customerId,
                                       NotificationType type, String recipient,
                                       String subject, String body) {
        return new Notification(orderId, customerId, type, recipient, subject, body);
    }

    public void markSent() {
        status = NotificationStatus.SENT;
        sentAt = Instant.now();
    }

    public void markFailed(String reason) {
        status = NotificationStatus.FAILED;
        failureReason = reason;
    }


}
