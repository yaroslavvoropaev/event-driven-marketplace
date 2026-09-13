package ru.voropaev.event_driven_marketplace.payment.domain.state;

import org.springframework.stereotype.Component;

@Component("paymentPendingState")
public class PendingState implements PaymentState {
    @Override
    public PaymentStatus code() {
        return PaymentStatus.PENDING;
    }

    @Override
    public PaymentStatus succeed() {
        return PaymentStatus.SUCCEEDED;
    }

    @Override
    public PaymentStatus fail() {
        return PaymentStatus.FAILED;
    }
}
