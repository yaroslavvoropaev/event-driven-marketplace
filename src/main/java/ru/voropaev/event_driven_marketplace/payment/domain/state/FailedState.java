package ru.voropaev.event_driven_marketplace.payment.domain.state;

import org.springframework.stereotype.Component;
import ru.voropaev.event_driven_marketplace.payment.domain.state.exception.InvalidPaymentTransitionException;

@Component("paymentFailedState")
public class FailedState implements PaymentState {
    @Override
    public PaymentStatus code() {
        return PaymentStatus.FAILED;
    }

    @Override
    public PaymentStatus succeed() {
        throw new InvalidPaymentTransitionException(PaymentStatus.FAILED, "succeed");
    }

    @Override
    public PaymentStatus fail() {
        throw new InvalidPaymentTransitionException(PaymentStatus.FAILED, "fail");
    }
}
