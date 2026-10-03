package ru.voropaev.event_driven_marketplace.order.saga;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import ru.voropaev.event_driven_marketplace.inventory.event.InventoryReservationFailed;
import ru.voropaev.event_driven_marketplace.inventory.event.InventoryReserved;
import ru.voropaev.event_driven_marketplace.order.domain.Order;
import ru.voropaev.event_driven_marketplace.order.domain.OrderItem;
import ru.voropaev.event_driven_marketplace.order.domain.state.OrderState;
import ru.voropaev.event_driven_marketplace.order.domain.state.OrderStateResolver;
import ru.voropaev.event_driven_marketplace.order.domain.state.OrderStatus;
import ru.voropaev.event_driven_marketplace.order.event.CancellationReason;
import ru.voropaev.event_driven_marketplace.order.event.OrderCancelled;
import ru.voropaev.event_driven_marketplace.order.event.OrderConfirmed;
import ru.voropaev.event_driven_marketplace.order.repository.OrderRepository;
import ru.voropaev.event_driven_marketplace.order.service.exception.OrderNotFoundException;
import ru.voropaev.event_driven_marketplace.payment.command.ChargePayment;
import ru.voropaev.event_driven_marketplace.payment.event.PaymentCompleted;
import ru.voropaev.event_driven_marketplace.payment.event.PaymentFailed;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Оркестратор действует от имени системы, а не покупателя: владельца он не проверяет,
 * а данные для команд берёт из заказа, а не из пришедшего ответа.
 */
@ExtendWith(MockitoExtension.class)
class OrderSagaOrchestratorTest {

    @Mock
    private OrderRepository orderRepository;
    @Mock
    private OrderStateResolver orderStateResolver;
    @Mock
    private ApplicationEventPublisher applicationEventPublisher;
    @Mock
    private OrderState orderState;

    @InjectMocks
    private OrderSagaOrchestrator orchestrator;

    private Order orderIn(OrderStatus status) {
        Order order = new Order(UUID.randomUUID());
        order.addItem(new OrderItem(UUID.randomUUID(), 2, new BigDecimal("50.00")));
        order.updateStatus(status);
        when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));
        when(orderStateResolver.resolve(status)).thenReturn(orderState);
        return order;
    }

    private <T> T published(Class<T> type) {
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(applicationEventPublisher).publishEvent(captor.capture());
        return type.cast(captor.getValue());
    }

    /**
     * Сумма и покупатель в команде — из заказа: InventoryReserved их больше не несёт,
     * и payment не должен ни у кого их спрашивать.
     */
    @Test
    void movesToPendingAndCommandsCharge_whenInventoryReserved() {
        Order order = orderIn(OrderStatus.CREATED);
        when(orderState.startProcessing()).thenReturn(OrderStatus.PENDING);

        orchestrator.on(new InventoryReserved(order.getId(), Instant.now()));

        assertEquals(OrderStatus.PENDING, order.getOrderStatus());
        ChargePayment command = published(ChargePayment.class);
        assertEquals(order.getId(), command.orderId());
        assertEquals(order.getCustomerId(), command.customerId());
        assertEquals(0, new BigDecimal("100.00").compareTo(command.amount()));
    }

    @Test
    void cancelsWithReservationReason_whenReservationFailed() {
        Order order = orderIn(OrderStatus.CREATED);
        when(orderState.cancel()).thenReturn(OrderStatus.CANCELLED);

        orchestrator.on(new InventoryReservationFailed(order.getId(), "not enough stock", Instant.now()));

        assertEquals(OrderStatus.CANCELLED, order.getOrderStatus());
        OrderCancelled event = published(OrderCancelled.class);
        assertEquals(order.getId(), event.orderId());
        assertEquals(CancellationReason.RESERVATION_FAILED, event.reason());
    }

    @Test
    void confirms_whenPaymentCompleted() {
        Order order = orderIn(OrderStatus.PENDING);
        when(orderState.confirm()).thenReturn(OrderStatus.CONFIRMED);

        orchestrator.on(new PaymentCompleted(
                order.getId(), UUID.randomUUID(), order.getCustomerId(),
                order.getTotalAmount(), "fake-tx-1", Instant.now()));

        assertEquals(OrderStatus.CONFIRMED, order.getOrderStatus());
        OrderConfirmed event = published(OrderConfirmed.class);
        assertEquals(order.getId(), event.orderId());
        assertEquals(order.getCustomerId(), event.customerId());
    }

    /**
     * Отказ платежа — это отмена, а не подтверждение: заказ и событие должны говорить
     * одно и то же. Иначе inventory отпустит резерв по OrderCancelled, а заказ останется
     * CONFIRMED.
     */
    @Test
    void cancelsWithPaymentReason_andNeverConfirms_whenPaymentFailed() {
        Order order = orderIn(OrderStatus.PENDING);
        when(orderState.cancel()).thenReturn(OrderStatus.CANCELLED);

        orchestrator.on(new PaymentFailed(
                order.getId(), UUID.randomUUID(), order.getCustomerId(),
                order.getTotalAmount(), "insufficient funds", Instant.now()));

        verify(orderState, never()).confirm();
        assertEquals(OrderStatus.CANCELLED, order.getOrderStatus());
        OrderCancelled event = published(OrderCancelled.class);
        assertEquals(CancellationReason.PAYMENT_FAILED, event.reason());
    }

    /**
     * Сага-ответ по неизвестному заказу. Пока бросаем; разбор таких случаев — в 4.0.2c.
     */
    @Test
    void throwsNotFound_andPublishesNothing_whenOrderMissing() {
        UUID missingId = UUID.randomUUID();
        when(orderRepository.findById(missingId)).thenReturn(Optional.empty());

        assertThrows(OrderNotFoundException.class,
                () -> orchestrator.on(new InventoryReserved(missingId, Instant.now())));

        verifyNoInteractions(applicationEventPublisher);
    }
}
