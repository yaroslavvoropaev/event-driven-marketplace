package ru.voropaev.event_driven_marketplace.payment.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import ru.voropaev.event_driven_marketplace.inventory.event.InventoryReserved;
import ru.voropaev.event_driven_marketplace.payment.domain.Payment;
import ru.voropaev.event_driven_marketplace.payment.domain.state.PaymentStatus;
import ru.voropaev.event_driven_marketplace.payment.gateway.PaymentGateway;
import ru.voropaev.event_driven_marketplace.payment.gateway.exception.PaymentDeclinedException;
import ru.voropaev.event_driven_marketplace.payment.gateway.exception.PaymentGatewayUnavailableException;
import ru.voropaev.event_driven_marketplace.payment.service.PaymentService;

import java.time.Instant;

@Component
public class InventoryReservedListener {
    private static final Logger log = LoggerFactory.getLogger(InventoryReservedListener.class);
    private final PaymentService paymentService;
    private final PaymentGateway paymentGateway;
    private final ApplicationEventPublisher applicationEventPublisher;

    public InventoryReservedListener(PaymentService paymentService, PaymentGateway paymentGateway, ApplicationEventPublisher applicationEventPublisher) {
        this.paymentService = paymentService;
        this.paymentGateway = paymentGateway;
        this.applicationEventPublisher = applicationEventPublisher;
    }


    @EventListener
    public void on(InventoryReserved event) {
        Payment payment = paymentService.createPending(event.orderId(), event.customerId(), event.totalAmount());
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
