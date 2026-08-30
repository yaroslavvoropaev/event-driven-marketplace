package ru.voropaev.event_driven_marketplace.order.domain.state;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class OrderStateResolver {
    private final Map<OrderStatus, OrderState> states;

    public OrderStateResolver(List<OrderState> stateBeans) {
        this.states = stateBeans.stream()
                .collect(Collectors.toMap(OrderState::code, state -> state));
    }

    public OrderState resolve(OrderStatus orderStatus) {
        return states.get(orderStatus);
    }
}
