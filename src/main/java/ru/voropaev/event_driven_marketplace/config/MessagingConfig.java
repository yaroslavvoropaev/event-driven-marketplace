package ru.voropaev.event_driven_marketplace.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import ru.voropaev.event_driven_marketplace.common.messaging.MessageTypes;
import ru.voropaev.event_driven_marketplace.inventory.command.ConfirmReservation;
import ru.voropaev.event_driven_marketplace.inventory.command.ReleaseInventory;
import ru.voropaev.event_driven_marketplace.inventory.command.ReserveInventory;
import ru.voropaev.event_driven_marketplace.inventory.event.InventoryReservationFailed;
import ru.voropaev.event_driven_marketplace.inventory.event.InventoryReserved;
import ru.voropaev.event_driven_marketplace.order.event.OrderCancelled;
import ru.voropaev.event_driven_marketplace.order.event.OrderConfirmed;
import ru.voropaev.event_driven_marketplace.order.event.OrderCreated;
import ru.voropaev.event_driven_marketplace.payment.command.ChargePayment;
import ru.voropaev.event_driven_marketplace.payment.event.PaymentCompleted;
import ru.voropaev.event_driven_marketplace.payment.event.PaymentFailed;

import java.util.List;

@Configuration
public class MessagingConfig {

    @Bean
    public MessageTypes messageTypes() {
        return new MessageTypes(List.of(
                OrderCreated.class, OrderConfirmed.class, OrderCancelled.class,
                ReserveInventory.class, ConfirmReservation.class, ReleaseInventory.class,
                InventoryReserved.class, InventoryReservationFailed.class,
                ChargePayment.class, PaymentCompleted.class, PaymentFailed.class));
    }
}