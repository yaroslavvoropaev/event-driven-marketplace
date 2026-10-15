package ru.voropaev.event_driven_marketplace.inventory.command;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import ru.voropaev.event_driven_marketplace.common.retry.OptimisticLockRetrier;
import ru.voropaev.event_driven_marketplace.inventory.domain.Stock;
import ru.voropaev.event_driven_marketplace.inventory.domain.exception.InsufficientStockException;
import ru.voropaev.event_driven_marketplace.inventory.event.InventoryReservationFailed;
import ru.voropaev.event_driven_marketplace.inventory.event.InventoryReserved;
import ru.voropaev.event_driven_marketplace.inventory.service.InventoryService;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class InventoryCommandHandlerTest {
    private static final int MAX_ATTEMPTS = 3;
    private static final UUID ORDER_ID = UUID.randomUUID();

    @Mock
    private InventoryService inventoryService;
    @Mock
    private ApplicationEventPublisher applicationEventPublisher;

    private InventoryCommandHandler handler;

    private final ConfirmReservation confirm = new ConfirmReservation(ORDER_ID, Instant.now());
    private final ReleaseInventory release = new ReleaseInventory(ORDER_ID, Instant.now());

    @BeforeEach
    void setUp() {
        handler = new InventoryCommandHandler(
                applicationEventPublisher,
                inventoryService,
                new OptimisticLockRetrier(MAX_ATTEMPTS, 0)
        );
    }

    private ObjectOptimisticLockingFailureException versionConflict() {
        return new ObjectOptimisticLockingFailureException(Stock.class, UUID.randomUUID());
    }

    private ReserveInventory reserve(int quantity) {
        return new ReserveInventory(
                UUID.randomUUID(),
                List.of(new ReserveInventory.Item(UUID.randomUUID(), quantity)),
                Instant.now()
        );
    }

    @Test
    void publishesInventoryReserved_whenReservationSucceeds() {
        ReserveInventory command = reserve(1);

        handler.on(command);

        ArgumentCaptor<InventoryReserved> captor = ArgumentCaptor.forClass(InventoryReserved.class);
        verify(applicationEventPublisher).publishEvent(captor.capture());
        assertEquals(command.orderId(), captor.getValue().orderId());
    }

    @Test
    void publishesInventoryReservationFailed_whenReservationThrows() {
        ReserveInventory command = reserve(5);
        doThrow(new InsufficientStockException(5, 1))
                .when(inventoryService).reserveForOrder(command);

        handler.on(command);

        ArgumentCaptor<InventoryReservationFailed> captor = ArgumentCaptor.forClass(InventoryReservationFailed.class);
        verify(applicationEventPublisher).publishEvent(captor.capture());
        assertEquals(command.orderId(), captor.getValue().orderId());
        assertEquals("Cannot reserve 5, only 1 are available", captor.getValue().reason());
        verify(inventoryService, times(1)).reserveForOrder(command);
    }

    @Test
    @Timeout(5)
    void retriesAfterVersionConflict_andPublishesReserved() {
        ReserveInventory command = reserve(1);
        doThrow(versionConflict())
                .doNothing()
                .when(inventoryService).reserveForOrder(command);

        handler.on(command);

        verify(inventoryService, times(2)).reserveForOrder(command);
        verify(applicationEventPublisher).publishEvent(any(InventoryReserved.class));
        verify(applicationEventPublisher, never()).publishEvent(any(InventoryReservationFailed.class));
    }

    @Test
    @Timeout(5)
    void publishesReservationFailed_afterMaxAttempts() {
        ReserveInventory command = reserve(1);
        doThrow(versionConflict()).when(inventoryService).reserveForOrder(command);

        handler.on(command);

        verify(inventoryService, times(MAX_ATTEMPTS)).reserveForOrder(command);
        ArgumentCaptor<InventoryReservationFailed> captor = ArgumentCaptor.forClass(InventoryReservationFailed.class);
        verify(applicationEventPublisher).publishEvent(captor.capture());
        assertEquals(command.orderId(), captor.getValue().orderId());
        verify(applicationEventPublisher, never()).publishEvent(any(InventoryReserved.class));
    }

    @Test
    @Timeout(5)
    void doesNotReportReservationFailure_whenDownstreamSagaFails() {
        ReserveInventory command = reserve(1);
        doThrow(versionConflict())
                .when(applicationEventPublisher).publishEvent(any(InventoryReserved.class));

        assertThrows(ObjectOptimisticLockingFailureException.class, () -> handler.on(command));

        verify(inventoryService, times(1)).reserveForOrder(command);
        verify(applicationEventPublisher, never()).publishEvent(any(InventoryReservationFailed.class));
    }

    @Test
    void confirmsReservationsOfTheOrder() {
        handler.on(confirm);

        verify(inventoryService).confirmReservations(ORDER_ID);
        verifyNoInteractions(applicationEventPublisher);
    }

    @Test
    @Timeout(5)
    void givesUpConfirm_afterMaxAttempts_withoutPropagating() {
        doThrow(versionConflict()).when(inventoryService).confirmReservations(ORDER_ID);

        handler.on(confirm);

        verify(inventoryService, times(MAX_ATTEMPTS)).confirmReservations(ORDER_ID);
    }

    @Test
    void releasesReservationsOfTheOrder() {
        handler.on(release);

        verify(inventoryService).releaseReservations(ORDER_ID);
        verifyNoInteractions(applicationEventPublisher);
    }

    @Test
    @Timeout(5)
    void givesUpRelease_afterMaxAttempts_withoutPropagating() {
        doThrow(versionConflict()).when(inventoryService).releaseReservations(ORDER_ID);

        handler.on(release);

        verify(inventoryService, times(MAX_ATTEMPTS)).releaseReservations(ORDER_ID);
    }
}
