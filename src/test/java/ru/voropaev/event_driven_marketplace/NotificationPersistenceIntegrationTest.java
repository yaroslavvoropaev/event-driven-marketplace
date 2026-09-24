package ru.voropaev.event_driven_marketplace;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import ru.voropaev.event_driven_marketplace.notification.domain.Notification;
import ru.voropaev.event_driven_marketplace.notification.domain.NotificationStatus;
import ru.voropaev.event_driven_marketplace.notification.domain.NotificationType;
import ru.voropaev.event_driven_marketplace.notification.repository.NotificationRepository;
import ru.voropaev.event_driven_marketplace.notification.service.NotificationService;
import ru.voropaev.event_driven_marketplace.user.api.dto.RegisterRequest;
import ru.voropaev.event_driven_marketplace.user.api.dto.UserResponse;
import ru.voropaev.event_driven_marketplace.user.service.AuthService;
import ru.voropaev.event_driven_marketplace.user.service.CustomerDirectory;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Проверяет домен notification на настоящем Postgres: маппинг сущности на схему V8,
 * уникальный индекс как барьер против дубля и резолв получателя через user-домен.
 * <p>
 * Само поднятие контекста здесь — тоже проверка: {@code ddl-auto=validate} упадёт,
 * если V8 не применилась или имена/типы колонок разъехались с полями сущности.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class NotificationPersistenceIntegrationTest {

    private static final String RECIPIENT = "buyer@example.com";
    private static final String SUBJECT = "Заказ подтверждён";
    private static final String BODY = "Мы получили оплату по вашему заказу.";

    @Autowired
    private NotificationRepository notificationRepository;
    @Autowired
    private NotificationService notificationService;
    @Autowired
    private CustomerDirectory customerDirectory;
    @Autowired
    private AuthService authService;

    private Notification pending(UUID orderId, NotificationType type) {
        return Notification.pending(orderId, UUID.randomUUID(), type, RECIPIENT, SUBJECT, BODY);
    }

    @Test
    void pendingNotificationIsStoredWithEmptyDeliveryFields() {
        UUID orderId = UUID.randomUUID();

        UUID id = notificationRepository.save(pending(orderId, NotificationType.ORDER_CONFIRMED)).getId();

        Notification stored = notificationRepository.findById(id).orElseThrow();
        assertEquals(NotificationStatus.PENDING, stored.getStatus());
        assertEquals(NotificationType.ORDER_CONFIRMED, stored.getType());
        assertEquals(RECIPIENT, stored.getRecipient());
        assertEquals(BODY, stored.getBody());
        assertNotNull(stored.getCreatedAt());
        assertNull(stored.getSentAt());
        assertNull(stored.getFailureReason());
    }

    /**
     * Настоящий барьер дедупликации: {@code existsBy} в сервисе — только быстрый путь,
     * гонку двух параллельных вызовов отсекает именно индекс в БД.
     */
    @Test
    void secondNotificationOfSameTypeForSameOrderIsRejectedByDatabase() {
        UUID orderId = UUID.randomUUID();
        notificationRepository.saveAndFlush(pending(orderId, NotificationType.ORDER_CONFIRMED));

        assertThrows(DataIntegrityViolationException.class, () ->
                notificationRepository.saveAndFlush(pending(orderId, NotificationType.ORDER_CONFIRMED)));
    }

    @Test
    void cancelledNotificationIsAllowedAlongsideConfirmedForSameOrder() {
        UUID orderId = UUID.randomUUID();

        notificationRepository.saveAndFlush(pending(orderId, NotificationType.ORDER_CONFIRMED));
        notificationRepository.saveAndFlush(pending(orderId, NotificationType.ORDER_CANCELLED));

        assertTrue(notificationRepository.existsByOrderIdAndType(orderId, NotificationType.ORDER_CONFIRMED));
        assertTrue(notificationRepository.existsByOrderIdAndType(orderId, NotificationType.ORDER_CANCELLED));
    }

    @Test
    void startDelivery_refusesSecondAttemptForSameOrderAndType() {
        UUID orderId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();

        Optional<UUID> first = notificationService.startDelivery(orderId, customerId,
                NotificationType.ORDER_CONFIRMED, RECIPIENT, SUBJECT, BODY);
        Optional<UUID> second = notificationService.startDelivery(orderId, customerId,
                NotificationType.ORDER_CONFIRMED, RECIPIENT, SUBJECT, BODY);

        assertTrue(first.isPresent());
        assertTrue(second.isEmpty());
    }

    @Test
    void markSentAndMarkFailedArePersisted() {
        UUID sentId = notificationService.startDelivery(UUID.randomUUID(), UUID.randomUUID(),
                NotificationType.ORDER_CONFIRMED, RECIPIENT, SUBJECT, BODY).orElseThrow();
        UUID failedId = notificationService.startDelivery(UUID.randomUUID(), UUID.randomUUID(),
                NotificationType.ORDER_CANCELLED, RECIPIENT, SUBJECT, BODY).orElseThrow();

        notificationService.markSent(sentId);
        notificationService.markFailed(failedId, "smtp timeout");

        Notification sent = notificationRepository.findById(sentId).orElseThrow();
        assertEquals(NotificationStatus.SENT, sent.getStatus());
        assertNotNull(sent.getSentAt());

        Notification failed = notificationRepository.findById(failedId).orElseThrow();
        assertEquals(NotificationStatus.FAILED, failed.getStatus());
        assertEquals("smtp timeout", failed.getFailureReason());
        assertNull(failed.getSentAt());
    }

    @Test
    void customerDirectoryResolvesEmailOfRegisteredUser() {
        String email = "notified-" + UUID.randomUUID() + "@example.com";
        UserResponse registered = authService.register(new RegisterRequest(email, "password123"));

        assertEquals(Optional.of(email), customerDirectory.emailOf(registered.id()));
    }

    /**
     * Некому писать — не ошибка: листенер просто пропустит отправку.
     */
    @Test
    void customerDirectoryReturnsEmptyForUnknownCustomer() {
        assertTrue(customerDirectory.emailOf(UUID.randomUUID()).isEmpty());
    }
}
