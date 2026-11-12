package com.ticket.system.security;

import com.ticket.system.entity.Role;
import com.ticket.system.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class JwtServiceTest {

    private JwtService jwtService;
    private final String secret = "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970";
    private final long expirationMs = 3600000; // 1 hour

    @BeforeEach
    void setUp() {
        jwtService = new JwtService(secret, expirationMs);
    }

    @Test
    @DisplayName("Should generate token with valid claims and subject")
    void shouldGenerateAndExtractClaims() {
        User user = User.builder()
                .id(42L)
                .name("John Doe")
                .email("john@example.com")
                .role(Role.ADMIN)
                .build();

        String token = jwtService.generateToken(user);

        assertThat(token).isNotBlank();
        assertThat(jwtService.validateToken(token)).isTrue();
        assertThat(jwtService.extractEmail(token)).isEqualTo("john@example.com");
        assertThat(jwtService.extractUserId(token)).isEqualTo(42L);
        assertThat(jwtService.extractRole(token)).isEqualTo(Role.ADMIN);
        assertThat(jwtService.extractExpiration(token)).isAfter(Instant.now());
    }

    @Test
    @DisplayName("Should reject tampered token")
    void shouldRejectTamperedToken() {
        User user = User.builder()
                .id(1L)
                .name("Bob")
                .email("bob@example.com")
                .role(Role.USER)
                .build();

        String token = jwtService.generateToken(user);
        String tamperedToken = token.substring(0, token.length() - 4) + "abcd";

        assertThat(jwtService.validateToken(tamperedToken)).isFalse();
    }

    @Test
    @DisplayName("Should reject expired token")
    void shouldRejectExpiredToken() {
        // Construct service with negative expiration
        JwtService expiredService = new JwtService(secret, -1000);
        User user = User.builder()
                .id(1L)
                .name("Alice")
                .email("alice@example.com")
                .role(Role.USER)
                .build();

        String token = expiredService.generateToken(user);

        assertThat(jwtService.validateToken(token)).isFalse();
    }
}

