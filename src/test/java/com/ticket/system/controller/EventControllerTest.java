package com.ticket.system.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticket.system.config.SecurityConfig;
import com.ticket.system.dto.request.CreateEventRequest;
import com.ticket.system.dto.response.EventDetailResponse;
import com.ticket.system.dto.response.EventResponse;
import com.ticket.system.dto.response.SeatResponse;
import com.ticket.system.entity.SeatStatus;
import com.ticket.system.exception.GlobalExceptionHandler;
import com.ticket.system.exception.ResourceNotFoundException;
import com.ticket.system.security.CustomUserDetailsService;
import com.ticket.system.security.DelegatedAccessDeniedHandler;
import com.ticket.system.security.DelegatedAuthenticationEntryPoint;
import com.ticket.system.security.JwtAuthenticationFilter;
import com.ticket.system.security.JwtService;
import com.ticket.system.service.EventService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(EventController.class)
@Import({
        SecurityConfig.class,
        JwtAuthenticationFilter.class,
        DelegatedAuthenticationEntryPoint.class,
        DelegatedAccessDeniedHandler.class,
        GlobalExceptionHandler.class
})
class EventControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private EventService eventService;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private CustomUserDetailsService userDetailsService;

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("POST /api/v1/events with valid payload as ADMIN should return 201 Created and EventResponse")
    void createEvent_validPayloadAsAdmin_returnsCreated() throws Exception {
        Instant startTime = Instant.now().plus(5, ChronoUnit.DAYS);
        CreateEventRequest request = CreateEventRequest.builder()
                .name("Taylor Swift The Eras Tour")
                .venue("Wembley Stadium")
                .startTime(startTime)
                .totalSeats(500)
                .build();

        EventResponse response = EventResponse.builder()
                .id(1L)
                .name(request.getName())
                .venue(request.getVenue())
                .startTime(startTime)
                .totalSeats(500)
                .createdAt(Instant.now())
                .build();

        when(eventService.createEvent(any(CreateEventRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/v1/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.name").value("Taylor Swift The Eras Tour"))
                .andExpect(jsonPath("$.data.totalSeats").value(500));
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("POST /api/v1/events as non-ADMIN USER should return 403 Forbidden")
    void createEvent_asUser_returnsForbidden() throws Exception {
        CreateEventRequest request = CreateEventRequest.builder()
                .name("Unauthorized Event")
                .venue("Anywhere")
                .startTime(Instant.now().plus(2, ChronoUnit.DAYS))
                .totalSeats(10)
                .build();

        mockMvc.perform(post("/api/v1/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.error").value("Forbidden"));
    }

    @Test
    @DisplayName("POST /api/v1/events unauthenticated should return 401 Unauthorized")
    void createEvent_unauthenticated_returnsUnauthorized() throws Exception {
        CreateEventRequest request = CreateEventRequest.builder()
                .name("Unauthorized Event")
                .venue("Anywhere")
                .startTime(Instant.now().plus(2, ChronoUnit.DAYS))
                .totalSeats(10)
                .build();

        mockMvc.perform(post("/api/v1/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("POST /api/v1/events with invalid payload should return 400 Bad Request with field errors")
    void createEvent_invalidPayload_returnsBadRequest() throws Exception {
        CreateEventRequest invalidRequest = CreateEventRequest.builder()
                .name("") // Blank name
                .venue("") // Blank venue
                .startTime(Instant.now().minus(1, ChronoUnit.DAYS)) // Past time
                .totalSeats(0) // Minimum is 1
                .build();

        mockMvc.perform(post("/api/v1/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.validationErrors.name").exists())
                .andExpect(jsonPath("$.validationErrors.venue").exists())
                .andExpect(jsonPath("$.validationErrors.startTime").exists())
                .andExpect(jsonPath("$.validationErrors.totalSeats").exists());
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("GET /api/v1/events as authenticated USER should return 200 OK and paged events")
    void listEvents_asUser_returnsPagedEvents() throws Exception {
        EventResponse item = EventResponse.builder()
                .id(1L)
                .name("Jazz Night")
                .venue("Blue Note")
                .startTime(Instant.now().plus(2, ChronoUnit.DAYS))
                .totalSeats(100)
                .build();

        when(eventService.getAllEvents(any())).thenReturn(new PageImpl<>(List.of(item), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/v1/events")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content[0].name").value("Jazz Night"));
    }

    @Test
    @DisplayName("GET /api/v1/events unauthenticated should return 401 Unauthorized")
    void listEvents_unauthenticated_returnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/events")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("GET /api/v1/events/{id} should return 200 OK with seat availability")
    void getEvent_existingId_returnsDetail() throws Exception {
        EventDetailResponse detail = EventDetailResponse.builder()
                .id(1L)
                .name("Tech Summit 2026")
                .venue("Convention Center")
                .startTime(Instant.now().plus(10, ChronoUnit.DAYS))
                .totalSeats(2)
                .availableSeats(1)
                .heldSeats(1)
                .bookedSeats(0)
                .seats(List.of(
                        SeatResponse.builder().id(101L).seatNumber("SEAT-001").status(SeatStatus.AVAILABLE).build(),
                        SeatResponse.builder().id(102L).seatNumber("SEAT-002").status(SeatStatus.HELD).build()
                ))
                .build();

        when(eventService.getEventWithSeats(1L)).thenReturn(detail);

        mockMvc.perform(get("/api/v1/events/1")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.availableSeats").value(1))
                .andExpect(jsonPath("$.data.heldSeats").value(1))
                .andExpect(jsonPath("$.data.seats[0].seatNumber").value("SEAT-001"));
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("GET /api/v1/events/{id} with missing id should return 404 Not Found")
    void getEvent_missingId_returnsNotFound() throws Exception {
        when(eventService.getEventWithSeats(eq(999L)))
                .thenThrow(new ResourceNotFoundException("Event not found with id: 999"));

        mockMvc.perform(get("/api/v1/events/999")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("Event not found with id: 999"));
    }
}
