package ru.voropaev.event_driven_marketplace;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import ru.voropaev.event_driven_marketplace.inventory.domain.Reservation;
import ru.voropaev.event_driven_marketplace.inventory.domain.ReservationStatus;
import ru.voropaev.event_driven_marketplace.inventory.domain.Stock;
import ru.voropaev.event_driven_marketplace.inventory.event.InventoryReservationFailed;
import ru.voropaev.event_driven_marketplace.inventory.event.InventoryReserved;
import ru.voropaev.event_driven_marketplace.inventory.repository.ReservationRepository;
import ru.voropaev.event_driven_marketplace.inventory.repository.StockRepository;
import ru.voropaev.event_driven_marketplace.order.api.dto.CreateOrderRequest;
import ru.voropaev.event_driven_marketplace.order.api.dto.OrderItemRequest;
import ru.voropaev.event_driven_marketplace.order.api.dto.OrderResponse;
import ru.voropaev.event_driven_marketplace.order.domain.state.OrderStatus;
import ru.voropaev.event_driven_marketplace.order.domain.state.exception.InvalidOrderTransitionException;
import ru.voropaev.event_driven_marketplace.order.event.CancellationReason;
import ru.voropaev.event_driven_marketplace.order.event.OrderCancelled;
import ru.voropaev.event_driven_marketplace.order.event.OrderConfirmed;
import ru.voropaev.event_driven_marketplace.order.service.OrderService;
import ru.voropaev.event_driven_marketplace.payment.command.ChargePayment;
import ru.voropaev.event_driven_marketplace.payment.domain.Payment;
import ru.voropaev.event_driven_marketplace.payment.domain.state.PaymentStatus;
import ru.voropaev.event_driven_marketplace.payment.event.PaymentCompleted;
import ru.voropaev.event_driven_marketplace.payment.event.PaymentFailed;
import ru.voropaev.event_driven_marketplace.payment.repository.PaymentRepository;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@RecordApplicationEvents
@ActiveProfiles("test")
class OrderSagaIntegrationTest {
    @Autowired
    private OrderService orderService;
    @Autowired
    private StockRepository stockRepository;
    @Autowired
    private ReservationRepository reservationRepository;
    @Autowired
    private PaymentRepository paymentRepository;

    @Test
    void confirmsOrderAndWritesOffStock_whenStockAndPaymentSucceed(ApplicationEvents events) {
        UUID productId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        stockRepository.save(new Stock(productId, BigDecimal.TEN, 10, 0));

        CreateOrderRequest request = new CreateOrderRequest(
                List.of(new OrderItemRequest(productId, 3))
        );

        OrderResponse response = orderService.createOrder(customerId, request);

        assertEquals(OrderStatus.CONFIRMED, orderService.getOrder(response.id(), customerId).orderStatus());

        Stock updatedStock = stockRepository.findByProductId(productId).orElseThrow();
        assertEquals(7, updatedStock.getAvailableQuantity());
        assertEquals(0, updatedStock.getReservedQuantity());

        List<Reservation> reservations = reservationRepository.findByOrderId(response.id());
        assertEquals(1, reservations.size());
        assertEquals(ReservationStatus.CONFIRMED, reservations.getFirst().getReservationStatus());

        Payment payment = paymentRepository.findByOrderId(response.id()).orElseThrow();
        assertEquals(PaymentStatus.SUCCEEDED, payment.getPaymentStatus());
        assertEquals(customerId, payment.getCustomerId());
        assertEquals(0, payment.getAmount().compareTo(response.totalAmount()));
        assertEquals("fake-tx-" + payment.getId(), payment.getGatewayTransactionId());
        assertNotNull(payment.getUpdatedAt());

        assertEquals(1, events.stream(InventoryReserved.class)
                .filter(e -> e.orderId().equals(response.id()))
                .count());

        ChargePayment chargePayment = events.stream(ChargePayment.class)
                .filter(c -> c.orderId().equals(response.id()))
                .findFirst()
                .orElseThrow();
        assertEquals(customerId, chargePayment.customerId());
        assertEquals(0, chargePayment.amount().compareTo(response.totalAmount()));

        PaymentCompleted paymentCompleted = events.stream(PaymentCompleted.class)
                .filter(e -> e.orderId().equals(response.id()))
                .findFirst()
                .orElseThrow();
        assertEquals(payment.getId(), paymentCompleted.paymentId());
        assertEquals(payment.getGatewayTransactionId(), paymentCompleted.gatewayTransactionId());

        OrderConfirmed orderConfirmed = events.stream(OrderConfirmed.class)
                .filter(e -> e.orderId().equals(response.id()))
                .findFirst()
                .orElseThrow();
        assertEquals(customerId, orderConfirmed.customerId());
        assertEquals(0, orderConfirmed.totalAmount().compareTo(response.totalAmount()));
    }

