package ru.voropaev.event_driven_marketplace.order.event;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import ru.voropaev.event_driven_marketplace.inventory.event.InventoryReservationFailed;
import ru.voropaev.event_driven_marketplace.inventory.event.InventoryReserved;
import ru.voropaev.event_driven_marketplace.order.service.OrderService;

@Component
public class InventoryReservationListener {

    private final OrderService orderService;


    public InventoryReservationListener(OrderService orderService) {
        this.orderService = orderService;
    }

    @EventListener
    public void on(InventoryReserved event) {
        orderService.startProcessing(event.orderId());
    }

    @EventListener
    public void on(InventoryReservationFailed event) {
        orderService.cancelOrderDueToReservationFailure(event.orderId());
    }
}
