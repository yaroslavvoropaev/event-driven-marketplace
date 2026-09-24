package ru.voropaev.event_driven_marketplace.notification.message;

import ru.voropaev.event_driven_marketplace.order.event.OrderCancelled;
import ru.voropaev.event_driven_marketplace.order.event.OrderConfirmed;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class NotificationMessages {

    private static final String CONFIRMED_SUBJECT = "Заказ подтверждён";
    private static final String CANCELLED_SUBJECT = "Заказ отменён";

    private NotificationMessages() {}

    public static NotificationMessage confirmed(OrderConfirmed event) {
        String body = "Ваш заказ %s оплачен и подтверждён. Сумма: %s ₽."
                .formatted(event.orderId(), formatAmount(event.totalAmount()));
        return new NotificationMessage(CONFIRMED_SUBJECT, body);
    }

    public static NotificationMessage cancelled(OrderCancelled event) {
        String template = switch (event.reason()) {
            case RESERVATION_FAILED -> "Заказ %s отменён: не удалось зарезервировать товар. Оплата не списывалась.";
            case PAYMENT_FAILED -> "Заказ %s отменён: оплата не прошла. Деньги не списаны.";
            case CUSTOMER_REQUEST -> "Заказ %s отменён по вашему запросу.";
        };
        return new NotificationMessage(CANCELLED_SUBJECT, template.formatted(event.orderId()));
    }

    private static String formatAmount(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }
}
