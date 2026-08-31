package ru.voropaev.event_driven_marketplace.inventory.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.voropaev.event_driven_marketplace.inventory.domain.Reservation;
import ru.voropaev.event_driven_marketplace.inventory.domain.ReservationStatus;
import ru.voropaev.event_driven_marketplace.inventory.domain.Stock;
import ru.voropaev.event_driven_marketplace.inventory.domain.exception.InsufficientStockException;
import ru.voropaev.event_driven_marketplace.inventory.domain.exception.StockNotFoundException;
import ru.voropaev.event_driven_marketplace.inventory.repository.ReservationRepository;
import ru.voropaev.event_driven_marketplace.inventory.repository.StockRepository;
import ru.voropaev.event_driven_marketplace.order.event.OrderCreated;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InventoryServiceImplTest {

    @Mock
    private StockRepository stockRepository;
    @Mock
    private ReservationRepository reservationRepository;

    @InjectMocks
    private InventoryServiceImpl inventoryService;

    @Test
    void reservesStockAndSavesReservation_whenEnoughQuantity() {
        UUID orderId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        Stock stock = new Stock(productId, BigDecimal.TEN, 10, 0);
        when(stockRepository.findByProductId(productId)).thenReturn(Optional.of(stock));

        OrderCreated event = new OrderCreated(
                orderId, "customer-1", BigDecimal.valueOf(300),
                List.of(new OrderCreated.OrderItemPayload(productId, 3)),
                Instant.now()
        );

        inventoryService.reserveForOrder(event);

        assertEquals(7, stock.getAvailableQuantity());
        assertEquals(3, stock.getReservedQuantity());

        ArgumentCaptor<Reservation> captor = ArgumentCaptor.forClass(Reservation.class);
        verify(reservationRepository).save(captor.capture());

        Reservation reservation = captor.getValue();
        assertEquals(orderId, reservation.getOrderId());
        assertEquals(productId, reservation.getProductId());
        assertEquals(3, reservation.getQuantity());
        assertEquals(ReservationStatus.RESERVED, reservation.getReservationStatus());
    }

    @Test
    void throwsInsufficientStockException_whenNotEnoughQuantity() {
        UUID productId = UUID.randomUUID();
        Stock stock = new Stock(productId, BigDecimal.TEN, 1, 0);
        when(stockRepository.findByProductId(productId)).thenReturn(Optional.of(stock));

        OrderCreated event = new OrderCreated(
                UUID.randomUUID(), "customer-1", BigDecimal.valueOf(300),
                List.of(new OrderCreated.OrderItemPayload(productId, 3)),
                Instant.now()
        );

        assertThrows(InsufficientStockException.class, () -> inventoryService.reserveForOrder(event));
        verify(reservationRepository, never()).save(any());
    }

    @Test
    void throwsStockNotFoundException_whenStockMissing() {
        UUID productId = UUID.randomUUID();
        when(stockRepository.findByProductId(productId)).thenReturn(Optional.empty());

        OrderCreated event = new OrderCreated(
                UUID.randomUUID(), "customer-1", BigDecimal.valueOf(300),
                List.of(new OrderCreated.OrderItemPayload(productId, 1)),
                Instant.now()
        );

        assertThrows(StockNotFoundException.class, () -> inventoryService.reserveForOrder(event));
        verify(reservationRepository, never()).save(any());
    }

    @Test
    void stopsAtFirstFailingItem_whenLaterItemHasNotEnoughStock() {
        UUID productA = UUID.randomUUID();
        UUID productB = UUID.randomUUID();
        Stock stockA = new Stock(productA, BigDecimal.TEN, 5, 0);
        Stock stockB = new Stock(productB, BigDecimal.TEN, 1, 0);
        when(stockRepository.findByProductId(productA)).thenReturn(Optional.of(stockA));
        when(stockRepository.findByProductId(productB)).thenReturn(Optional.of(stockB));

        OrderCreated event = new OrderCreated(
                UUID.randomUUID(), "customer-1", BigDecimal.valueOf(300),
                List.of(
                        new OrderCreated.OrderItemPayload(productA, 2),
                        new OrderCreated.OrderItemPayload(productB, 5)
                ),
                Instant.now()
        );

        assertThrows(InsufficientStockException.class, () -> inventoryService.reserveForOrder(event));

        // productA успел примениться в памяти до того, как упала вторая позиция —
        // здесь это видно (мок не откатывает состояние), но реальный откат в БД
        // обеспечивает @Transactional на InventoryServiceImpl, а не эта проверка.
        // Он покрыт отдельно в InventoryReservationIntegrationTest.
        assertEquals(3, stockA.getAvailableQuantity());
        verify(reservationRepository, times(1)).save(any(Reservation.class));
    }
}
