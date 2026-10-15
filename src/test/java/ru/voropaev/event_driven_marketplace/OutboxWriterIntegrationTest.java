package ru.voropaev.event_driven_marketplace;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import ru.voropaev.event_driven_marketplace.common.messaging.Message;
import ru.voropaev.event_driven_marketplace.common.messaging.MessageTypes;
import ru.voropaev.event_driven_marketplace.common.messaging.OutboxMessage;
import ru.voropaev.event_driven_marketplace.common.messaging.OutboxRepository;
import ru.voropaev.event_driven_marketplace.common.messaging.OutboxWriter;
import ru.voropaev.event_driven_marketplace.inventory.command.ConfirmReservation;
import ru.voropaev.event_driven_marketplace.inventory.command.ReleaseInventory;
import ru.voropaev.event_driven_marketplace.inventory.command.ReserveInventory;
import ru.voropaev.event_driven_marketplace.inventory.event.InventoryReservationFailed;
import ru.voropaev.event_driven_marketplace.inventory.event.InventoryReserved;
import ru.voropaev.event_driven_marketplace.order.event.CancellationReason;
import ru.voropaev.event_driven_marketplace.order.event.OrderCancelled;
import ru.voropaev.event_driven_marketplace.order.event.OrderConfirmed;
import ru.voropaev.event_driven_marketplace.order.event.OrderCreated;
import ru.voropaev.event_driven_marketplace.payment.command.ChargePayment;
import ru.voropaev.event_driven_marketplace.payment.event.PaymentCompleted;
import ru.voropaev.event_driven_marketplace.payment.event.PaymentFailed;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class OutboxWriterIntegrationTest {

    private static final Instant AT = Instant.parse("2026-10-15T10:00:01.123456Z");
    private static final BigDecimal AMOUNT = new BigDecimal("100.00");

    record Unregistered(UUID orderId, Instant occurredAt) implements Message { }

    @Autowired
    private OutboxWriter outboxWriter;
    @Autowired
    private OutboxRepository outboxRepository;
    @Autowired
    private MessageTypes messageTypes;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private PlatformTransactionManager transactionManager;

    static Stream<Message> everyMessageType() {
        return Stream.of(
                new OrderCreated(UUID.randomUUID(), UUID.randomUUID(), AMOUNT,
                        List.of(new OrderCreated.OrderItemPayload(UUID.randomUUID(), 2)), AT),
                new OrderConfirmed(UUID.randomUUID(), UUID.randomUUID(), AMOUNT, AT),
                new OrderCancelled(UUID.randomUUID(), UUID.randomUUID(), AMOUNT,
                        CancellationReason.PAYMENT_FAILED, AT),
                new ReserveInventory(UUID.randomUUID(),
                        List.of(new ReserveInventory.Item(UUID.randomUUID(), 3)), AT),
                new ConfirmReservation(UUID.randomUUID(), AT),
                new ReleaseInventory(UUID.randomUUID(), AT),
                new InventoryReserved(UUID.randomUUID(), AT),
                new InventoryReservationFailed(UUID.randomUUID(), "not enough stock", AT),
                new ChargePayment(UUID.randomUUID(), UUID.randomUUID(), AMOUNT, AT),
                new PaymentCompleted(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), AMOUNT,
                        "fake-tx-1", AT),
                new PaymentFailed(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), AMOUNT,
                        "insufficient funds", AT)
        );
    }

    private List<OutboxMessage> rowsOf(UUID orderId) {
        return outboxRepository.findAll().stream()
                .filter(row -> row.getOrderId().equals(orderId))
                .toList();
    }

    private void inTransaction(Runnable action) {
        new TransactionTemplate(transactionManager).executeWithoutResult(_ -> action.run());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("everyMessageType")
    void storesUnsentRowWhosePayloadReadsBackEqual(Message message) {
        inTransaction(() -> outboxWriter.save(message));

        List<OutboxMessage> rows = rowsOf(message.orderId());
        assertEquals(1, rows.size());
        OutboxMessage row = rows.getFirst();
        assertNotNull(row.getId());
        assertEquals(message.getClass().getSimpleName(), row.getMessageType());
        assertNotNull(row.getCreatedAt());
        assertNull(row.getSentAt());
        assertEquals(0, row.getAttempts());
        assertNull(row.getLastError());

        Message restored = objectMapper.readValue(row.getPayload(), messageTypes.classOf(row.getMessageType()));
        assertEquals(message, restored);
    }

    @Test
    void leavesNoRow_whenSurroundingTransactionRollsBack() {
        InventoryReserved message = new InventoryReserved(UUID.randomUUID(), AT);

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            outboxWriter.save(message);
            status.setRollbackOnly();
        });

        assertTrue(rowsOf(message.orderId()).isEmpty());
    }

    @Test
    void refusesToWrite_withoutSurroundingTransaction() {
        InventoryReserved message = new InventoryReserved(UUID.randomUUID(), AT);

        assertThrows(IllegalTransactionStateException.class, () -> outboxWriter.save(message));

        assertTrue(rowsOf(message.orderId()).isEmpty());
    }

    @Test
    void refusesUnregisteredMessageType() {
        Unregistered message = new Unregistered(UUID.randomUUID(), AT);

        assertThrows(IllegalArgumentException.class, () -> inTransaction(() -> outboxWriter.save(message)));

        assertTrue(rowsOf(message.orderId()).isEmpty());
    }

    @Test
    void numbersRowsInInsertionOrder() {
        UUID orderId = UUID.randomUUID();

        inTransaction(() -> {
            outboxWriter.save(new ReserveInventory(orderId, List.of(), AT));
            outboxWriter.save(new InventoryReserved(orderId, AT));
        });

        List<OutboxMessage> rows = rowsOf(orderId);
        assertEquals(2, rows.size());
        OutboxMessage first = rows.stream().filter(r -> r.getMessageType().equals("ReserveInventory")).findFirst().orElseThrow();
        OutboxMessage second = rows.stream().filter(r -> r.getMessageType().equals("InventoryReserved")).findFirst().orElseThrow();
        assertTrue(first.getId() < second.getId());
    }
}
