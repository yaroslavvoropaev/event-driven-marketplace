package ru.voropaev.event_driven_marketplace;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import ru.voropaev.event_driven_marketplace.inventory.domain.Reservation;
import ru.voropaev.event_driven_marketplace.inventory.domain.ReservationStatus;
import ru.voropaev.event_driven_marketplace.inventory.domain.Stock;
import ru.voropaev.event_driven_marketplace.inventory.event.InventoryReservationFailed;
import ru.voropaev.event_driven_marketplace.inventory.event.InventoryReserved;
import ru.voropaev.event_driven_marketplace.inventory.repository.ReservationRepository;
import ru.voropaev.event_driven_marketplace.inventory.repository.StockRepository;
import ru.voropaev.event_driven_marketplace.order.api.dto.CreateOrderRequest;
import ru.voropaev.event_driven_marketplace.order.api.dto.OrderItemRequest;
import ru.voropaev.event_driven_marketplace.order.api.dto.OrderResponse;
import ru.voropaev.event_driven_marketplace.order.domain.state.OrderStatus;
import ru.voropaev.event_driven_marketplace.order.service.OrderService;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@RecordApplicationEvents
class InventoryReservationIntegrationTest {

    @Autowired
    private OrderService orderService;
    @Autowired
    private StockRepository stockRepository;
    @Autowired
    private ReservationRepository reservationRepository;

    @Test
    void reservesStockAndPublishesInventoryReserved_whenEnoughStock(ApplicationEvents events) {
        UUID productId = UUID.randomUUID();
        stockRepository.save(new Stock(productId, BigDecimal.TEN, 10, 0));

        CreateOrderRequest request = new CreateOrderRequest(
                "customer-1",
                List.of(new OrderItemRequest(productId, 3))
        );

        OrderResponse response = orderService.createOrder(request);

        Stock updatedStock = stockRepository.findByProductId(productId).orElseThrow();
        assertEquals(7, updatedStock.getAvailableQuantity());
        assertEquals(3, updatedStock.getReservedQuantity());

        List<Reservation> reservationsForOrder = reservationRepository.findAll().stream()
                .filter(r -> r.getOrderId().equals(response.id()))
                .toList();
        assertEquals(1, reservationsForOrder.size());
        assertEquals(ReservationStatus.RESERVED, reservationsForOrder.getFirst().getReservationStatus());

        boolean publishedInventoryReserved = events.stream(InventoryReserved.class)
                .anyMatch(e -> e.orderId().equals(response.id()));
        assertTrue(publishedInventoryReserved);

        assertEquals(OrderStatus.PENDING, orderService.getOrder(response.id()).orderStatus());
    }

    @Test
    void rollsBackAndPublishesInventoryReservationFailed_whenNotEnoughStock(ApplicationEvents events) {
        UUID productId = UUID.randomUUID();
        stockRepository.save(new Stock(productId, BigDecimal.TEN, 1, 0));

        CreateOrderRequest request = new CreateOrderRequest(
                "customer-1",
                List.of(new OrderItemRequest(productId, 3))
        );

        OrderResponse response = orderService.createOrder(request);

        Stock unchangedStock = stockRepository.findByProductId(productId).orElseThrow();
        assertEquals(1, unchangedStock.getAvailableQuantity());
        assertEquals(0, unchangedStock.getReservedQuantity());

        long reservationsForOrder = reservationRepository.findAll().stream()
                .filter(r -> r.getOrderId().equals(response.id()))
                .count();
        assertEquals(0, reservationsForOrder);

        boolean publishedInventoryReservationFailed = events.stream(InventoryReservationFailed.class)
                .anyMatch(e -> e.orderId().equals(response.id()));
        assertTrue(publishedInventoryReservationFailed);

        assertEquals(OrderStatus.CANCELLED, orderService.getOrder(response.id()).orderStatus());
    }
}
