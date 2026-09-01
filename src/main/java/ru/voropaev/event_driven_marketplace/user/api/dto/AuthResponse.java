package ru.voropaev.event_driven_marketplace.user.api.dto;

public record AuthResponse(
        String token,
        UserResponse user
) { }
