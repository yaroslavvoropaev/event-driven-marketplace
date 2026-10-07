package ru.voropaev.event_driven_marketplace;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import ru.voropaev.event_driven_marketplace.inventory.domain.Stock;
import ru.voropaev.event_driven_marketplace.inventory.repository.StockRepository;
import ru.voropaev.event_driven_marketplace.notification.domain.Notification;
import ru.voropaev.event_driven_marketplace.notification.domain.NotificationStatus;
import ru.voropaev.event_driven_marketplace.notification.domain.NotificationType;
import ru.voropaev.event_driven_marketplace.notification.repository.NotificationRepository;
import ru.voropaev.event_driven_marketplace.order.api.dto.CreateOrderRequest;
import ru.voropaev.event_driven_marketplace.order.api.dto.OrderItemRequest;
import ru.voropaev.event_driven_marketplace.order.api.dto.OrderResponse;
import ru.voropaev.event_driven_marketplace.order.domain.state.OrderStatus;
import ru.voropaev.event_driven_marketplace.order.service.OrderService;
import ru.voropaev.event_driven_marketplace.user.api.dto.RegisterRequest;
import ru.voropaev.event_driven_marketplace.user.service.AuthService;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class OrderNotificationIntegrationTest {
    @Autowired
    private OrderService orderService;
    @Autowired
    private AuthService authService;
    @Autowired
    private StockRepository stockRepository;
    @Autowired
    private NotificationRepository notificationRepository;

    private UUID registeredCustomer() {
        String email = "customer-" + UUID.randomUUID() + "@example.com";
        return authService.register(new RegisterRequest(email, "password123")).id();
    }

    private UUID productPricedAt(String price, int available) {
        UUID productId = UUID.randomUUID();
        stockRepository.save(new Stock(productId, new BigDecimal(price), available, 0));
        return productId;
    }

    private OrderResponse order(UUID customerId, UUID productId, int quantity) {
        return orderService.createOrder(customerId, new CreateOrderRequest(
                List.of(new OrderItemRequest(productId, quantity))
        ));
    }

    private List<Notification> notificationsOf(UUID orderId) {
        return notificationRepository.findAll().stream()
                .filter(n -> n.getOrderId().equals(orderId))
                .toList();
    }

    @Test
    void sendsConfirmation_whenOrderIsConfirmed() {
        UUID customerId = registeredCustomer();
        OrderResponse response = order(customerId, productPricedAt("10.00", 10), 3);

        List<Notification> notifications = notificationsOf(response.id());

        assertEquals(1, notifications.size());
        Notification notification = notifications.getFirst();
        assertEquals(NotificationType.ORDER_CONFIRMED, notification.getType());
        assertEquals(NotificationStatus.SENT, notification.getStatus());
        assertEquals(customerId, notification.getCustomerId());
        assertNotNull(notification.getSentAt());
        assertTrue(notification.getRecipient().startsWith("customer-"));
        assertTrue(notification.getBody().contains(response.id().toString()));
        assertTrue(notification.getBody().contains("30.00"));
    }

    @Test
    void sendsReservationFailureText_whenNotEnoughStock() {
        UUID customerId = registeredCustomer();
        OrderResponse response = order(customerId, productPricedAt("10.00", 1), 3);

        List<Notification> notifications = notificationsOf(response.id());

        assertEquals(1, notifications.size());
        assertEquals(NotificationType.ORDER_CANCELLED, notifications.getFirst().getType());
        assertTrue(notifications.getFirst().getBody().contains("не удалось зарезервировать товар"));
    }

    @Test
    void sagaCompletesWithoutNotification_whenCustomerHasNoAccount() {
        UUID customerId = UUID.randomUUID();
        OrderResponse response = order(customerId, productPricedAt("10.00", 10), 1);

        assertEquals(OrderStatus.CONFIRMED, orderService.getOrder(response.id(), customerId).orderStatus());
        assertTrue(notificationsOf(response.id()).isEmpty());
    }
}
