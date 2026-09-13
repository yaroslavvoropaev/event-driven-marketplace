package ru.voropaev.event_driven_marketplace.payment.domain.state;

import org.springframework.stereotype.Component;
import ru.voropaev.event_driven_marketplace.payment.domain.state.exception.InvalidPaymentTransitionException;

@Component("paymentSuceedState")
public class SucceedState implements PaymentState {
    @Override
    public PaymentStatus code() {
        return PaymentStatus.SUCCEEDED;
    }

    @Override
    public PaymentStatus succeed() {
        throw new InvalidPaymentTransitionException(PaymentStatus.SUCCEEDED, "succeed");
    }

    @Override
    public PaymentStatus fail() {
        throw new InvalidPaymentTransitionException(PaymentStatus.SUCCEEDED, "fail");
    }
}
