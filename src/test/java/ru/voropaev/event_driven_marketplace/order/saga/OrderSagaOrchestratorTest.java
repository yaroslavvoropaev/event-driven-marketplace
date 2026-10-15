package ru.voropaev.event_driven_marketplace.order.saga;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.transaction.PlatformTransactionManager;
import ru.voropaev.event_driven_marketplace.common.retry.OptimisticLockRetrier;
import ru.voropaev.event_driven_marketplace.inventory.command.ConfirmReservation;
import ru.voropaev.event_driven_marketplace.inventory.command.ReleaseInventory;
import ru.voropaev.event_driven_marketplace.inventory.command.ReserveInventory;
import ru.voropaev.event_driven_marketplace.inventory.event.InventoryReservationFailed;
import ru.voropaev.event_driven_marketplace.inventory.event.InventoryReserved;
import ru.voropaev.event_driven_marketplace.order.domain.Order;
import ru.voropaev.event_driven_marketplace.order.domain.OrderItem;
import ru.voropaev.event_driven_marketplace.order.domain.state.CancelledState;
import ru.voropaev.event_driven_marketplace.order.domain.state.ConfirmedState;
import ru.voropaev.event_driven_marketplace.order.domain.state.CreatedState;
import ru.voropaev.event_driven_marketplace.order.domain.state.OrderStateResolver;
import ru.voropaev.event_driven_marketplace.order.domain.state.OrderStatus;
import ru.voropaev.event_driven_marketplace.order.domain.state.PendingState;
import ru.voropaev.event_driven_marketplace.order.event.OrderCancelled;
import ru.voropaev.event_driven_marketplace.order.event.OrderConfirmed;
import ru.voropaev.event_driven_marketplace.order.event.OrderCreated;
import ru.voropaev.event_driven_marketplace.order.repository.OrderRepository;
import ru.voropaev.event_driven_marketplace.payment.command.ChargePayment;
import ru.voropaev.event_driven_marketplace.payment.event.PaymentCompleted;
import ru.voropaev.event_driven_marketplace.payment.event.PaymentFailed;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderSagaOrchestratorTest {
    private static final int MAX_ATTEMPTS = 3;

    @Mock
    private OrderRepository orderRepository;
    @Mock
    private ApplicationEventPublisher applicationEventPublisher;
    @Mock
    private PlatformTransactionManager transactionManager;

    private OrderSagaOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        OrderStateResolver orderStateResolver = new OrderStateResolver(List.of(
                new CreatedState(), new PendingState(), new ConfirmedState(), new CancelledState()));
        orchestrator = new OrderSagaOrchestrator(
                orderRepository,
                orderStateResolver,
                applicationEventPublisher,
                new OptimisticLockRetrier(MAX_ATTEMPTS, 0),
                transactionManager);
    }

    enum Reply { INVENTORY_RESERVED, RESERVATION_FAILED, PAYMENT_COMPLETED, PAYMENT_FAILED }

    private void deliver(Reply reply, UUID orderId) {
        UUID paymentId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        BigDecimal amount = new BigDecimal("1.00");
        switch (reply) {
            case INVENTORY_RESERVED -> orchestrator.on(new InventoryReserved(orderId, Instant.now()));
            case RESERVATION_FAILED -> orchestrator.on(
                    new InventoryReservationFailed(orderId, "not enough stock", Instant.now()));
            case PAYMENT_COMPLETED -> orchestrator.on(
                    new PaymentCompleted(orderId, paymentId, customerId, amount, "fake-tx-1", Instant.now()));
            case PAYMENT_FAILED -> orchestrator.on(
                    new PaymentFailed(orderId, paymentId, customerId, amount, "insufficient funds", Instant.now()));
        }
    }

    private Order orderIn(OrderStatus status) {
        Order order = new Order(UUID.randomUUID());
        order.addItem(new OrderItem(UUID.randomUUID(), 2, new BigDecimal("50.00")));
        order.updateStatus(status);
        when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));
        return order;
    }

    private List<Object> allPublished() {
        return Mockito.mockingDetails(applicationEventPublisher).getInvocations().stream()
                .filter(invocation -> invocation.getMethod().getName().equals("publishEvent"))
                .map(invocation -> (Object) invocation.getArgument(0))
                .toList();
    }

    private <T> T published(Class<T> type) {
        List<T> matching = allPublished().stream().filter(type::isInstance).map(type::cast).toList();
        assertEquals(1, matching.size(), "expected exactly one " + type.getSimpleName());
        return matching.getFirst();
    }

    static Stream<Arguments> reactionTable() {
        return Stream.of(
                arguments(Reply.INVENTORY_RESERVED, OrderStatus.CREATED, OrderStatus.PENDING, List.of(ChargePayment.class)),
                arguments(Reply.INVENTORY_RESERVED, OrderStatus.PENDING, OrderStatus.PENDING, List.of()),
                arguments(Reply.INVENTORY_RESERVED, OrderStatus.CONFIRMED, OrderStatus.CONFIRMED, List.of()),
                arguments(Reply.INVENTORY_RESERVED, OrderStatus.CANCELLED, OrderStatus.CANCELLED, List.of(ReleaseInventory.class)),

                arguments(Reply.RESERVATION_FAILED, OrderStatus.CREATED, OrderStatus.CANCELLED, List.of(OrderCancelled.class)),
                arguments(Reply.RESERVATION_FAILED, OrderStatus.PENDING, OrderStatus.PENDING, List.of()),
                arguments(Reply.RESERVATION_FAILED, OrderStatus.CONFIRMED, OrderStatus.CONFIRMED, List.of()),
                arguments(Reply.RESERVATION_FAILED, OrderStatus.CANCELLED, OrderStatus.CANCELLED, List.of()),

                arguments(Reply.PAYMENT_COMPLETED, OrderStatus.CREATED, OrderStatus.CREATED, List.of()),
                arguments(Reply.PAYMENT_COMPLETED, OrderStatus.PENDING, OrderStatus.CONFIRMED,
                        List.of(OrderConfirmed.class, ConfirmReservation.class)),
                arguments(Reply.PAYMENT_COMPLETED, OrderStatus.CONFIRMED, OrderStatus.CONFIRMED, List.of()),
                arguments(Reply.PAYMENT_COMPLETED, OrderStatus.CANCELLED, OrderStatus.CANCELLED, List.of()),

                arguments(Reply.PAYMENT_FAILED, OrderStatus.CREATED, OrderStatus.CREATED, List.of()),
                arguments(Reply.PAYMENT_FAILED, OrderStatus.PENDING, OrderStatus.CANCELLED,
                        List.of(OrderCancelled.class, ReleaseInventory.class)),
                arguments(Reply.PAYMENT_FAILED, OrderStatus.CONFIRMED, OrderStatus.CONFIRMED, List.of()),
                arguments(Reply.PAYMENT_FAILED, OrderStatus.CANCELLED, OrderStatus.CANCELLED, List.of())
        );
    }

    @ParameterizedTest(name = "{0} in {1} -> {2}")
    @MethodSource("reactionTable")
    void reactsToReplyAccordingToOrderStatus(Reply reply, OrderStatus current,
                                             OrderStatus expectedStatus, List<Class<?>> expectedMessages) {
        Order order = orderIn(current);

        assertDoesNotThrow(() -> deliver(reply, order.getId()));

        assertEquals(expectedStatus, order.getOrderStatus());
        List<Class<?>> publishedTypes = allPublished().stream().<Class<?>>map(Object::getClass).toList();
        assertEquals(expectedMessages.size(), publishedTypes.size(), "published: " + publishedTypes);
        assertEquals(new HashSet<>(expectedMessages), new HashSet<>(publishedTypes));
    }

    @Test
    void commandsReservation_withoutTouchingOrderOrTransactions_whenOrderCreated() {
        UUID orderId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        OrderCreated event = new OrderCreated(
                orderId, UUID.randomUUID(), new BigDecimal("100.00"),
                List.of(new OrderCreated.OrderItemPayload(productId, 2)),
                Instant.now());

        orchestrator.on(event);

        verifyNoInteractions(orderRepository, transactionManager);
        ReserveInventory command = published(ReserveInventory.class);
        assertEquals(orderId, command.orderId());
        assertEquals(List.of(new ReserveInventory.Item(productId, 2)), command.items());
    }

    @ParameterizedTest
    @EnumSource(Reply.class)
    void dropsReplyWithoutError_whenOrderMissing(Reply reply) {
        UUID missingId = UUID.randomUUID();
        when(orderRepository.findById(missingId)).thenReturn(Optional.empty());

        assertDoesNotThrow(() -> deliver(reply, missingId));

        verifyNoInteractions(applicationEventPublisher);
    }

    @Test
    void rereadsOrderAndDecidesAgain_whenCommitHitsVersionConflict() {
        Order order = orderIn(OrderStatus.CREATED);
        doAnswer(invocation -> {
            order.updateStatus(OrderStatus.CANCELLED);
            throw new ObjectOptimisticLockingFailureException(Order.class, order.getId());
        }).doNothing().when(transactionManager).commit(any());

        assertDoesNotThrow(() -> deliver(Reply.INVENTORY_RESERVED, order.getId()));

        verify(orderRepository, times(2)).findById(order.getId());
        assertEquals(OrderStatus.CANCELLED, order.getOrderStatus());
        List<Object> published = allPublished();
        ReleaseInventory release = assertInstanceOf(ReleaseInventory.class, published.getLast());
        assertEquals(order.getId(), release.orderId());
    }

    @Test
    void givesUpWithoutError_whenVersionConflictPersists() {
        Order order = orderIn(OrderStatus.CREATED);
        doThrow(new ObjectOptimisticLockingFailureException(Order.class, order.getId()))
                .when(transactionManager).commit(any());

        assertDoesNotThrow(() -> deliver(Reply.INVENTORY_RESERVED, order.getId()));

        verify(orderRepository, times(MAX_ATTEMPTS)).findById(order.getId());
    }

    @Test
    void propagatesInfrastructureFailure_withoutRetry_andRollsBack() {
        UUID orderId = UUID.randomUUID();
        when(orderRepository.findById(orderId)).thenThrow(new DataAccessResourceFailureException("db down"));

        assertThrows(DataAccessResourceFailureException.class,
                () -> deliver(Reply.INVENTORY_RESERVED, orderId));

        verify(orderRepository, times(1)).findById(orderId);
        verify(transactionManager).rollback(any());
        verify(transactionManager, never()).commit(any());
        assertTrue(allPublished().isEmpty());
    }
}
