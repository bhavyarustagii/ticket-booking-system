package com.ticket.system.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticket.system.dto.request.CreateEventRequest;
import com.ticket.system.entity.*;
import com.ticket.system.repository.BookingRepository;
import com.ticket.system.repository.EventRepository;
import com.ticket.system.repository.SeatRepository;
import com.ticket.system.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class EventIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private SeatRepository seatRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private BookingRepository bookingRepository;

    @Test
    @org.springframework.security.test.context.support.WithMockUser(roles = "ADMIN")
    @DisplayName("End-to-end: Create event, verify auto-generated seats in DB, and fetch availability")
    void shouldCreateEventAndFetchAvailability() throws Exception {
        CreateEventRequest request = CreateEventRequest.builder()
                .name("Coldplay World Tour")
                .venue("Wembley Arena")
                .startTime(Instant.now().plus(30, ChronoUnit.DAYS))
                .totalSeats(10)
                .build();

        String responseBody = mockMvc.perform(post("/api/v1/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.name").value("Coldplay World Tour"))
                .andExpect(jsonPath("$.data.totalSeats").value(10))
                .andReturn().getResponse().getContentAsString();

        Long eventId = objectMapper.readTree(responseBody).get("data").get("id").asLong();

        // Verify seats persisted in database
        Event savedEvent = eventRepository.findByIdWithSeats(eventId).orElseThrow();
        assertThat(savedEvent.getSeats()).hasSize(10);
        assertThat(savedEvent.getSeats()).allMatch(s -> s.getStatus() == SeatStatus.AVAILABLE);

        // Fetch event with seat availability through API
        mockMvc.perform(get("/api/v1/events/" + eventId)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.availableSeats").value(10))
                .andExpect(jsonPath("$.data.heldSeats").value(0))
                .andExpect(jsonPath("$.data.bookedSeats").value(0))
                .andExpect(jsonPath("$.data.seats.length()").value(10));
    }

    @Test
    @DisplayName("Partial unique index should prevent multiple CONFIRMED bookings on the same seat but allow CANCELLED")
    void shouldEnforceOneConfirmedBookingPerSeat() {
        // Setup user, event, and seat
        User user = userRepository.save(User.builder()
                .name("Alice")
                .email("alice-" + UUID.randomUUID() + "@example.com")
                .passwordHash("$2a$10$7EqJtq98hPqEX7fNZaFWoO.8/k0aT9U08p0V21R1lQ177Qp5l.D8i")
                .role(Role.USER)
                .build());

        Event event = Event.builder()
                .name("Jazz Festival")
                .venue("Blue Note")
                .startTime(Instant.now().plus(5, ChronoUnit.DAYS))
                .totalSeats(1)
                .build();

        Seat seat = Seat.builder()
                .seatNumber("SEAT-001")
                .status(SeatStatus.BOOKED)
                .version(0L)
                .build();
        event.addSeat(seat);
        eventRepository.save(event);

        // First CONFIRMED booking succeeds
        Booking booking1 = Booking.builder()
                .user(user)
                .event(event)
                .seat(seat)
                .status(BookingStatus.CONFIRMED)
                .idempotencyKey("key-1-" + UUID.randomUUID())
                .build();
        bookingRepository.saveAndFlush(booking1);

        // A second CANCELLED booking on the same seat succeeds because status != 'CONFIRMED'
        Booking bookingCancelled = Booking.builder()
                .user(user)
                .event(event)
                .seat(seat)
                .status(BookingStatus.CANCELLED)
                .idempotencyKey("key-2-" + UUID.randomUUID())
                .build();
        Booking savedCancelled = bookingRepository.saveAndFlush(bookingCancelled);
        assertThat(savedCancelled.getId()).isNotNull();

        // A second CONFIRMED booking on the same seat must fail with DataIntegrityViolationException
        Booking booking2 = Booking.builder()
                .user(user)
                .event(event)
                .seat(seat)
                .status(BookingStatus.CONFIRMED)
                .idempotencyKey("key-3-" + UUID.randomUUID())
                .build();

        assertThatThrownBy(() -> bookingRepository.saveAndFlush(booking2))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}

