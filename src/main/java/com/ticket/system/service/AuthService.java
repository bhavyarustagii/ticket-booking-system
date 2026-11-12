package com.ticket.system.service;

import com.ticket.system.dto.request.LoginRequest;
import com.ticket.system.dto.request.RegisterRequest;
import com.ticket.system.dto.response.AuthResponse;
import com.ticket.system.entity.Role;
import com.ticket.system.entity.User;
import com.ticket.system.exception.ConflictException;
import com.ticket.system.repository.UserRepository;
import com.ticket.system.security.JwtService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String normalizedEmail = request.getEmail().trim().toLowerCase();

        // Enforce account uniqueness by rejecting duplicate email registrations upfront
        if (userRepository.findByEmail(normalizedEmail).isPresent()) {
            throw new ConflictException("An account with email '" + normalizedEmail + "' already exists");
        }

        // Public registration is strictly locked to USER role to prevent unauthorized privilege escalation
        Role assignedRole = Role.USER;

        // BCrypt hashing ensures plaintext passwords are never persisted to disk or logs
        User user = User.builder()
                .name(request.getName().trim())
                .email(normalizedEmail)
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .role(assignedRole)
                .build();

        User savedUser = userRepository.save(user);
        log.info("Registered new user id={} email={} with role={}", savedUser.getId(), savedUser.getEmail(), savedUser.getRole());

        String token = jwtService.generateToken(savedUser);
        return AuthResponse.builder()
                .token(token)
                .tokenType("Bearer")
                .userId(savedUser.getId())
                .email(savedUser.getEmail())
                .name(savedUser.getName())
                .role(savedUser.getRole())
                .expiresAt(jwtService.extractExpiration(token))
                .build();
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        String normalizedEmail = request.getEmail().trim().toLowerCase();

        // Constant-time check comparison helps mitigate user enumeration via timing attacks
        User user = userRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> new BadCredentialsException("Invalid email or password"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new BadCredentialsException("Invalid email or password");
        }

        String token = jwtService.generateToken(user);
        return AuthResponse.builder()
                .token(token)
                .tokenType("Bearer")
                .userId(user.getId())
                .email(user.getEmail())
                .name(user.getName())
                .role(user.getRole())
                .expiresAt(jwtService.extractExpiration(token))
                .build();
    }
}

