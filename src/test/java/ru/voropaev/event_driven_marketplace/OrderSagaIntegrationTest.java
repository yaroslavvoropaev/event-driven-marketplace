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
import ru.voropaev.event_driven_marketplace.order.service.OrderNotFoundException;
import ru.voropaev.event_driven_marketplace.order.service.OrderService;
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

/**
 * Сквозной тест саги на настоящем Postgres: от создания заказа до терминального состояния.
 * <p>
 * Цепочка выполняется синхронно внутри вызова {@code createOrder}, поэтому промежуточные
 * состояния (заказ PENDING, резерв RESERVED) здесь ненаблюдаемы — проверяется только
 * то, чем сага закончилась.
 * <p>
 * Суммы подобраны под правило {@code FakePaymentGateway}: копейки {@code 13} — отказ,
 * {@code 99} — неизвестный исход, остальное — успех. Меняя цену товара в тесте, следи
 * за итоговой суммой заказа, иначе исход платежа изменится молча.
 */
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

        // 3 x 10.00 = 30.00 -> копейки 00 -> шлюз подтверждает списание
        CreateOrderRequest request = new CreateOrderRequest(
                List.of(new OrderItemRequest(productId, 3))
        );

        OrderResponse response = orderService.createOrder(customerId, request);

        assertEquals(OrderStatus.CONFIRMED, orderService.getOrder(response.id(), customerId).orderStatus());

        // товар уехал клиенту: из резерва списан, в доступные не вернулся
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

        InventoryReserved inventoryReserved = events.stream(InventoryReserved.class)
                .filter(e -> e.orderId().equals(response.id()))
                .findFirst()
                .orElseThrow();
        assertEquals(customerId, inventoryReserved.customerId());
        assertEquals(0, inventoryReserved.totalAmount().compareTo(response.totalAmount()));

        PaymentCompleted paymentCompleted = events.stream(PaymentCompleted.class)
                .filter(e -> e.orderId().equals(response.id()))
                .findFirst()
                .orElseThrow();
        assertEquals(payment.getId(), paymentCompleted.paymentId());
        assertEquals(payment.getGatewayTransactionId(), paymentCompleted.gatewayTransactionId());

        // товар списывается по факту подтверждения заказа, а не по факту оплаты:
        // inventory узнаёт исход от order и ничего не знает про платёжный домен
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

        // 1 x 10.13 = 10.13 -> копейки 13 -> шлюз отказывает
        CreateOrderRequest request = new CreateOrderRequest(
                List.of(new OrderItemRequest(productId, 1))
        );

        OrderResponse response = orderService.createOrder(customerId, request);

        assertEquals(OrderStatus.CANCELLED, orderService.getOrder(response.id(), customerId).orderStatus());

        // компенсация вернула товар на полку: было 10, зарезервировали 1, отпустили обратно
        Stock compensatedStock = stockRepository.findByProductId(productId).orElseThrow();
        assertEquals(10, compensatedStock.getAvailableQuantity());
        assertEquals(0, compensatedStock.getReservedQuantity());

        // резерв не удалён, а переведён в RELEASED — история саги остаётся в БД
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

        // до платежа дело не дошло — платёжной записи быть не должно
        assertTrue(paymentRepository.findByOrderId(response.id()).isEmpty());

        boolean reservationFailed = events.stream(InventoryReservationFailed.class)
                .anyMatch(e -> e.orderId().equals(response.id()));
        assertTrue(reservationFailed);

        assertEquals(CancellationReason.RESERVATION_FAILED, cancellationReasonOf(events, response.id()));
    }

    /**
     * Причина отмены — часть контракта события, а не служебная деталь: notification
     * будет писать разный текст для «вы отменили сами», «товара не хватило» и
     * «платёж отклонён». Поэтому каждый из трёх путей отмены проверяется отдельно.
     */
    private CancellationReason cancellationReasonOf(ApplicationEvents events, UUID orderId) {
        return events.stream(OrderCancelled.class)
                .filter(e -> e.orderId().equals(orderId))
                .findFirst()
                .orElseThrow()
                .reason();
    }

    /**
     * Ручная отмена — единственный путь, на котором резерв раньше терялся навсегда:
     * заказ уходил в CANCELLED, а товар оставался занятым, потому что резерв отпускал
     * только слушатель PaymentFailed.
     * <p>
     * Фикстура — зависшая сага: копейки {@code 99} заставляют шлюз не ответить, событий
     * не публикуется, и заказ остаётся в PENDING с живым резервом. Это единственное
     * состояние, из которого отмену вообще можно позвать: при любом другом исходе сага
     * доводит заказ до терминального статуса внутри createOrder.
     */
    @Test
    void returnsStockToTheShelf_whenCustomerCancelsPendingOrder(ApplicationEvents events) {
        UUID productId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        stockRepository.save(new Stock(productId, new BigDecimal("10.99"), 10, 0));

        OrderResponse response = orderService.createOrder(customerId, new CreateOrderRequest(
                List.of(new OrderItemRequest(productId, 1))
        ));

        assertEquals(OrderStatus.PENDING, orderService.getOrder(response.id(), customerId).orderStatus());
        Stock reserved = stockRepository.findByProductId(productId).orElseThrow();
        assertEquals(9, reserved.getAvailableQuantity());
        assertEquals(1, reserved.getReservedQuantity());

        orderService.cancelOrder(response.id(), customerId);

        assertEquals(OrderStatus.CANCELLED, orderService.getOrder(response.id(), customerId).orderStatus());

        Stock released = stockRepository.findByProductId(productId).orElseThrow();
        assertEquals(10, released.getAvailableQuantity());
        assertEquals(0, released.getReservedQuantity());

        List<Reservation> reservations = reservationRepository.findByOrderId(response.id());
        assertEquals(1, reservations.size());
        assertEquals(ReservationStatus.RELEASED, reservations.getFirst().getReservationStatus());

        assertEquals(CancellationReason.CUSTOMER_REQUEST, cancellationReasonOf(events, response.id()));
    }

    /**
     * Повторная отмена отбивается state machine до публикации события, поэтому резерв
     * не отпускается второй раз. Если бы отбилась позже, release прошёл бы дважды и
     * вернул на полку вдвое больше товара, чем было снято.
     */
    @Test
    void doesNotReleaseStockTwice_whenCancelIsRepeated() {
        UUID productId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        stockRepository.save(new Stock(productId, new BigDecimal("10.99"), 10, 0));

        OrderResponse response = orderService.createOrder(customerId, new CreateOrderRequest(
                List.of(new OrderItemRequest(productId, 1))
        ));
        orderService.cancelOrder(response.id(), customerId);

        assertThrows(InvalidOrderTransitionException.class,
                () -> orderService.cancelOrder(response.id(), customerId));

        Stock stock = stockRepository.findByProductId(productId).orElseThrow();
        assertEquals(10, stock.getAvailableQuantity());
        assertEquals(0, stock.getReservedQuantity());
    }

    /**
     * Отмена чужого заказа не должна доходить до inventory: проверка владельца стоит
     * раньше смены статуса, значит OrderCancelled не публикуется и резерв остаётся.
     */
    @Test
    void keepsStockReserved_whenStrangerTriesToCancel() {
        UUID productId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        stockRepository.save(new Stock(productId, new BigDecimal("10.99"), 10, 0));

        OrderResponse response = orderService.createOrder(customerId, new CreateOrderRequest(
                List.of(new OrderItemRequest(productId, 1))
        ));

        assertThrows(OrderNotFoundException.class,
                () -> orderService.cancelOrder(response.id(), UUID.randomUUID()));

        Stock stock = stockRepository.findByProductId(productId).orElseThrow();
        assertEquals(9, stock.getAvailableQuantity());
        assertEquals(1, stock.getReservedQuantity());
        assertEquals(OrderStatus.PENDING, orderService.getOrder(response.id(), customerId).orderStatus());
    }
}
