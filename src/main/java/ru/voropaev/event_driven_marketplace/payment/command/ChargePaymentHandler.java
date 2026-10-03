package ru.voropaev.event_driven_marketplace.payment.command;


import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import ru.voropaev.event_driven_marketplace.payment.domain.Payment;
import ru.voropaev.event_driven_marketplace.payment.domain.state.PaymentStatus;
import ru.voropaev.event_driven_marketplace.payment.event.PaymentCompleted;
import ru.voropaev.event_driven_marketplace.payment.event.PaymentFailed;
import ru.voropaev.event_driven_marketplace.payment.gateway.PaymentGateway;
import ru.voropaev.event_driven_marketplace.payment.gateway.exception.PaymentDeclinedException;
import ru.voropaev.event_driven_marketplace.payment.gateway.exception.PaymentGatewayUnavailableException;
import ru.voropaev.event_driven_marketplace.payment.service.PaymentService;

import java.time.Instant;

@Slf4j
@Component
public class ChargePaymentHandler {
    private final PaymentService paymentService;
    private final PaymentGateway paymentGateway;
    private final ApplicationEventPublisher applicationEventPublisher;

    public ChargePaymentHandler(PaymentService paymentService, PaymentGateway paymentGateway, ApplicationEventPublisher applicationEventPublisher) {
        this.paymentService = paymentService;
        this.paymentGateway = paymentGateway;
        this.applicationEventPublisher = applicationEventPublisher;
    }


    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(ChargePayment command){
        Payment payment = paymentService.createPending(command.orderId(), command.customerId(), command.amount());
        if (payment.getPaymentStatus() != PaymentStatus.PENDING) {
            return;
        }

        try {
            String txId = paymentGateway.charge(payment.getId(), payment.getCustomerId(), payment.getAmount());
            paymentService.markSucceeded(payment.getId(), txId);

            applicationEventPublisher.publishEvent(new PaymentCompleted(
                    payment.getOrderId(),
                    payment.getId(),
                    payment.getCustomerId(),
                    payment.getAmount(),
                    txId,
                    Instant.now()));
        } catch (PaymentDeclinedException exception) {
            paymentService.markFailed(payment.getId(), exception.getMessage());
            applicationEventPublisher.publishEvent(new PaymentFailed(
                    payment.getOrderId(),
                    payment.getId(),
                    payment.getCustomerId(),
                    payment.getAmount(),
                    exception.getMessage(),
                    Instant.now()
            ));
        } catch (PaymentGatewayUnavailableException exception) {
            log.error("Payment {} for order {} left in unknown state, manual reconciliation required",
                    payment.getId(), payment.getOrderId(), exception);
        }
    }
}

