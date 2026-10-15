package ru.voropaev.event_driven_marketplace.common.messaging;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class MessageTypes {
    private final Map<String, Class<? extends Message>> byName = new HashMap<>();

    public MessageTypes(Collection<Class<? extends Message>> types) {
        for (Class<? extends Message> type : types) {
            Class<? extends Message> previous = byName.putIfAbsent(type.getSimpleName(), type);
            if (previous != null) {
                throw new IllegalStateException("Duplicate message type name " + type.getSimpleName()
                        + ": " + previous.getName() + " and " + type.getName());
            }
        }
    }

    public String nameOf(Message message) {
        String name = message.getClass().getSimpleName();
        if (byName.get(name) != message.getClass()) {
            throw new IllegalArgumentException("Unregistered message type " + message.getClass().getName());
        }
        return name;
    }

    public Class<? extends Message> classOf(String name) {
        Class<? extends Message> type = byName.get(name);
        if (type == null) {
            throw new IllegalArgumentException("Unknown message type " + name);
        }
        return type;
    }

}
