package com.ticket.system.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticket.system.config.SecurityConfig;
import com.ticket.system.dto.request.CreateBookingRequest;
import com.ticket.system.dto.response.BookingResponse;
import com.ticket.system.entity.BookingStatus;
import com.ticket.system.entity.Role;
import com.ticket.system.exception.GlobalExceptionHandler;
import com.ticket.system.security.*;
import com.ticket.system.service.BookingService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(BookingController.class)
@Import({
        SecurityConfig.class,
        JwtAuthenticationFilter.class,
        DelegatedAuthenticationEntryPoint.class,
        DelegatedAccessDeniedHandler.class,
        GlobalExceptionHandler.class
})
class BookingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private BookingService bookingService;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private CustomUserDetailsService userDetailsService;

    @Test
    @DisplayName("POST /api/v1/bookings with valid request should return 201 Created")
    void bookSeat_success() throws Exception {
        CreateBookingRequest request = CreateBookingRequest.builder()
                .eventId(1L)
                .seatId(5L)
                .build();

        BookingResponse response = BookingResponse.builder()
                .id(100L)
                .userId(1L)
                .eventId(1L)
                .seatId(5L)
                .seatNumber("SEAT-005")
                .status(BookingStatus.CONFIRMED)
                .createdAt(Instant.now())
                .build();

        when(bookingService.bookSeat(any(), any())).thenReturn(response);

        // Use a real UserPrincipal so @AuthenticationPrincipal resolves to the correct type.
        // @WithMockUser injects Spring's User, not our UserPrincipal, causing principal.getId() to NPE.
        UserPrincipal principal = new UserPrincipal(
                1L, "user@test.com", "hash", Role.USER,
                List.of(new SimpleGrantedAuthority("ROLE_USER"))
        );
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());

        mockMvc.perform(post("/api/v1/bookings")
                        .with(authentication(auth))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(100))
                .andExpect(jsonPath("$.data.status").value("CONFIRMED"));
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("POST /api/v1/bookings with missing eventId or seatId should return 400 Bad Request")
    void bookSeat_invalid_returnsBadRequest() throws Exception {
        CreateBookingRequest request = CreateBookingRequest.builder()
                .eventId(null)
                .seatId(null)
                .build();

        mockMvc.perform(post("/api/v1/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.validationErrors.eventId").exists())
                .andExpect(jsonPath("$.validationErrors.seatId").exists());
    }

    @Test
    @DisplayName("POST /api/v1/bookings unauthenticated should return 401 Unauthorized")
    void bookSeat_unauthenticated_returnsUnauthorized() throws Exception {
        CreateBookingRequest request = CreateBookingRequest.builder()
                .eventId(1L)
                .seatId(5L)
                .build();

        mockMvc.perform(post("/api/v1/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }
}

