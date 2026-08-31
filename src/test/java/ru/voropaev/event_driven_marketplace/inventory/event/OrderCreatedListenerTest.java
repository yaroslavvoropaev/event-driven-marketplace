package ru.voropaev.event_driven_marketplace.inventory.event;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import ru.voropaev.event_driven_marketplace.inventory.domain.exception.InsufficientStockException;
import ru.voropaev.event_driven_marketplace.inventory.service.InventoryService;
import ru.voropaev.event_driven_marketplace.order.event.OrderCreated;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class OrderCreatedListenerTest {

    @Mock
    private InventoryService inventoryService;
    @Mock
    private ApplicationEventPublisher applicationEventPublisher;

    @InjectMocks
    private OrderCreatedListener listener;

    @Test
    void publishesInventoryReserved_whenReservationSucceeds() {
        OrderCreated event = new OrderCreated(
                UUID.randomUUID(), "customer-1", BigDecimal.valueOf(100),
                List.of(new OrderCreated.OrderItemPayload(UUID.randomUUID(), 1)),
                Instant.now()
        );

        listener.on(event);

        ArgumentCaptor<InventoryReserved> captor = ArgumentCaptor.forClass(InventoryReserved.class);
        verify(applicationEventPublisher).publishEvent(captor.capture());
        assertEquals(event.orderId(), captor.getValue().orderId());
    }

    @Test
    void publishesInventoryReservationFailed_whenReservationThrows() {
        OrderCreated event = new OrderCreated(
                UUID.randomUUID(), "customer-1", BigDecimal.valueOf(100),
                List.of(new OrderCreated.OrderItemPayload(UUID.randomUUID(), 5)),
                Instant.now()
        );
        doThrow(new InsufficientStockException(5, 1))
                .when(inventoryService).reserveForOrder(event);

        listener.on(event);

        ArgumentCaptor<InventoryReservationFailed> captor = ArgumentCaptor.forClass(InventoryReservationFailed.class);
        verify(applicationEventPublisher).publishEvent(captor.capture());

        InventoryReservationFailed published = captor.getValue();
        assertEquals(event.orderId(), published.orderId());
        assertEquals("Cannot reserve 5, only 1 are available", published.reason());
    }
}
