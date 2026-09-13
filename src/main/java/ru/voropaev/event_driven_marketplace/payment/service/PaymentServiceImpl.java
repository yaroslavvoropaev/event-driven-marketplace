package ru.voropaev.event_driven_marketplace.payment.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import ru.voropaev.event_driven_marketplace.payment.domain.Payment;
import ru.voropaev.event_driven_marketplace.payment.domain.state.PaymentStateResolver;
import ru.voropaev.event_driven_marketplace.payment.domain.state.PaymentStatus;
import ru.voropaev.event_driven_marketplace.payment.repository.PaymentRepository;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

@Service
public class PaymentServiceImpl implements PaymentService {
    private final PaymentRepository paymentRepository;
    private final PaymentStateResolver paymentStateResolver;
    public PaymentServiceImpl(PaymentRepository paymentRepository, PaymentStateResolver paymentStateResolver) {
        this.paymentRepository = paymentRepository;
        this.paymentStateResolver = paymentStateResolver;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Payment createPending(UUID orderId, UUID customerId, BigDecimal amount) {
        Optional<Payment> optPayment = paymentRepository.findByOrderId(orderId);
        if (optPayment.isPresent()) {
            return optPayment.get();
        }

        Payment payment = Payment.pending(orderId, customerId, amount);
        paymentRepository.save(payment);
        return payment;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markSucceeded(UUID paymentId, String gatewayTransactionId) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new PaymentNotFoundException(paymentId));

        PaymentStatus newStatus = paymentStateResolver.resolve(payment.getPaymentStatus()).succeed();
        payment.applySuccess(newStatus, gatewayTransactionId);
        paymentRepository.save(payment);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(UUID paymentId, String reason) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new PaymentNotFoundException(paymentId));

        PaymentStatus newStatus = paymentStateResolver.resolve(payment.getPaymentStatus()).fail();
        payment.applyFailure(newStatus, reason);
        paymentRepository.save(payment);
    }
}
