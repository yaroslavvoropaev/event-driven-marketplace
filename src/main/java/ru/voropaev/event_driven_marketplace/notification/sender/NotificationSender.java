package ru.voropaev.event_driven_marketplace.notification.sender;

public interface NotificationSender {
    void send(String recipient, String subject, String body);
}
