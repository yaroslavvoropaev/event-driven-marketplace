package ru.voropaev.event_driven_marketplace.user.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import ru.voropaev.event_driven_marketplace.user.api.dto.LoginRequest;
import ru.voropaev.event_driven_marketplace.user.domain.User;
import ru.voropaev.event_driven_marketplace.user.repository.UserRepository;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    private static final Instant NOW = Instant.parse("2026-01-01T12:00:00Z");
    private static final long TOKEN_TTL_SECONDS = 3600;

    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JwtEncoder jwtEncoder;

    private AuthServiceImpl authService;

    @BeforeEach
    void setUp() {
        authService = new AuthServiceImpl(
                userRepository, passwordEncoder, jwtEncoder, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    /**
     * Часы, сдвигающиеся на секунду при каждом обращении. Нужны, чтобы отличить
     * "время прочитано один раз" от "прочитано дважды": на фиксированных часах
     * оба варианта неразличимы.
     */
    private static Clock advancingClock(Instant start) {
        return new Clock() {
            private Instant current = start;

            @Override
            public ZoneId getZone() {
                return ZoneOffset.UTC;
            }

            @Override
            public Clock withZone(ZoneId zone) {
                return this;
            }

            @Override
            public Instant instant() {
                Instant result = current;
                current = current.plusSeconds(1);
                return result;
            }
        };
    }

    @Test
    void issuesTokenWhoseExpiryIsMeasuredFromItsIssuedAt() {
        User user = new User("test@example.com", "hash");
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("password123", "hash")).thenReturn(true);
        when(jwtEncoder.encode(any())).thenReturn(Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .claim("sub", user.getId().toString())
                .build());

        authService.login(new LoginRequest("test@example.com", "password123"));

        ArgumentCaptor<JwtEncoderParameters> captor = ArgumentCaptor.forClass(JwtEncoderParameters.class);
        verify(jwtEncoder).encode(captor.capture());

        JwtClaimsSet claims = captor.getValue().getClaims();
        assertEquals(user.getId().toString(), claims.getSubject());
        assertEquals(NOW, claims.getIssuedAt());
        assertEquals(NOW.plusSeconds(TOKEN_TTL_SECONDS), claims.getExpiresAt());
    }

    @Test
    void readsCurrentTimeOnlyOnceWhenBuildingClaims() {
        authService = new AuthServiceImpl(
                userRepository, passwordEncoder, jwtEncoder, advancingClock(NOW));

        User user = new User("test@example.com", "hash");
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("password123", "hash")).thenReturn(true);
        when(jwtEncoder.encode(any())).thenReturn(Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .claim("sub", user.getId().toString())
                .build());

        authService.login(new LoginRequest("test@example.com", "password123"));

        ArgumentCaptor<JwtEncoderParameters> captor = ArgumentCaptor.forClass(JwtEncoderParameters.class);
        verify(jwtEncoder).encode(captor.capture());

        JwtClaimsSet claims = captor.getValue().getClaims();
        assertEquals(
                Duration.ofSeconds(TOKEN_TTL_SECONDS),
                Duration.between(claims.getIssuedAt(), claims.getExpiresAt()));
    }
}
