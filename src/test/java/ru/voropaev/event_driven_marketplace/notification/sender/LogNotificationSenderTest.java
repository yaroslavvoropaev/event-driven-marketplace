package ru.voropaev.event_driven_marketplace.notification.sender;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(OutputCaptureExtension.class)
class LogNotificationSenderTest {
    private static final String RECIPIENT = "buyer@example.com";
    private static final String SUBJECT = "Заказ подтверждён";
    private static final String BODY = "Ваш заказ 7f3a оплачен и подтверждён. Сумма: 300.00 ₽.";

    private final LogNotificationSender sender = new LogNotificationSender();

    @Test
    void send_logsRecipientAndSubjectButNotBody(CapturedOutput output) {
        sender.send(RECIPIENT, SUBJECT, BODY);

        assertTrue(output.getOut().contains(RECIPIENT));
        assertTrue(output.getOut().contains(SUBJECT));
        assertFalse(output.getOut().contains(BODY));
    }
}
