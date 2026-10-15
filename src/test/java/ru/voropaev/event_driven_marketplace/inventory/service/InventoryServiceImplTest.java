package ru.voropaev.event_driven_marketplace.inventory.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.voropaev.event_driven_marketplace.inventory.command.ReserveInventory;
import ru.voropaev.event_driven_marketplace.inventory.domain.Reservation;
import ru.voropaev.event_driven_marketplace.inventory.domain.ReservationStatus;
import ru.voropaev.event_driven_marketplace.inventory.domain.Stock;
import ru.voropaev.event_driven_marketplace.inventory.domain.exception.InsufficientStockException;
import ru.voropaev.event_driven_marketplace.inventory.domain.exception.StockNotFoundException;
import ru.voropaev.event_driven_marketplace.inventory.repository.ReservationRepository;
import ru.voropaev.event_driven_marketplace.inventory.repository.StockRepository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
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

        ReserveInventory command = new ReserveInventory(
                orderId,
                List.of(new ReserveInventory.Item(productId, 3)),
                Instant.now()
        );

        inventoryService.reserveForOrder(command);

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

        ReserveInventory command = new ReserveInventory(
                UUID.randomUUID(),
                List.of(new ReserveInventory.Item(productId, 3)),
                Instant.now()
        );

        assertThrows(InsufficientStockException.class, () -> inventoryService.reserveForOrder(command));
        verify(reservationRepository, never()).save(any());
    }

    @Test
    void throwsStockNotFoundException_whenStockMissing() {
        UUID productId = UUID.randomUUID();
        when(stockRepository.findByProductId(productId)).thenReturn(Optional.empty());

        ReserveInventory command = new ReserveInventory(
                UUID.randomUUID(),
                List.of(new ReserveInventory.Item(productId, 1)),
                Instant.now()
        );

        assertThrows(StockNotFoundException.class, () -> inventoryService.reserveForOrder(command));
        verify(reservationRepository, never()).save(any());
    }

    @Test
    void confirmReservations_confirmsReservationAndWritesOffReservedStock() {
        UUID orderId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();

        Stock stock = new Stock(productId, BigDecimal.TEN, 10, 0);
        stock.reserve(3);
        Reservation reservation = Reservation.reserved(orderId, productId, 3);

        when(reservationRepository.findByOrderId(orderId)).thenReturn(List.of(reservation));
        when(stockRepository.findByProductId(productId)).thenReturn(Optional.of(stock));

        inventoryService.confirmReservations(orderId);

        assertEquals(ReservationStatus.CONFIRMED, reservation.getReservationStatus());

        assertEquals(0, stock.getReservedQuantity());
        assertEquals(7, stock.getAvailableQuantity());
    }

    @Test
    void confirmReservations_skipsReservationsThatAreNotReserved() {
        UUID orderId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();

        Reservation alreadyConfirmed = Reservation.reserved(orderId, productId, 3);
        alreadyConfirmed.confirm();

        when(reservationRepository.findByOrderId(orderId)).thenReturn(List.of(alreadyConfirmed));

        inventoryService.confirmReservations(orderId);

        assertEquals(ReservationStatus.CONFIRMED, alreadyConfirmed.getReservationStatus());
        verifyNoInteractions(stockRepository);
    }

    @Test
    void releaseReservations_returnsStockToAvailableAndReleasesReservation() {
        UUID orderId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();

        Stock stock = new Stock(productId, BigDecimal.TEN, 10, 0);
        stock.reserve(3);
        Reservation reservation = Reservation.reserved(orderId, productId, 3);

        when(reservationRepository.findByOrderId(orderId)).thenReturn(List.of(reservation));
        when(stockRepository.findByProductId(productId)).thenReturn(Optional.of(stock));

        inventoryService.releaseReservations(orderId);

        assertEquals(ReservationStatus.RELEASED, reservation.getReservationStatus());

        assertEquals(0, stock.getReservedQuantity());
        assertEquals(10, stock.getAvailableQuantity());
    }

    @Test
    void releaseReservations_skipsAlreadyConfirmedReservations() {
        UUID orderId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();

        Reservation alreadyConfirmed = Reservation.reserved(orderId, productId, 3);
        alreadyConfirmed.confirm();

        when(reservationRepository.findByOrderId(orderId)).thenReturn(List.of(alreadyConfirmed));

        inventoryService.releaseReservations(orderId);

        assertEquals(ReservationStatus.CONFIRMED, alreadyConfirmed.getReservationStatus());
        verifyNoInteractions(stockRepository);
    }

    @Test
    void releaseReservations_isIdempotent_whenReservationAlreadyReleased() {
        UUID orderId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();

        Reservation alreadyReleased = Reservation.reserved(orderId, productId, 3);
        alreadyReleased.release();

        when(reservationRepository.findByOrderId(orderId)).thenReturn(List.of(alreadyReleased));

        inventoryService.releaseReservations(orderId);

        assertEquals(ReservationStatus.RELEASED, alreadyReleased.getReservationStatus());
        verifyNoInteractions(stockRepository);
    }
}
