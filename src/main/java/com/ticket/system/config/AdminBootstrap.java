package com.ticket.system.config;

import com.ticket.system.entity.Role;
import com.ticket.system.entity.User;
import com.ticket.system.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Bootstraps an initial administrator account on startup if no ADMIN user exists.
 * Guarantees administrative access without allowing arbitrary self-promotion via public endpoints.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AdminBootstrap implements ApplicationRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.admin.email:admin@ticket.local}")
    private String adminEmail;

    @Value("${app.admin.password:DevAdminPassword123!}")
    private String adminPassword;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        // Only seed when no ADMIN user is present in the database
        if (!userRepository.existsByRole(Role.ADMIN)) {
            String normalizedEmail = adminEmail.trim().toLowerCase();

            // Guard against existing user with the same email
            if (userRepository.findByEmail(normalizedEmail).isEmpty()) {
                User admin = User.builder()
                        .name("Administrator")
                        .email(normalizedEmail)
                        .passwordHash(passwordEncoder.encode(adminPassword))
                        .role(Role.ADMIN)
                        .build();

                userRepository.save(admin);
                log.info("Initialized bootstrap ADMIN user with email: {}", normalizedEmail);
            }
        }
    }
}

