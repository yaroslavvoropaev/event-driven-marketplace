package ru.voropaev.event_driven_marketplace;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import ru.voropaev.event_driven_marketplace.inventory.domain.Reservation;
import ru.voropaev.event_driven_marketplace.inventory.domain.ReservationStatus;
import ru.voropaev.event_driven_marketplace.inventory.domain.Stock;
import ru.voropaev.event_driven_marketplace.inventory.repository.ReservationRepository;
import ru.voropaev.event_driven_marketplace.inventory.repository.StockRepository;
import ru.voropaev.event_driven_marketplace.notification.domain.Notification;
import ru.voropaev.event_driven_marketplace.notification.domain.NotificationStatus;
import ru.voropaev.event_driven_marketplace.notification.repository.NotificationRepository;
import ru.voropaev.event_driven_marketplace.notification.sender.NotificationSender;
import ru.voropaev.event_driven_marketplace.notification.sender.exception.NotificationDeliveryException;
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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;

/**
 * Изоляция уведомлений от саги (N-5, N-6) на настоящей БД.
 * <p>
 * Сендер подменён моком: правило «без моков в интеграционных тестах» касается БД и
 * брокера, а сендер — внешний канал, который и в проде стоит за интерфейсом.
 * Отдельный класс, потому что подмена бина порождает отдельный Spring-контекст.
 * <p>
 * На OrderConfirmed подписаны и notification, и inventory, порядок вызова не определён.
 * Проверка подтверждённого резерва ловит обрыв рассылки при любом порядке.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class NotificationSenderFailureIntegrationTest {

    @MockitoBean
    private NotificationSender notificationSender;

    @Autowired
    private OrderService orderService;
    @Autowired
    private AuthService authService;
    @Autowired
    private StockRepository stockRepository;
    @Autowired
    private ReservationRepository reservationRepository;
    @Autowired
    private NotificationRepository notificationRepository;

    @Test
    void orderAndStockAreConfirmed_andNotificationFailed_whenSenderIsDown() {
        doThrow(new NotificationDeliveryException("smtp down"))
                .when(notificationSender).send(anyString(), anyString(), anyString());

        UUID customerId = authService.register(new RegisterRequest(
                "customer-" + UUID.randomUUID() + "@example.com", "password123")).id();
        UUID productId = UUID.randomUUID();
        stockRepository.save(new Stock(productId, new BigDecimal("10.00"), 10, 0));

        OrderResponse response = assertDoesNotThrow(() -> orderService.createOrder(customerId,
                new CreateOrderRequest(List.of(new OrderItemRequest(productId, 2)))));

        assertEquals(OrderStatus.CONFIRMED, orderService.getOrder(response.id(), customerId).orderStatus());

        List<Reservation> reservations = reservationRepository.findByOrderId(response.id());
        assertEquals(1, reservations.size());
        assertEquals(ReservationStatus.CONFIRMED, reservations.getFirst().getReservationStatus());

        Stock stock = stockRepository.findByProductId(productId).orElseThrow();
        assertEquals(8, stock.getAvailableQuantity());
        assertEquals(0, stock.getReservedQuantity());

        Notification notification = notificationRepository.findAll().stream()
                .filter(n -> n.getOrderId().equals(response.id()))
                .findFirst()
                .orElseThrow();
        assertEquals(NotificationStatus.FAILED, notification.getStatus());
        assertEquals("smtp down", notification.getFailureReason());
        assertNull(notification.getSentAt());
    }
}
