package ru.voropaev.event_driven_marketplace.notification.service;


import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import ru.voropaev.event_driven_marketplace.notification.domain.Notification;
import ru.voropaev.event_driven_marketplace.notification.domain.NotificationType;
import ru.voropaev.event_driven_marketplace.notification.repository.NotificationRepository;

import java.util.Optional;
import java.util.UUID;

@Service
public class NotificationServiceImpl implements NotificationService {
    private final NotificationRepository notificationRepository;

    public NotificationServiceImpl(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<UUID> startDelivery(UUID orderId, UUID customerId, NotificationType type, String recipient, String subject, String body) {
        if (notificationRepository.existsByOrderIdAndType(orderId, type)) {
            return Optional.empty();
        }

        Notification notification = Notification.pending(orderId, customerId, type, recipient, subject, body);
        notificationRepository.save(notification);
        return Optional.of(notification.getId());
    }


    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markSent(UUID notificationId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new NotificationNotFoundException(notificationId));
        notification.markSent();
        notificationRepository.save(notification);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(UUID notificationId, String reason) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new NotificationNotFoundException(notificationId));
        notification.markFailed(reason);
        notificationRepository.save(notification);
    }
}
