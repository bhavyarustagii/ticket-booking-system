package com.ticket.system.config;

import com.ticket.system.security.DelegatedAccessDeniedHandler;
import com.ticket.system.security.DelegatedAuthenticationEntryPoint;
import com.ticket.system.security.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final DelegatedAuthenticationEntryPoint authenticationEntryPoint;
    private final DelegatedAccessDeniedHandler accessDeniedHandler;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // CSRF protection is disabled because REST endpoints rely on stateless token authentication
            .csrf(AbstractHttpConfigurer::disable)
            // Stateless sessions avoid server-side session affinity under horizontal scaling
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            // Return structured JSON error responses instead of default HTML error pages
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint(authenticationEntryPoint)
                .accessDeniedHandler(accessDeniedHandler)
            )
            .authorizeHttpRequests(auth -> auth
                // Public health and observability endpoints
                .requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/info").permitAll()
                // Public registration and login
                .requestMatchers("/api/v1/auth/**").permitAll()
                // Only ADMIN role may create events and auto-generate seat inventory
                .requestMatchers(HttpMethod.POST, "/api/v1/events").hasRole("ADMIN")
                // Any authenticated USER or ADMIN can browse and inspect events
                .requestMatchers(HttpMethod.GET, "/api/v1/events", "/api/v1/events/**").hasAnyRole("USER", "ADMIN")
                // Booking endpoints for authenticated users
                .requestMatchers("/api/v1/bookings/**").hasAnyRole("USER", "ADMIN")
                // All other endpoints require authentication
                .anyRequest().authenticated()
            )
            // Register JWT token filter prior to standard username/password filter
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }
}
