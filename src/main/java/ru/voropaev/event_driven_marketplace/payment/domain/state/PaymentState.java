package ru.voropaev.event_driven_marketplace.payment.domain.state;

public interface PaymentState {
    PaymentStatus code();
    PaymentStatus succeed();
    PaymentStatus fail();
}