    @Test
    void compensatesReservationAndCancelsOrder_whenPaymentIsDeclined(ApplicationEvents events) {
        UUID productId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        stockRepository.save(new Stock(productId, new BigDecimal("10.13"), 10, 0));

        CreateOrderRequest request = new CreateOrderRequest(
                List.of(new OrderItemRequest(productId, 1))
        );

        OrderResponse response = orderService.createOrder(customerId, request);

        assertEquals(OrderStatus.CANCELLED, orderService.getOrder(response.id(), customerId).orderStatus());

        Stock compensatedStock = stockRepository.findByProductId(productId).orElseThrow();
        assertEquals(10, compensatedStock.getAvailableQuantity());
        assertEquals(0, compensatedStock.getReservedQuantity());

        List<Reservation> reservations = reservationRepository.findByOrderId(response.id());
        assertEquals(1, reservations.size());
        assertEquals(ReservationStatus.RELEASED, reservations.getFirst().getReservationStatus());

        Payment payment = paymentRepository.findByOrderId(response.id()).orElseThrow();
        assertEquals(PaymentStatus.FAILED, payment.getPaymentStatus());
        assertEquals("insufficient funds", payment.getFailureReason());
        assertNull(payment.getGatewayTransactionId());

        PaymentFailed paymentFailed = events.stream(PaymentFailed.class)
                .filter(e -> e.orderId().equals(response.id()))
                .findFirst()
                .orElseThrow();
        assertEquals(payment.getId(), paymentFailed.paymentId());
        assertEquals(customerId, paymentFailed.customerId());

        assertEquals(CancellationReason.PAYMENT_FAILED, cancellationReasonOf(events, response.id()));
    }

    @Test
    void cancelsOrderAndLeavesStockUntouched_whenNotEnoughStock(ApplicationEvents events) {
        UUID productId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        stockRepository.save(new Stock(productId, BigDecimal.TEN, 1, 0));

        CreateOrderRequest request = new CreateOrderRequest(
                List.of(new OrderItemRequest(productId, 3))
        );

        OrderResponse response = orderService.createOrder(customerId, request);

        assertEquals(OrderStatus.CANCELLED, orderService.getOrder(response.id(), customerId).orderStatus());

        Stock unchangedStock = stockRepository.findByProductId(productId).orElseThrow();
        assertEquals(1, unchangedStock.getAvailableQuantity());
        assertEquals(0, unchangedStock.getReservedQuantity());

        assertTrue(reservationRepository.findByOrderId(response.id()).isEmpty());

        assertTrue(paymentRepository.findByOrderId(response.id()).isEmpty());

        boolean reservationFailed = events.stream(InventoryReservationFailed.class)
                .anyMatch(e -> e.orderId().equals(response.id()));
        assertTrue(reservationFailed);

        assertEquals(CancellationReason.RESERVATION_FAILED, cancellationReasonOf(events, response.id()));
    }

    private CancellationReason cancellationReasonOf(ApplicationEvents events, UUID orderId) {
        return events.stream(OrderCancelled.class)
                .filter(e -> e.orderId().equals(orderId))
                .findFirst()
                .orElseThrow()
                .reason();
    }

    @Test
    void rejectsCustomerCancel_andKeepsReservation_whenPaymentOutcomeUnknown(ApplicationEvents events) {
        UUID productId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        stockRepository.save(new Stock(productId, new BigDecimal("10.99"), 10, 0));

        OrderResponse response = orderService.createOrder(customerId, new CreateOrderRequest(
                List.of(new OrderItemRequest(productId, 1))
        ));
        assertEquals(OrderStatus.PENDING, orderService.getOrder(response.id(), customerId).orderStatus());

        assertThrows(InvalidOrderTransitionException.class,
                () -> orderService.cancelOrder(response.id(), customerId));

        assertEquals(OrderStatus.PENDING, orderService.getOrder(response.id(), customerId).orderStatus());
        Stock stock = stockRepository.findByProductId(productId).orElseThrow();
        assertEquals(9, stock.getAvailableQuantity());
        assertEquals(1, stock.getReservedQuantity());

        List<Reservation> reservations = reservationRepository.findByOrderId(response.id());
        assertEquals(1, reservations.size());
        assertEquals(ReservationStatus.RESERVED, reservations.getFirst().getReservationStatus());

        assertTrue(events.stream(OrderCancelled.class).noneMatch(e -> e.orderId().equals(response.id())));
    }
}
