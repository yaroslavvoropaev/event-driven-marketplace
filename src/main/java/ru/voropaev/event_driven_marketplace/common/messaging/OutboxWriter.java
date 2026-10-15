package ru.voropaev.event_driven_marketplace.common.messaging;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;

@Component
public class OutboxWriter {
    private final OutboxRepository outboxRepository;
    private final MessageTypes messageTypes;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public OutboxWriter(OutboxRepository outboxRepository, MessageTypes messageTypes,
                        ObjectMapper objectMapper, Clock clock) {
        this.outboxRepository = outboxRepository;
        this.messageTypes = messageTypes;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void save(Message message) {
        outboxRepository.save(new OutboxMessage(
                messageTypes.nameOf(message),
                message.orderId(),
                objectMapper.writeValueAsString(message),
                clock.instant()
        ));
    }
}
