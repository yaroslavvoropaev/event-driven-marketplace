package ru.voropaev.event_driven_marketplace.common.messaging;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MessageTypesTest {

    static class First {
        record Sample(UUID orderId, Instant occurredAt) implements Message { }
    }

    static class Second {
        record Sample(UUID orderId, Instant occurredAt) implements Message { }
    }

    record Other(UUID orderId, Instant occurredAt) implements Message { }

    private final MessageTypes messageTypes = new MessageTypes(List.of(First.Sample.class, Other.class));

    @Test
    void mapsRegisteredTypeToSimpleNameAndBack() {
        First.Sample message = new First.Sample(UUID.randomUUID(), Instant.now());

        assertEquals("Sample", messageTypes.nameOf(message));
        assertEquals(First.Sample.class, messageTypes.classOf("Sample"));
    }

    @Test
    void rejectsUnknownName() {
        assertThrows(IllegalArgumentException.class, () -> messageTypes.classOf("Unknown"));
    }

    @Test
    void rejectsUnregisteredClass_evenWithRegisteredSimpleName() {
        Second.Sample impostor = new Second.Sample(UUID.randomUUID(), Instant.now());

        assertThrows(IllegalArgumentException.class, () -> messageTypes.nameOf(impostor));
    }

    @Test
    void failsAtStartup_whenTwoTypesShareSimpleName() {
        assertThrows(IllegalStateException.class,
                () -> new MessageTypes(List.of(First.Sample.class, Second.Sample.class)));
    }
}
