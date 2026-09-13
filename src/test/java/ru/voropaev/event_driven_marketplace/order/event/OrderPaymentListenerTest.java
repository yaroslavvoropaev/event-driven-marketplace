package ru.voropaev.event_driven_marketplace.order.event;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.voropaev.event_driven_marketplace.order.service.OrderService;
import ru.voropaev.event_driven_marketplace.payment.event.PaymentCompleted;
import ru.voropaev.event_driven_marketplace.payment.event.PaymentFailed;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class OrderPaymentListenerTest {

    private static final UUID ORDER_ID = UUID.randomUUID();

    @Mock
    private OrderService orderService;

    @InjectMocks
    private OrderPaymentListener listener;

    @Test
    void confirmsOrderWhenPaymentCompleted() {
        PaymentCompleted event = new PaymentCompleted(
                ORDER_ID,
                UUID.randomUUID(),
                UUID.randomUUID(),
                new BigDecimal("199.00"),
                "fake-tx-1",
                Instant.now()
        );

        listener.on(event);

        verify(orderService).confirmOrder(ORDER_ID);
    }

    @Test
    void cancelsOrderWhenPaymentFailed() {
        PaymentFailed event = new PaymentFailed(
                ORDER_ID,
                UUID.randomUUID(),
                UUID.randomUUID(),
                new BigDecimal("199.13"),
                "insufficient funds",
                Instant.now()
        );

        listener.on(event);

        verify(orderService).cancelOrderDueToPaymentFailure(ORDER_ID);
    }

}
