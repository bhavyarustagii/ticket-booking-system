package com.ticket.system.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // CSRF protection is disabled because REST endpoints rely on stateless token authentication
            .csrf(AbstractHttpConfigurer::disable)
            // Stateless sessions avoid server-side session affinity under horizontal scaling
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                // Allow health checks for Docker and orchestrator probes without authentication
                .requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/info").permitAll()
                // Require authentication for all application business endpoints
                .anyRequest().authenticated()
            );

        return http.build();
    }
}

