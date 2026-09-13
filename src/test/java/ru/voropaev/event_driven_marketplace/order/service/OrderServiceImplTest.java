package ru.voropaev.event_driven_marketplace.order.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import ru.voropaev.event_driven_marketplace.inventory.service.InventoryService;
import ru.voropaev.event_driven_marketplace.order.api.dto.CreateOrderRequest;
import ru.voropaev.event_driven_marketplace.order.api.dto.OrderItemRequest;
import ru.voropaev.event_driven_marketplace.order.api.dto.OrderResponse;
import ru.voropaev.event_driven_marketplace.order.domain.state.exception.InvalidOrderTransitionException;
import ru.voropaev.event_driven_marketplace.order.domain.Order;
import ru.voropaev.event_driven_marketplace.order.domain.state.OrderState;
import ru.voropaev.event_driven_marketplace.order.domain.state.OrderStateResolver;
import ru.voropaev.event_driven_marketplace.order.domain.state.OrderStatus;
import ru.voropaev.event_driven_marketplace.order.event.OrderCreated;
import ru.voropaev.event_driven_marketplace.order.repository.OrderRepository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ExtendWith(MockitoExtension.class)
public class OrderServiceImplTest {

    @Mock
    private OrderRepository orderRepository;
    @Mock
    private OrderStateResolver orderStateResolver;
    @Mock
    private ApplicationEventPublisher applicationEventPublisher;
    @Mock
    private OrderState orderState;
    @Mock
    private InventoryService inventoryService;

    @InjectMocks
    OrderServiceImpl orderService;

    @Test
    public void saveOrderAndPublishEvent() {
        UUID customerId = UUID.randomUUID();
        OrderItemRequest itemRequest = new OrderItemRequest(UUID.randomUUID(), 2);
        CreateOrderRequest orderRequest = new CreateOrderRequest(List.of(itemRequest));
        when(inventoryService.getPrice(any(UUID.class))).thenReturn(BigDecimal.valueOf(100));

        OrderResponse response = orderService.createOrder(customerId, orderRequest);

        assertEquals(customerId, response.customerId());
        assertEquals(OrderStatus.CREATED, response.orderStatus());
        assertEquals(BigDecimal.valueOf(200), response.totalAmount());

        verify(orderRepository).save(any(Order.class));

        ArgumentCaptor<OrderCreated> eventCaptor = ArgumentCaptor.forClass(OrderCreated.class);
        verify(applicationEventPublisher).publishEvent(eventCaptor.capture());


        OrderCreated publishedEvent = eventCaptor.getValue();

        assertEquals(customerId, publishedEvent.customerId());
        assertEquals(BigDecimal.valueOf(200), publishedEvent.totalAmount());
        assertEquals(1, publishedEvent.items().size());
        assertEquals(2, publishedEvent.items().getFirst().quantity());

    }

    @Test
    public void returnsResponseWhenExists() {
        Order order = new Order(UUID.randomUUID());
        when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));

        OrderResponse response = orderService.getOrder(order.getId());

        assertEquals(order.getId(), response.id());
        assertEquals(order.getCustomerId(), response.customerId());
        assertEquals(OrderStatus.CREATED, response.orderStatus());
    }

    @Test
    public void throwsNotFoundWhenMissing() {
        UUID missingId = UUID.randomUUID();
        when(orderRepository.findById(missingId)).thenReturn(Optional.empty());

        assertThrows(OrderNotFoundException.class, () -> orderService.getOrder(missingId));
    }

    @Test
    public void updatesStatusWhenTransitionLegal() {
        Order order = new Order(UUID.randomUUID());
        when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));
        when(orderStateResolver.resolve(OrderStatus.CREATED)).thenReturn(orderState);
        when(orderState.cancel()).thenReturn(OrderStatus.CANCELLED);

        OrderResponse response = orderService.cancelOrder(order.getId());

        assertEquals(OrderStatus.CANCELLED, response.orderStatus());
    }

    @Test
    public void _propagatesExceptionWhenTransitionIllegal() {
        Order order = new Order(UUID.randomUUID());
        when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));
        when(orderStateResolver.resolve(OrderStatus.CREATED)).thenReturn(orderState);
        when(orderState.cancel())
                .thenThrow(new InvalidOrderTransitionException(OrderStatus.CREATED, "cancel"));

        assertThrows(InvalidOrderTransitionException.class, () -> orderService.cancelOrder(order.getId()));
    }

    @Test
    public void confirmsOrderWhenPaymentCompleted() {
        Order order = new Order(UUID.randomUUID());
        order.updateStatus(OrderStatus.PENDING);
        when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));
        when(orderStateResolver.resolve(OrderStatus.PENDING)).thenReturn(orderState);
        when(orderState.confirm()).thenReturn(OrderStatus.CONFIRMED);

        OrderResponse response = orderService.confirmOrder(order.getId());

        assertEquals(OrderStatus.CONFIRMED, response.orderStatus());
        assertEquals(OrderStatus.CONFIRMED, order.getOrderStatus());
    }

    @Test
    public void propagatesExceptionWhenConfirmIsIllegal() {
        Order order = new Order(UUID.randomUUID());
        when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));
        when(orderStateResolver.resolve(OrderStatus.CREATED)).thenReturn(orderState);
        when(orderState.confirm())
                .thenThrow(new InvalidOrderTransitionException(OrderStatus.CREATED, "confirm"));

        assertThrows(InvalidOrderTransitionException.class, () -> orderService.confirmOrder(order.getId()));
    }

    @Test
    public void throwsNotFoundWhenConfirmingMissingOrder() {
        UUID missingId = UUID.randomUUID();
        when(orderRepository.findById(missingId)).thenReturn(Optional.empty());

        assertThrows(OrderNotFoundException.class, () -> orderService.confirmOrder(missingId));
    }

}
