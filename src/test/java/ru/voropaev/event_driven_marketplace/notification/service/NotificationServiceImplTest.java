package ru.voropaev.event_driven_marketplace.notification.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.voropaev.event_driven_marketplace.notification.domain.Notification;
import ru.voropaev.event_driven_marketplace.notification.domain.NotificationStatus;
import ru.voropaev.event_driven_marketplace.notification.domain.NotificationType;
import ru.voropaev.event_driven_marketplace.notification.repository.NotificationRepository;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceImplTest {
    private static final UUID ORDER_ID = UUID.randomUUID();
    private static final UUID CUSTOMER_ID = UUID.randomUUID();
    private static final NotificationType TYPE = NotificationType.ORDER_CONFIRMED;
    private static final String RECIPIENT = "buyer@example.com";
    private static final String SUBJECT = "Заказ подтверждён";
    private static final String BODY = "Мы получили оплату по вашему заказу.";

    @Mock
    private NotificationRepository notificationRepository;

    private NotificationServiceImpl notificationService;

    @BeforeEach
    void setUp() {
        notificationService = new NotificationServiceImpl(notificationRepository);
    }

    private Notification pendingNotification() {
        return Notification.pending(ORDER_ID, CUSTOMER_ID, TYPE, RECIPIENT, SUBJECT, BODY);
    }

    private Optional<UUID> startDelivery() {
        return notificationService.startDelivery(ORDER_ID, CUSTOMER_ID, TYPE, RECIPIENT, SUBJECT, BODY);
    }

    @Test
    void startDelivery_savesPendingNotification_whenNoneExistsForOrderAndType() {
        when(notificationRepository.existsByOrderIdAndType(ORDER_ID, TYPE)).thenReturn(false);

        Optional<UUID> returned = startDelivery();

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());

        Notification saved = captor.getValue();
        assertTrue(returned.isPresent());
        assertEquals(saved.getId(), returned.get());
        assertEquals(ORDER_ID, saved.getOrderId());
        assertEquals(CUSTOMER_ID, saved.getCustomerId());
        assertEquals(TYPE, saved.getType());
        assertEquals(RECIPIENT, saved.getRecipient());
        assertEquals(SUBJECT, saved.getSubject());
        assertEquals(BODY, saved.getBody());
        assertEquals(NotificationStatus.PENDING, saved.getStatus());
        assertNull(saved.getSentAt());
    }

    @Test
    void startDelivery_returnsEmptyAndSavesNothing_whenSameOrderAndTypeAlreadyNotified() {
        when(notificationRepository.existsByOrderIdAndType(ORDER_ID, TYPE)).thenReturn(true);

        Optional<UUID> returned = startDelivery();

        assertTrue(returned.isEmpty());
        verify(notificationRepository, never()).save(any());
    }

    @Test
    void markSent_movesNotificationToSent() {
        Notification notification = pendingNotification();
        when(notificationRepository.findById(notification.getId())).thenReturn(Optional.of(notification));

        notificationService.markSent(notification.getId());

        assertEquals(NotificationStatus.SENT, notification.getStatus());
        assertNull(notification.getFailureReason());
    }

    @Test
    void markFailed_storesReason() {
        Notification notification = pendingNotification();
        when(notificationRepository.findById(notification.getId())).thenReturn(Optional.of(notification));

        notificationService.markFailed(notification.getId(), "smtp timeout");

        assertEquals(NotificationStatus.FAILED, notification.getStatus());
        assertEquals("smtp timeout", notification.getFailureReason());
        assertNull(notification.getSentAt());
    }
}
