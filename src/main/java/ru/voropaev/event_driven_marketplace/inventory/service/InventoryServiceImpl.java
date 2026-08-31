package ru.voropaev.event_driven_marketplace.inventory.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import ru.voropaev.event_driven_marketplace.inventory.domain.Reservation;
import ru.voropaev.event_driven_marketplace.inventory.domain.Stock;
import ru.voropaev.event_driven_marketplace.inventory.domain.exception.StockNotFoundException;
import ru.voropaev.event_driven_marketplace.inventory.repository.ReservationRepository;
import ru.voropaev.event_driven_marketplace.inventory.repository.StockRepository;
import ru.voropaev.event_driven_marketplace.order.event.OrderCreated;

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
}
