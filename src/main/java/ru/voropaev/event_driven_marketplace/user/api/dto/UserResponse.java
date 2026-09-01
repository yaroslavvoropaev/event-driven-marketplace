package ru.voropaev.event_driven_marketplace.user.api.dto;

import java.time.Instant;
import java.util.UUID;

public record UserResponse(
        UUID id,
        Instant createdAt
) { }
