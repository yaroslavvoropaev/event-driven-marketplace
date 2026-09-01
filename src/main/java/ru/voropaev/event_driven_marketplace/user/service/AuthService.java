package ru.voropaev.event_driven_marketplace.user.service;

import ru.voropaev.event_driven_marketplace.user.api.dto.AuthResponse;
import ru.voropaev.event_driven_marketplace.user.api.dto.LoginRequest;
import ru.voropaev.event_driven_marketplace.user.api.dto.RegisterRequest;
import ru.voropaev.event_driven_marketplace.user.api.dto.UserResponse;

public interface AuthService {
    UserResponse register(RegisterRequest request);
    AuthResponse login(LoginRequest request);
}
