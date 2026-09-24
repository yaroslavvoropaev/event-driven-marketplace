package ru.voropaev.event_driven_marketplace.notification.domain;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class NotificationTest {

    private static final UUID ORDER_ID = UUID.randomUUID();
    private static final UUID CUSTOMER_ID = UUID.randomUUID();
    private static final String RECIPIENT = "buyer@example.com";
    private static final String SUBJECT = "Заказ подтверждён";
    private static final String BODY = "Мы получили оплату по вашему заказу.";

    private Notification newNotification() {
        return Notification.pending(ORDER_ID, CUSTOMER_ID, NotificationType.ORDER_CONFIRMED,
                RECIPIENT, SUBJECT, BODY);
    }

    @Test
    void newNotificationIsPendingAndNotYetSent() {
        Notification notification = newNotification();

        assertNotNull(notification.getId());
        assertEquals(ORDER_ID, notification.getOrderId());
        assertEquals(CUSTOMER_ID, notification.getCustomerId());
        assertEquals(NotificationType.ORDER_CONFIRMED, notification.getType());
        assertEquals(RECIPIENT, notification.getRecipient());
        assertEquals(SUBJECT, notification.getSubject());
        assertEquals(BODY, notification.getBody());
        assertEquals(NotificationStatus.PENDING, notification.getStatus());
        assertNotNull(notification.getCreatedAt());
        assertNull(notification.getSentAt());
        assertNull(notification.getFailureReason());
    }

    @Test
    void markSentRecordsDeliveryTime() {
        Notification notification = newNotification();

        notification.markSent();

        assertEquals(NotificationStatus.SENT, notification.getStatus());
        assertNotNull(notification.getSentAt());
        assertFalse(notification.getSentAt().isBefore(notification.getCreatedAt()));
        assertNull(notification.getFailureReason());
    }

    /**
     * sentAt остаётся пустым: колонка означает «когда письмо ушло», а не
     * «когда мы последний раз дёрнулись».
     */
    @Test
    void markFailedStoresReasonAndLeavesSentAtEmpty() {
        Notification notification = newNotification();

        notification.markFailed("smtp timeout");

        assertEquals(NotificationStatus.FAILED, notification.getStatus());
        assertEquals("smtp timeout", notification.getFailureReason());
        assertNull(notification.getSentAt());
    }

    /**
     * Барьер против дубля — ключ (orderId, type), поэтому два уведомления по одному
     * заказу обязаны отличаться типом, а не идентификатором.
     */
    @Test
    void confirmedAndCancelledAreDistinctNotificationsForSameOrder() {
        Notification confirmed = newNotification();
        Notification cancelled = Notification.pending(ORDER_ID, CUSTOMER_ID,
                NotificationType.ORDER_CANCELLED, RECIPIENT, SUBJECT, BODY);

        assertEquals(confirmed.getOrderId(), cancelled.getOrderId());
        assertFalse(confirmed.getId().equals(cancelled.getId()));
        assertFalse(confirmed.getType() == cancelled.getType());
    }
}
