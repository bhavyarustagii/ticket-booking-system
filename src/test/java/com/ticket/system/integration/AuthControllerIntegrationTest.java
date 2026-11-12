package com.ticket.system.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticket.system.dto.request.CreateEventRequest;
import com.ticket.system.dto.request.LoginRequest;
import com.ticket.system.dto.request.RegisterRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private com.ticket.system.repository.UserRepository userRepository;

    @Autowired
    private org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;

    @org.junit.jupiter.api.BeforeEach
    void setUpAdmin() {
        String adminEmail = "admin@ticket.local";
        if (userRepository.findByEmail(adminEmail).isEmpty()) {
            userRepository.save(com.ticket.system.entity.User.builder()
                    .name("Administrator")
                    .email(adminEmail)
                    .passwordHash(passwordEncoder.encode("DevAdminPassword123!"))
                    .role(com.ticket.system.entity.Role.ADMIN)
                    .build());
        }
    }

    @Test
    @DisplayName("Security: Registering with 'role':'ADMIN' in payload must be ignored and always assign USER role")
    void registerWithAdminRoleInPayload_shouldStillCreateUserRole() throws Exception {
        String email = "privilege-test-" + UUID.randomUUID() + "@example.com";
        // Explicitly inject "role": "ADMIN" into raw JSON payload to simulate an escalation attempt
        String maliciousPayload = """
                {
                    "name": "Attacker",
                    "email": "%s",
                    "password": "Password123!",
                    "role": "ADMIN"
                }
                """.formatted(email);

        String response = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(maliciousPayload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.role").value("USER"))
                .andReturn().getResponse().getContentAsString();

        String token = objectMapper.readTree(response).get("data").get("token").asText();

        // Verify that this token cannot perform ADMIN actions like creating events
        CreateEventRequest eventRequest = CreateEventRequest.builder()
                .name("Illicit Event")
                .venue("Secret Venue")
                .startTime(Instant.now().plus(7, ChronoUnit.DAYS))
                .totalSeats(10)
                .build();

        mockMvc.perform(post("/api/v1/events")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(eventRequest)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    @DisplayName("End-to-End: Bootstrapped admin login, user registration, and role-based access control")
    void fullAuthAndAuthorizationFlow() throws Exception {
        String userEmail = "regular-user-" + UUID.randomUUID() + "@example.com";
        String password = "StrongPassword123!";

        // 1. Obtain ADMIN token by logging in as the bootstrapped system administrator
        LoginRequest adminLogin = LoginRequest.builder()
                .email("admin@ticket.local")
                .password("DevAdminPassword123!")
                .build();

        String adminLoginResponse = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(adminLogin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.token").isString())
                .andExpect(jsonPath("$.data.role").value("ADMIN"))
                .andReturn().getResponse().getContentAsString();

        String adminToken = objectMapper.readTree(adminLoginResponse).get("data").get("token").asText();
        assertThat(adminToken).isNotBlank();

        // 2. Register regular USER (role field omitted)
        RegisterRequest userRegister = RegisterRequest.builder()
                .name("Regular User")
                .email(userEmail)
                .password(password)
                .build();

        String userRegResponse = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(userRegister)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.role").value("USER"))
                .andReturn().getResponse().getContentAsString();

        String userToken = objectMapper.readTree(userRegResponse).get("data").get("token").asText();
        assertThat(userToken).isNotBlank();

        // 3. Reject duplicate registration with 409 Conflict
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(userRegister)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));

        // 4. Login with registered user credentials
        LoginRequest loginRequest = LoginRequest.builder()
                .email(userEmail)
                .password(password)
                .build();

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.token").isString())
                .andExpect(jsonPath("$.data.email").value(userEmail));

        // 5. Login with wrong password returns 401 Unauthorized
        LoginRequest badLogin = LoginRequest.builder()
                .email(userEmail)
                .password("wrongPassword")
                .build();

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(badLogin)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));

        // 6. Accessing POST /api/v1/events with USER token should be FORBIDDEN (403)
        CreateEventRequest eventRequest = CreateEventRequest.builder()
                .name("Secured Concert")
                .venue("Madison Square Garden")
                .startTime(Instant.now().plus(14, ChronoUnit.DAYS))
                .totalSeats(50)
                .build();

        mockMvc.perform(post("/api/v1/events")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(eventRequest)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));

        // 7. Accessing POST /api/v1/events without token should be UNAUTHORIZED (401)
        mockMvc.perform(post("/api/v1/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(eventRequest)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));

        // 8. Accessing POST /api/v1/events with bootstrapped ADMIN token should SUCCEED (201)
        String createdEventResp = mockMvc.perform(post("/api/v1/events")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(eventRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.name").value("Secured Concert"))
                .andReturn().getResponse().getContentAsString();

        Long eventId = objectMapper.readTree(createdEventResp).get("data").get("id").asLong();

        // 9. Authenticated USER can view events (200 OK)
        mockMvc.perform(get("/api/v1/events/" + eventId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(eventId));

        // 10. Unauthenticated request to view event returns 401
        mockMvc.perform(get("/api/v1/events/" + eventId)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }
}
