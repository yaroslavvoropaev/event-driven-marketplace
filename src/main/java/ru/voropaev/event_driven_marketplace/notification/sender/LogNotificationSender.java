package ru.voropaev.event_driven_marketplace.notification.sender;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class LogNotificationSender implements NotificationSender {
    private static final Logger log = LoggerFactory.getLogger(LogNotificationSender.class);

    @Override
    public void send(String recipient, String subject, String body) {
        log.info("Notification sent to {}, subject={}",
                recipient, subject);
    }
}
