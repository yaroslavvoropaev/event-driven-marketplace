package ru.voropaev.event_driven_marketplace.notification.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.voropaev.event_driven_marketplace.notification.domain.Notification;
import ru.voropaev.event_driven_marketplace.notification.domain.NotificationType;

import java.util.UUID;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {
    boolean existsByOrderIdAndType(UUID orderId, NotificationType type);
}
