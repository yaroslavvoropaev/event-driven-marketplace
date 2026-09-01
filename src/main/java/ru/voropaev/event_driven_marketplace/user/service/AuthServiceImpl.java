package ru.voropaev.event_driven_marketplace.user.service;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import ru.voropaev.event_driven_marketplace.user.api.dto.AuthResponse;
import ru.voropaev.event_driven_marketplace.user.api.dto.LoginRequest;
import ru.voropaev.event_driven_marketplace.user.api.dto.RegisterRequest;
import ru.voropaev.event_driven_marketplace.user.api.dto.UserResponse;
import ru.voropaev.event_driven_marketplace.user.domain.exception.EmailAlreadyExistsException;
import ru.voropaev.event_driven_marketplace.user.domain.User;
import ru.voropaev.event_driven_marketplace.user.domain.exception.InvalidCredentialsException;
import ru.voropaev.event_driven_marketplace.user.repository.UserRepository;


@Service
public class AuthServiceImpl  implements AuthService{
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    public AuthServiceImpl(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public UserResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new EmailAlreadyExistsException(request.email());
        }

        String hash = passwordEncoder.encode(request.password());
        User user = new User(request.email(), hash);
        userRepository.save(user);
        return toResponse(user);
    }

    @Override
    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(InvalidCredentialsException::new);

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }
        ;
    }

    private UserResponse toResponse(User user) {
        return new UserResponse(user.getId(), user.getCreatedAt());
    }
}
