package ru.voropaev.event_driven_marketplace.notification.sender;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import ru.voropaev.event_driven_marketplace.notification.sender.exception.NotificationDeliveryException;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(OutputCaptureExtension.class)
class LogNotificationSenderTest {

    private static final String RECIPIENT = "buyer@example.com";
    private static final String SUBJECT = "Заказ подтверждён";
    private static final String BODY = "Ваш заказ 7f3a оплачен и подтверждён. Сумма: 300.00 ₽.";

    private final LogNotificationSender sender = new LogNotificationSender();

    @Test
    void send_alwaysSucceeds() {
        assertDoesNotThrow(() -> sender.send(RECIPIENT, SUBJECT, BODY));
    }

    @Test
    void send_logsRecipientAndSubject(CapturedOutput output) {
        sender.send(RECIPIENT, SUBJECT, BODY);

        assertTrue(output.getOut().contains(RECIPIENT));
        assertTrue(output.getOut().contains(SUBJECT));
    }

    /**
     * В теле — номер заказа и сумма, это переписка с клиентом, а не диагностика.
     * Тело хранится в таблице notifications, в логе ему делать нечего.
     */
    @Test
    void send_doesNotLogBody(CapturedOutput output) {
        sender.send(RECIPIENT, SUBJECT, BODY);

        assertFalse(output.getOut().contains(BODY));
    }

    /**
     * Реальный сендер будет оборачивать сбой SMTP-клиента — исходная причина
     * не должна теряться.
     */
    @Test
    void deliveryException_keepsOriginalCause() {
        IOException cause = new IOException("connection reset");

        NotificationDeliveryException exception = new NotificationDeliveryException("smtp failure", cause);

        assertEquals("smtp failure", exception.getMessage());
        assertSame(cause, exception.getCause());
    }
}
