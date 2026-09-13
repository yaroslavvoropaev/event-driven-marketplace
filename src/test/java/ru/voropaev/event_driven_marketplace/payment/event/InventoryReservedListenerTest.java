package ru.voropaev.event_driven_marketplace.payment.event;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import ru.voropaev.event_driven_marketplace.inventory.event.InventoryReserved;
import ru.voropaev.event_driven_marketplace.payment.domain.Payment;
import ru.voropaev.event_driven_marketplace.payment.domain.state.PaymentStatus;
import ru.voropaev.event_driven_marketplace.payment.gateway.PaymentGateway;
import ru.voropaev.event_driven_marketplace.payment.gateway.exception.PaymentDeclinedException;
import ru.voropaev.event_driven_marketplace.payment.gateway.exception.PaymentGatewayUnavailableException;
import ru.voropaev.event_driven_marketplace.payment.service.PaymentService;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InventoryReservedListenerTest {

    private static final UUID ORDER_ID = UUID.randomUUID();
    private static final UUID CUSTOMER_ID = UUID.randomUUID();
    private static final BigDecimal AMOUNT = new BigDecimal("199.00");
    private static final String TRANSACTION_ID = "fake-tx-1";

    @Mock
    private PaymentService paymentService;
    @Mock
    private PaymentGateway paymentGateway;
    @Mock
    private ApplicationEventPublisher applicationEventPublisher;

    @InjectMocks
    private InventoryReservedListener listener;

    private final InventoryReserved event =
            new InventoryReserved(ORDER_ID, CUSTOMER_ID, AMOUNT, Instant.now());

    private Payment pendingPayment() {
        return Payment.pending(ORDER_ID, CUSTOMER_ID, AMOUNT);
    }

    @Test
    void publishesPaymentCompleted_whenGatewayCharges() {
        Payment payment = pendingPayment();
        when(paymentService.createPending(ORDER_ID, CUSTOMER_ID, AMOUNT)).thenReturn(payment);
        when(paymentGateway.charge(payment.getId(), CUSTOMER_ID, AMOUNT)).thenReturn(TRANSACTION_ID);

        listener.on(event);

        verify(paymentService).markSucceeded(payment.getId(), TRANSACTION_ID);

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(applicationEventPublisher).publishEvent(captor.capture());

        PaymentCompleted published = (PaymentCompleted) captor.getValue();
        assertEquals(ORDER_ID, published.orderId());
        assertEquals(payment.getId(), published.paymentId());
        assertEquals(CUSTOMER_ID, published.customerId());
        assertEquals(AMOUNT, published.amount());
        assertEquals(TRANSACTION_ID, published.gatewayTransactionId());
    }

    @Test
    void publishesPaymentFailed_whenGatewayDeclines() {
        Payment payment = pendingPayment();
        when(paymentService.createPending(ORDER_ID, CUSTOMER_ID, AMOUNT)).thenReturn(payment);
        when(paymentGateway.charge(payment.getId(), CUSTOMER_ID, AMOUNT))
                .thenThrow(new PaymentDeclinedException("insufficient funds"));

        listener.on(event);

        verify(paymentService).markFailed(payment.getId(), "insufficient funds");
        verify(paymentService, never()).markSucceeded(any(), anyString());

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(applicationEventPublisher).publishEvent(captor.capture());

        PaymentFailed published = (PaymentFailed) captor.getValue();
        assertEquals(ORDER_ID, published.orderId());
        assertEquals(payment.getId(), published.paymentId());
        assertEquals(CUSTOMER_ID, published.customerId());
        assertEquals("insufficient funds", published.reason());
    }

    /**
     * Исход неизвестен: деньги могли быть списаны. Ни одно решение о судьбе заказа
     * принимать нельзя, поэтому платёж остаётся PENDING и сага честно висит до
     * реконсиляции. Тест фиксирует именно бездействие.
     */
    @Test
    void leavesPaymentPending_whenGatewayOutcomeIsUnknown() {
        Payment payment = pendingPayment();
        when(paymentService.createPending(ORDER_ID, CUSTOMER_ID, AMOUNT)).thenReturn(payment);
        when(paymentGateway.charge(payment.getId(), CUSTOMER_ID, AMOUNT))
                .thenThrow(new PaymentGatewayUnavailableException("gateway timeout"));

        listener.on(event);

        verify(paymentService, never()).markSucceeded(any(), anyString());
        verify(paymentService, never()).markFailed(any(), anyString());
        verifyNoInteractions(applicationEventPublisher);
        assertEquals(PaymentStatus.PENDING, payment.getPaymentStatus());
    }

    @Test
    void doesNotChargeTwice_whenPaymentIsAlreadyCompleted() {
        Payment payment = pendingPayment();
        payment.applySuccess(PaymentStatus.SUCCEEDED, TRANSACTION_ID);
        when(paymentService.createPending(ORDER_ID, CUSTOMER_ID, AMOUNT)).thenReturn(payment);

        listener.on(event);

        verifyNoInteractions(paymentGateway);
        verifyNoInteractions(applicationEventPublisher);
        verify(paymentService, never()).markSucceeded(any(), anyString());
        verify(paymentService, never()).markFailed(eq(payment.getId()), anyString());
    }

}
