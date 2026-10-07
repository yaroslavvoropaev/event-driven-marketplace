package ru.voropaev.event_driven_marketplace;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import ru.voropaev.event_driven_marketplace.inventory.command.ConfirmReservation;
import ru.voropaev.event_driven_marketplace.inventory.command.ReserveInventory;
import ru.voropaev.event_driven_marketplace.inventory.domain.Reservation;
import ru.voropaev.event_driven_marketplace.inventory.domain.ReservationStatus;
import ru.voropaev.event_driven_marketplace.inventory.domain.Stock;
import ru.voropaev.event_driven_marketplace.inventory.event.InventoryReserved;
import ru.voropaev.event_driven_marketplace.inventory.repository.ReservationRepository;
import ru.voropaev.event_driven_marketplace.inventory.repository.StockRepository;
import ru.voropaev.event_driven_marketplace.inventory.service.InventoryService;
import ru.voropaev.event_driven_marketplace.order.api.dto.CreateOrderRequest;
import ru.voropaev.event_driven_marketplace.order.api.dto.OrderItemRequest;
import ru.voropaev.event_driven_marketplace.order.api.dto.OrderResponse;
import ru.voropaev.event_driven_marketplace.order.domain.Order;
import ru.voropaev.event_driven_marketplace.order.domain.OrderItem;
import ru.voropaev.event_driven_marketplace.order.domain.state.OrderStatus;
import ru.voropaev.event_driven_marketplace.order.event.OrderConfirmed;
import ru.voropaev.event_driven_marketplace.order.repository.OrderRepository;
import ru.voropaev.event_driven_marketplace.order.service.OrderService;
import ru.voropaev.event_driven_marketplace.payment.command.ChargePayment;
import ru.voropaev.event_driven_marketplace.payment.event.PaymentCompleted;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@RecordApplicationEvents
@ActiveProfiles("test")
class OrderSagaResilienceIntegrationTest {
    @Autowired
    private ApplicationEventPublisher applicationEventPublisher;
    @Autowired
    private OrderService orderService;
    @Autowired
    private OrderRepository orderRepository;
    @Autowired
    private InventoryService inventoryService;
    @Autowired
    private StockRepository stockRepository;
    @Autowired
    private ReservationRepository reservationRepository;

    private UUID cancelledOrderWithLiveReservation(UUID productId, int quantity) {
        Order order = new Order(UUID.randomUUID());
        order.addItem(new OrderItem(productId, quantity, BigDecimal.TEN));
        order.updateStatus(OrderStatus.CANCELLED);
        orderRepository.save(order);

        inventoryService.reserveForOrder(new ReserveInventory(
                order.getId(), List.of(new ReserveInventory.Item(productId, quantity)), Instant.now()));
        return order.getId();
    }

    @Test
    void returnsStockToShelf_whenReservationArrivesForCancelledOrder(ApplicationEvents events) {
        UUID productId = UUID.randomUUID();
        stockRepository.save(new Stock(productId, BigDecimal.TEN, 10, 0));
        UUID orderId = cancelledOrderWithLiveReservation(productId, 2);

        applicationEventPublisher.publishEvent(new InventoryReserved(orderId, Instant.now()));

        Stock stock = stockRepository.findByProductId(productId).orElseThrow();
        assertEquals(10, stock.getAvailableQuantity());
        assertEquals(0, stock.getReservedQuantity());
        List<Reservation> reservations = reservationRepository.findByOrderId(orderId);
        assertEquals(ReservationStatus.RELEASED, reservations.getFirst().getReservationStatus());

        assertEquals(OrderStatus.CANCELLED, orderRepository.findById(orderId).orElseThrow().getOrderStatus());
        assertEquals(0, events.stream(ChargePayment.class).filter(c -> c.orderId().equals(orderId)).count());
    }

    @Test
    void ignoresDuplicatePaymentCompleted_forConfirmedOrder(ApplicationEvents events) {
        UUID productId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        stockRepository.save(new Stock(productId, BigDecimal.TEN, 10, 0));

        OrderResponse response = orderService.createOrder(customerId, new CreateOrderRequest(
                List.of(new OrderItemRequest(productId, 3))));
        assertEquals(OrderStatus.CONFIRMED, orderService.getOrder(response.id(), customerId).orderStatus());

        assertDoesNotThrow(() -> applicationEventPublisher.publishEvent(new PaymentCompleted(
                response.id(), UUID.randomUUID(), customerId, response.totalAmount(), "fake-tx-dup", Instant.now())));

        assertEquals(OrderStatus.CONFIRMED, orderService.getOrder(response.id(), customerId).orderStatus());
        assertEquals(1, events.stream(OrderConfirmed.class).filter(e -> e.orderId().equals(response.id())).count());
        assertEquals(1, events.stream(ConfirmReservation.class).filter(c -> c.orderId().equals(response.id())).count());
        Stock stock = stockRepository.findByProductId(productId).orElseThrow();
        assertEquals(7, stock.getAvailableQuantity());
        assertEquals(0, stock.getReservedQuantity());
    }
}
