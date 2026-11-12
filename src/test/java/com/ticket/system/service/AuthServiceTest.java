package com.ticket.system.service;

import com.ticket.system.dto.request.LoginRequest;
import com.ticket.system.dto.request.RegisterRequest;
import com.ticket.system.dto.response.AuthResponse;
import com.ticket.system.entity.Role;
import com.ticket.system.entity.User;
import com.ticket.system.exception.ConflictException;
import com.ticket.system.repository.UserRepository;
import com.ticket.system.security.JwtService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    @InjectMocks
    private AuthService authService;

    @Test
    @DisplayName("register should hash password, persist user, and return JWT token")
    void register_success() {
        RegisterRequest request = RegisterRequest.builder()
                .name("Alice")
                .email("alice@example.com")
                .password("secret123")
                .role(Role.USER)
                .build();

        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("secret123")).thenReturn("$2a$10$hashed");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(10L);
            return u;
        });
        when(jwtService.generateToken(any(User.class))).thenReturn("jwt-token-123");
        when(jwtService.extractExpiration("jwt-token-123")).thenReturn(Instant.now().plusSeconds(3600));

        AuthResponse response = authService.register(request);

        assertThat(response).isNotNull();
        assertThat(response.getToken()).isEqualTo("jwt-token-123");
        assertThat(response.getEmail()).isEqualTo("alice@example.com");
        assertThat(response.getRole()).isEqualTo(Role.USER);

        verify(passwordEncoder).encode("secret123");
        verify(userRepository).save(any(User.class));
    }

    @Test
    @DisplayName("register with existing email should throw ConflictException")
    void register_duplicateEmail_throwsConflict() {
        RegisterRequest request = RegisterRequest.builder()
                .name("Alice")
                .email("alice@example.com")
                .password("secret123")
                .build();

        when(userRepository.findByEmail("alice@example.com"))
                .thenReturn(Optional.of(User.builder().id(1L).email("alice@example.com").build()));

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("alice@example.com");

        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("login with valid credentials should return JWT token")
    void login_success() {
        LoginRequest request = LoginRequest.builder()
                .email("alice@example.com")
                .password("secret123")
                .build();

        User user = User.builder()
                .id(1L)
                .name("Alice")
                .email("alice@example.com")
                .passwordHash("$2a$10$hashed")
                .role(Role.USER)
                .build();

        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("secret123", "$2a$10$hashed")).thenReturn(true);
        when(jwtService.generateToken(user)).thenReturn("jwt-token-456");
        when(jwtService.extractExpiration("jwt-token-456")).thenReturn(Instant.now().plusSeconds(3600));

        AuthResponse response = authService.login(request);

        assertThat(response).isNotNull();
        assertThat(response.getToken()).isEqualTo("jwt-token-456");
        assertThat(response.getEmail()).isEqualTo("alice@example.com");
    }

    @Test
    @DisplayName("login with invalid password should throw BadCredentialsException")
    void login_wrongPassword_throwsBadCredentials() {
        LoginRequest request = LoginRequest.builder()
                .email("alice@example.com")
                .password("wrongpassword")
                .build();

        User user = User.builder()
                .id(1L)
                .name("Alice")
                .email("alice@example.com")
                .passwordHash("$2a$10$hashed")
                .role(Role.USER)
                .build();

        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrongpassword", "$2a$10$hashed")).thenReturn(false);

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessageContaining("Invalid email or password");
    }

    @Test
    @DisplayName("login with non-existent email should throw BadCredentialsException")
    void login_unknownEmail_throwsBadCredentials() {
        LoginRequest request = LoginRequest.builder()
                .email("unknown@example.com")
                .password("secret123")
                .build();

        when(userRepository.findByEmail("unknown@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessageContaining("Invalid email or password");
    }
}

