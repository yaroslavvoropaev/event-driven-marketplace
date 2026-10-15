package ru.voropaev.event_driven_marketplace.common.messaging;

import java.time.Instant;
import java.util.UUID;

public interface Message {
    UUID orderId();
    Instant occurredAt();
}
