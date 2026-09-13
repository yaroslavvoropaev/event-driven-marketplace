package ru.voropaev.event_driven_marketplace.user.api;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.voropaev.event_driven_marketplace.user.api.dto.AuthResponse;
import ru.voropaev.event_driven_marketplace.user.api.dto.LoginRequest;
import ru.voropaev.event_driven_marketplace.user.api.dto.RegisterRequest;
import ru.voropaev.event_driven_marketplace.user.api.dto.UserResponse;
import ru.voropaev.event_driven_marketplace.user.service.AuthService;

@RestController
@RequestMapping("api/auth")
public class AuthController {
    private final AuthService authService;

    public  AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("register")
    ResponseEntity<UserResponse> register(@RequestBody @Valid RegisterRequest request) {
        UserResponse userResponse = authService.register(request);

        return ResponseEntity.ok().body(userResponse);
    }

    @PostMapping("login")
    ResponseEntity<AuthResponse> login(@RequestBody @Valid LoginRequest request) {
        AuthResponse userResponse = authService.login(request);

        return ResponseEntity.ok().body(userResponse);
    }

}
