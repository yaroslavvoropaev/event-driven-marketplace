package ru.voropaev.event_driven_marketplace;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import ru.voropaev.event_driven_marketplace.order.domain.Order;
import ru.voropaev.event_driven_marketplace.order.domain.state.OrderStatus;
import ru.voropaev.event_driven_marketplace.order.repository.OrderRepository;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class OrderOptimisticLockingIntegrationTest {
    @Autowired
    private OrderRepository orderRepository;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void secondWriterFailsAtCommit_andFirstWriteSurvives() {
        UUID orderId = orderRepository.save(new Order(UUID.randomUUID())).getId();

        TransactionTemplate slow = new TransactionTemplate(transactionManager);
        TransactionTemplate fast = new TransactionTemplate(transactionManager);
        fast.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        assertThrows(ObjectOptimisticLockingFailureException.class, () -> slow.executeWithoutResult(status -> {
            Order seenBySlow = orderRepository.findById(orderId).orElseThrow();

            fast.executeWithoutResult(inner ->
                    orderRepository.findById(orderId).orElseThrow().updateStatus(OrderStatus.CANCELLED));

            seenBySlow.updateStatus(OrderStatus.PENDING);
        }));

        Order stored = orderRepository.findById(orderId).orElseThrow();
        assertEquals(OrderStatus.CANCELLED, stored.getOrderStatus());
        assertEquals(1L, stored.getVersion());
    }
}
