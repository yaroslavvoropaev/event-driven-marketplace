package ru.voropaev.event_driven_marketplace.user.service;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;
import ru.voropaev.event_driven_marketplace.user.api.dto.AuthResponse;
import ru.voropaev.event_driven_marketplace.user.api.dto.LoginRequest;
import ru.voropaev.event_driven_marketplace.user.api.dto.RegisterRequest;
import ru.voropaev.event_driven_marketplace.user.api.dto.UserResponse;
import ru.voropaev.event_driven_marketplace.user.domain.exception.EmailAlreadyExistsException;
import ru.voropaev.event_driven_marketplace.user.domain.User;
import ru.voropaev.event_driven_marketplace.user.domain.exception.InvalidCredentialsException;
import ru.voropaev.event_driven_marketplace.user.repository.UserRepository;

import java.time.Instant;


@Service
public class AuthServiceImpl  implements AuthService{
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtEncoder jwtEncoder;
    private static final long TOKEN_TTL_SECONDS = 3600;

    public AuthServiceImpl(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtEncoder jwtEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtEncoder = jwtEncoder;
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

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(user.getId().toString())
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(TOKEN_TTL_SECONDS))
                .build();

        String token = jwtEncoder.encode(JwtEncoderParameters.from(claims)).getTokenValue();
        return new AuthResponse(token, toResponse(user));
    }

    private UserResponse toResponse(User user) {
        return new UserResponse(user.getId(), user.getCreatedAt());
    }
}
