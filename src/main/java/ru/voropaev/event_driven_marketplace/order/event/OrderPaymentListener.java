package ru.voropaev.event_driven_marketplace.order.event;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import ru.voropaev.event_driven_marketplace.order.service.OrderService;
import ru.voropaev.event_driven_marketplace.payment.event.PaymentCompleted;
import ru.voropaev.event_driven_marketplace.payment.event.PaymentFailed;

@Component
public class OrderPaymentListener {

    private final OrderService orderService;

    public OrderPaymentListener(OrderService orderService) {
        this.orderService = orderService;
    }

    @EventListener
    public void on(PaymentCompleted event) {
        orderService.confirmOrder(event.orderId());
    }

    @EventListener
    public void on(PaymentFailed event) {
        orderService.cancelOrderDueToPaymentFailure(event.orderId());
    }
}
