package ru.voropaev.event_driven_marketplace.payment.domain.state;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class PaymentStateResolver {
    private final Map<PaymentStatus, PaymentState> states;

    public PaymentStateResolver(List<PaymentState> stateBeans) {
        this.states = stateBeans.stream()
                .collect(Collectors.toMap(PaymentState::code, state -> state));
    }

    public PaymentState resolve(PaymentStatus paymentStatus) {
        return states.get(paymentStatus);
    }
}
