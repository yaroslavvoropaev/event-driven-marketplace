package ru.voropaev.event_driven_marketplace.order.event;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.voropaev.event_driven_marketplace.inventory.event.InventoryReservationFailed;
import ru.voropaev.event_driven_marketplace.inventory.event.InventoryReserved;
import ru.voropaev.event_driven_marketplace.order.service.OrderService;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class InventoryReservationListenerTest {

    @Mock
    private OrderService orderService;

    @InjectMocks
    private InventoryReservationListener listener;

    @Test
    void startsProcessing_whenInventoryReserved() {
        UUID orderId = UUID.randomUUID();

        listener.on(new InventoryReserved(orderId, Instant.now()));

        verify(orderService).startProcessing(orderId);
    }

    @Test
    void cancelsOrder_whenInventoryReservationFailed() {
        UUID orderId = UUID.randomUUID();

        listener.on(new InventoryReservationFailed(orderId, "no stock", Instant.now()));

        verify(orderService).cancelOrderDueToReservationFailure(orderId);
    }
}
