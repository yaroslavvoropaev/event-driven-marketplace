package ru.voropaev.event_driven_marketplace.notification.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import ru.voropaev.event_driven_marketplace.notification.domain.NotificationType;
import ru.voropaev.event_driven_marketplace.notification.message.NotificationMessage;
import ru.voropaev.event_driven_marketplace.notification.message.NotificationMessages;
import ru.voropaev.event_driven_marketplace.notification.sender.NotificationSender;
import ru.voropaev.event_driven_marketplace.notification.sender.exception.NotificationDeliveryException;
import ru.voropaev.event_driven_marketplace.notification.service.NotificationService;
import ru.voropaev.event_driven_marketplace.order.event.OrderCancelled;
import ru.voropaev.event_driven_marketplace.order.event.OrderConfirmed;
import ru.voropaev.event_driven_marketplace.user.service.CustomerDirectory;

import java.util.Optional;
import java.util.UUID;

@Component
public class OrderNotificationListener {
    private static final Logger log = LoggerFactory.getLogger(OrderNotificationListener.class);

    private final CustomerDirectory customerDirectory;
    private final NotificationService notificationService;
    private final NotificationSender notificationSender;

    public OrderNotificationListener(CustomerDirectory customerDirectory,
                                     NotificationService notificationService,
                                     NotificationSender notificationSender) {
        this.customerDirectory = customerDirectory;
        this.notificationService = notificationService;
        this.notificationSender = notificationSender;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(OrderConfirmed event) {
        deliver(event.orderId(), event.customerId(), NotificationType.ORDER_CONFIRMED,
                NotificationMessages.confirmed(event));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(OrderCancelled event) {
        deliver(event.orderId(), event.customerId(), NotificationType.ORDER_CANCELLED,
                NotificationMessages.cancelled(event));
    }

    private void deliver(UUID orderId, UUID customerId, NotificationType type, NotificationMessage message) {
        try {
            Optional<String> email = customerDirectory.emailOf(customerId);
            if (email.isEmpty()) {
                log.warn("No email for customer {}, {} notification for order {} skipped",
                        customerId, type, orderId);
                return;
            }

            Optional<UUID> notificationId;
            try {
                notificationId = notificationService.startDelivery(orderId, customerId, type,
                        email.get(), message.subject(), message.body());
            } catch (DataIntegrityViolationException exception) {
                log.debug("{} notification for order {} is being delivered by a concurrent handler",
                        type, orderId);
                return;
            }
            if (notificationId.isEmpty()) {
                log.debug("{} notification for order {} was already delivered", type, orderId);
                return;
            }

            try {
                notificationSender.send(email.get(), message.subject(), message.body());
            } catch (NotificationDeliveryException exception) {
                notificationService.markFailed(notificationId.get(), exception.getMessage());
                log.error("Failed to deliver {} notification for order {}", type, orderId, exception);
                return;
            }

            notificationService.markSent(notificationId.get());
        } catch (Exception exception) {
            log.error("{} notification for order {} failed, notification may be left in PENDING",
                    type, orderId, exception);
        }
    }
}
