package ru.voropaev.event_driven_marketplace.payment.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.voropaev.event_driven_marketplace.payment.domain.Payment;
import ru.voropaev.event_driven_marketplace.payment.domain.state.FailedState;
import ru.voropaev.event_driven_marketplace.payment.domain.state.PaymentStateResolver;
import ru.voropaev.event_driven_marketplace.payment.domain.state.PaymentStatus;
import ru.voropaev.event_driven_marketplace.payment.domain.state.PendingState;
import ru.voropaev.event_driven_marketplace.payment.domain.state.SucceedState;
import ru.voropaev.event_driven_marketplace.payment.domain.state.exception.InvalidPaymentTransitionException;
import ru.voropaev.event_driven_marketplace.payment.repository.PaymentRepository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentServiceImplTest {

    private static final UUID ORDER_ID = UUID.randomUUID();
    private static final UUID CUSTOMER_ID = UUID.randomUUID();
    private static final BigDecimal AMOUNT = new BigDecimal("199.00");

    @Mock
    private PaymentRepository paymentRepository;

    // резолвер настоящий, а не мок: это чистая доменная логика без зависимостей,
    // мок здесь проверял бы только то, что мы сами же и застабили
    private final PaymentStateResolver paymentStateResolver = new PaymentStateResolver(
            List.of(new PendingState(), new SucceedState(), new FailedState())
    );

    private PaymentServiceImpl paymentService;

    @BeforeEach
    void setUp() {
        paymentService = new PaymentServiceImpl(paymentRepository, paymentStateResolver);
    }

    private Payment pendingPayment() {
        return Payment.pending(ORDER_ID, CUSTOMER_ID, AMOUNT);
    }

    @Test
    void createPending_savesNewPayment_whenNoneExistsForOrder() {
        when(paymentRepository.findByOrderId(ORDER_ID)).thenReturn(Optional.empty());

        Payment returned = paymentService.createPending(ORDER_ID, CUSTOMER_ID, AMOUNT);

        ArgumentCaptor<Payment> captor = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).save(captor.capture());

        Payment saved = captor.getValue();
        assertSame(saved, returned);
        assertEquals(ORDER_ID, saved.getOrderId());
        assertEquals(CUSTOMER_ID, saved.getCustomerId());
        assertEquals(AMOUNT, saved.getAmount());
        assertEquals(PaymentStatus.PENDING, saved.getPaymentStatus());
        assertNull(saved.getGatewayTransactionId());
    }

    @Test
    void createPending_returnsExistingPayment_whenOrderAlreadyPaid() {
        Payment existing = pendingPayment();
        when(paymentRepository.findByOrderId(ORDER_ID)).thenReturn(Optional.of(existing));

        Payment returned = paymentService.createPending(ORDER_ID, CUSTOMER_ID, AMOUNT);

        assertSame(existing, returned);
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void markSucceeded_movesPaymentToSucceeded() {
        Payment payment = pendingPayment();
        when(paymentRepository.findById(payment.getId())).thenReturn(Optional.of(payment));

        paymentService.markSucceeded(payment.getId(), "fake-tx-1");

        assertEquals(PaymentStatus.SUCCEEDED, payment.getPaymentStatus());
        assertEquals("fake-tx-1", payment.getGatewayTransactionId());
        assertNull(payment.getFailureReason());
    }

    @Test
    void markFailed_movesPaymentToFailed() {
        Payment payment = pendingPayment();
        when(paymentRepository.findById(payment.getId())).thenReturn(Optional.of(payment));

        paymentService.markFailed(payment.getId(), "insufficient funds");

        assertEquals(PaymentStatus.FAILED, payment.getPaymentStatus());
        assertEquals("insufficient funds", payment.getFailureReason());
        assertNull(payment.getGatewayTransactionId());
    }

    @Test
    void markSucceeded_isRejected_whenPaymentAlreadySucceeded() {
        Payment payment = pendingPayment();
        payment.applySuccess(PaymentStatus.SUCCEEDED, "fake-tx-1");
        when(paymentRepository.findById(payment.getId())).thenReturn(Optional.of(payment));

        assertThrows(InvalidPaymentTransitionException.class,
                () -> paymentService.markSucceeded(payment.getId(), "fake-tx-2"));

        assertEquals("fake-tx-1", payment.getGatewayTransactionId());
    }

    @Test
    void markFailed_isRejected_whenPaymentAlreadyFailed() {
        Payment payment = pendingPayment();
        payment.applyFailure(PaymentStatus.FAILED, "insufficient funds");
        when(paymentRepository.findById(payment.getId())).thenReturn(Optional.of(payment));

        assertThrows(InvalidPaymentTransitionException.class,
                () -> paymentService.markFailed(payment.getId(), "another reason"));

        assertEquals("insufficient funds", payment.getFailureReason());
    }

    @Test
    void markSucceeded_throws_whenPaymentIsMissing() {
        UUID missingId = UUID.randomUUID();
        when(paymentRepository.findById(missingId)).thenReturn(Optional.empty());

        assertThrows(PaymentNotFoundException.class,
                () -> paymentService.markSucceeded(missingId, "fake-tx-1"));
    }

    @Test
    void markFailed_throws_whenPaymentIsMissing() {
        UUID missingId = UUID.randomUUID();
        when(paymentRepository.findById(missingId)).thenReturn(Optional.empty());

        assertThrows(PaymentNotFoundException.class,
                () -> paymentService.markFailed(missingId, "insufficient funds"));
    }

}
