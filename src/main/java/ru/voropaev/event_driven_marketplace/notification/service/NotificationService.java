package ru.voropaev.event_driven_marketplace.notification.service;

import ru.voropaev.event_driven_marketplace.notification.domain.Notification;
import ru.voropaev.event_driven_marketplace.notification.domain.NotificationType;

import java.util.Optional;
import java.util.UUID;

public interface NotificationService {
    Optional<UUID> startDelivery(UUID orderId, UUID customerId,
                                         NotificationType type, String recipient,
                                         String subject, String body);
    void markSent(UUID notificationId);
    void markFailed(UUID notificationId, String reason);
}
