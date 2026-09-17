package ru.voropaev.event_driven_marketplace.inventory.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import ru.voropaev.event_driven_marketplace.inventory.domain.Reservation;
import ru.voropaev.event_driven_marketplace.inventory.domain.ReservationStatus;
import ru.voropaev.event_driven_marketplace.inventory.domain.Stock;
import ru.voropaev.event_driven_marketplace.inventory.domain.exception.StockNotFoundException;
import ru.voropaev.event_driven_marketplace.inventory.repository.ReservationRepository;
import ru.voropaev.event_driven_marketplace.inventory.repository.StockRepository;
import ru.voropaev.event_driven_marketplace.order.event.OrderCreated;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
public class InventoryServiceImpl implements InventoryService {
    private final StockRepository stockRepository;
    private final ReservationRepository reservationRepository;

    public InventoryServiceImpl(StockRepository stockRepository, ReservationRepository reservationRepository) {
        this.stockRepository = stockRepository;
        this.reservationRepository = reservationRepository;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void reserveForOrder(OrderCreated event) {
        for (OrderCreated.OrderItemPayload item : event.items()) {
            Stock stock = stockRepository.findByProductId(item.productId())
                    .orElseThrow(() -> new StockNotFoundException(item.productId()));

            stock.reserve(item.quantity());
            Reservation reservation = Reservation.reserved(event.orderId(), item.productId(), item.quantity());
            reservationRepository.save(reservation);
        }
    }


    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void confirmReservations(UUID orderId) {
        List<Reservation> reservations = reservationRepository.findByOrderId(orderId)
                .stream()
                .filter(reservation -> reservation.getReservationStatus().equals(ReservationStatus.RESERVED))
                .toList();

        for (var reservation : reservations) {
            Stock stock = stockRepository.findByProductId(reservation.getProductId())
                    .orElseThrow(() -> new StockNotFoundException(reservation.getProductId()));
            stock.confirm(reservation.getQuantity());
            reservation.confirm();
        }
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void releaseReservations(UUID orderId) {
        List<Reservation> reservations = reservationRepository.findByOrderId(orderId)
                .stream()
                .filter(reservation -> reservation.getReservationStatus().equals(ReservationStatus.RESERVED))
                .toList();


        for (var reservation : reservations) {
            Stock stock = stockRepository.findByProductId(reservation.getProductId())
                    .orElseThrow(() -> new StockNotFoundException(reservation.getProductId()));

            stock.release(reservation.getQuantity());
            reservation.release();
        }
    }

    @Override
    public BigDecimal getPrice(UUID productId) {
        return stockRepository.findByProductId(productId)
                .orElseThrow(() -> new StockNotFoundException(productId)).getPrice();
    }
}
