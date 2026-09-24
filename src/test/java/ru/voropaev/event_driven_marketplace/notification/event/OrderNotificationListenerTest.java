package ru.voropaev.event_driven_marketplace.notification.event;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import ru.voropaev.event_driven_marketplace.notification.domain.NotificationType;
import ru.voropaev.event_driven_marketplace.notification.message.NotificationMessage;
import ru.voropaev.event_driven_marketplace.notification.message.NotificationMessages;
import ru.voropaev.event_driven_marketplace.notification.sender.NotificationSender;
import ru.voropaev.event_driven_marketplace.notification.sender.exception.NotificationDeliveryException;
import ru.voropaev.event_driven_marketplace.notification.service.NotificationService;
import ru.voropaev.event_driven_marketplace.order.event.CancellationReason;
import ru.voropaev.event_driven_marketplace.order.event.OrderCancelled;
import ru.voropaev.event_driven_marketplace.order.event.OrderConfirmed;
import ru.voropaev.event_driven_marketplace.user.service.CustomerDirectory;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderNotificationListenerTest {

    private static final UUID ORDER_ID = UUID.randomUUID();
    private static final UUID CUSTOMER_ID = UUID.randomUUID();
    private static final UUID NOTIFICATION_ID = UUID.randomUUID();
    private static final String EMAIL = "buyer@example.com";
    private static final BigDecimal AMOUNT = new BigDecimal("300.00");
    private static final Instant OCCURRED_AT = Instant.parse("2026-09-24T10:00:00Z");

    @Mock
    private CustomerDirectory customerDirectory;
    @Mock
    private NotificationService notificationService;
    @Mock
    private NotificationSender notificationSender;

    private OrderNotificationListener listener;

    @BeforeEach
    void setUp() {
        listener = new OrderNotificationListener(customerDirectory, notificationService, notificationSender);
    }

    private OrderConfirmed confirmedEvent() {
        return new OrderConfirmed(ORDER_ID, CUSTOMER_ID, AMOUNT, OCCURRED_AT);
    }

    private OrderCancelled cancelledEvent(CancellationReason reason) {
        return new OrderCancelled(ORDER_ID, CUSTOMER_ID, AMOUNT, reason, OCCURRED_AT);
    }

    private void customerHasEmail() {
        when(customerDirectory.emailOf(CUSTOMER_ID)).thenReturn(Optional.of(EMAIL));
    }

    private void deliveryStarts(NotificationType type) {
        when(notificationService.startDelivery(ORDER_ID, CUSTOMER_ID, type, EMAIL,
                subjectFor(type), bodyFor(type))).thenReturn(Optional.of(NOTIFICATION_ID));
    }

    private String subjectFor(NotificationType type) {
        return messageFor(type).subject();
    }

    private String bodyFor(NotificationType type) {
        return messageFor(type).body();
    }

    private NotificationMessage messageFor(NotificationType type) {
        return type == NotificationType.ORDER_CONFIRMED
                ? NotificationMessages.confirmed(confirmedEvent())
                : NotificationMessages.cancelled(cancelledEvent(CancellationReason.PAYMENT_FAILED));
    }

    // --- успешный путь ---

    @Test
    void confirmed_registersSendsAndMarksSent() {
        customerHasEmail();
        deliveryStarts(NotificationType.ORDER_CONFIRMED);
        NotificationMessage expected = NotificationMessages.confirmed(confirmedEvent());

        listener.on(confirmedEvent());

        verify(notificationService).startDelivery(ORDER_ID, CUSTOMER_ID, NotificationType.ORDER_CONFIRMED,
                EMAIL, expected.subject(), expected.body());
        verify(notificationSender).send(EMAIL, expected.subject(), expected.body());
        verify(notificationService).markSent(NOTIFICATION_ID);
        verify(notificationService, never()).markFailed(any(), any());
    }

    @Test
    void cancelled_usesCancelledTypeAndTextOfItsReason() {
        customerHasEmail();
        deliveryStarts(NotificationType.ORDER_CANCELLED);
        NotificationMessage expected =
                NotificationMessages.cancelled(cancelledEvent(CancellationReason.PAYMENT_FAILED));

        listener.on(cancelledEvent(CancellationReason.PAYMENT_FAILED));

        verify(notificationService).startDelivery(ORDER_ID, CUSTOMER_ID, NotificationType.ORDER_CANCELLED,
                EMAIL, expected.subject(), expected.body());
        verify(notificationSender).send(EMAIL, expected.subject(), expected.body());
        verify(notificationService).markSent(NOTIFICATION_ID);
    }

    /**
     * N-3: при обратном порядке сбой между отправкой и записью дал бы второе письмо.
     */
    @Test
    void registersDeliveryBeforeSending() {
        customerHasEmail();
        deliveryStarts(NotificationType.ORDER_CONFIRMED);

        listener.on(confirmedEvent());

        InOrder order = inOrder(notificationService, notificationSender);
        order.verify(notificationService).startDelivery(any(), any(), any(), any(), any(), any());
        order.verify(notificationSender).send(anyString(), anyString(), anyString());
        order.verify(notificationService).markSent(NOTIFICATION_ID);
    }

    // --- пропуски ---

    @Test
    void skipsWithoutRecord_whenCustomerHasNoEmail() {
        when(customerDirectory.emailOf(CUSTOMER_ID)).thenReturn(Optional.empty());

        listener.on(confirmedEvent());

        verifyNoInteractions(notificationService, notificationSender);
    }

    @Test
    void doesNotSend_whenAlreadyNotified() {
        customerHasEmail();
        when(notificationService.startDelivery(any(), any(), any(), any(), any(), any()))
                .thenReturn(Optional.empty());

        listener.on(confirmedEvent());

        verifyNoInteractions(notificationSender);
        verify(notificationService, never()).markSent(any());
        verify(notificationService, never()).markFailed(any(), any());
    }

    /**
     * Проиграли гонку: строку вставил параллельный обработчик. Трогать её нельзя —
     * она чужая, и письмо по ней, возможно, как раз уходит.
     */
    @Test
    void yieldsSilently_whenConcurrentHandlerWonTheRace() {
        customerHasEmail();
        when(notificationService.startDelivery(any(), any(), any(), any(), any(), any()))
                .thenThrow(new DataIntegrityViolationException("uk_notification_order_type"));

        assertDoesNotThrow(() -> listener.on(confirmedEvent()));

        verifyNoInteractions(notificationSender);
        verify(notificationService, never()).markSent(any());
        verify(notificationService, never()).markFailed(any(), any());
    }

    // --- отказы ---

    @Test
    void marksFailedWithReason_whenSenderRefuses() {
        customerHasEmail();
        deliveryStarts(NotificationType.ORDER_CONFIRMED);
        doThrow(new NotificationDeliveryException("smtp timeout"))
                .when(notificationSender).send(anyString(), anyString(), anyString());

        assertDoesNotThrow(() -> listener.on(confirmedEvent()));

        verify(notificationService).markFailed(NOTIFICATION_ID, "smtp timeout");
        verify(notificationService, never()).markSent(any());
    }

    /**
     * N-9: письмо реально ушло, поэтому сбой записи SENT не должен превращаться в FAILED.
     */
    @Test
    void doesNotMarkFailed_whenSentButMarkSentFails() {
        customerHasEmail();
        deliveryStarts(NotificationType.ORDER_CONFIRMED);
        doThrow(new RuntimeException("db down")).when(notificationService).markSent(NOTIFICATION_ID);

        assertDoesNotThrow(() -> listener.on(confirmedEvent()));

        verify(notificationSender).send(anyString(), anyString(), anyString());
        verify(notificationService, never()).markFailed(any(), any());
    }

    /**
     * N-10: даже неудачная запись FAILED не выходит наружу. Здесь же ловушка из-за
     * отсутствия обрезки reason: длинная причина роняет markFailed ровно этим типом,
     * и широкий catch DataIntegrityViolationException выдал бы её за безобидный дубль.
     */
    @Test
    void staysIsolated_whenMarkFailedItselfFails() {
        customerHasEmail();
        deliveryStarts(NotificationType.ORDER_CONFIRMED);
        doThrow(new NotificationDeliveryException("smtp timeout"))
                .when(notificationSender).send(anyString(), anyString(), anyString());
        doThrow(new DataIntegrityViolationException("value too long"))
                .when(notificationService).markFailed(any(), any());

        assertDoesNotThrow(() -> listener.on(confirmedEvent()));

        verify(notificationService, never()).markSent(any());
    }

    @Test
    void staysIsolated_whenRecipientLookupFails() {
        when(customerDirectory.emailOf(CUSTOMER_ID)).thenThrow(new RuntimeException("auth schema unavailable"));

        assertDoesNotThrow(() -> listener.on(confirmedEvent()));

        verifyNoInteractions(notificationService, notificationSender);
    }

    @Test
    void staysIsolated_whenSenderThrowsUnexpectedException() {
        customerHasEmail();
        deliveryStarts(NotificationType.ORDER_CONFIRMED);
        doThrow(new IllegalStateException("bug in sender"))
                .when(notificationSender).send(anyString(), anyString(), anyString());

        assertDoesNotThrow(() -> listener.on(confirmedEvent()));

        // не NotificationDeliveryException — значит, не «канал отказал», а неизвестный сбой:
        // строка остаётся PENDING, её разберёт будущий sweeper
        verify(notificationService, never()).markFailed(any(), any());
        verify(notificationService, never()).markSent(any());
    }
}
