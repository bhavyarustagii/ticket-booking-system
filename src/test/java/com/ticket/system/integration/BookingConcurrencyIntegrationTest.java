package com.ticket.system.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticket.system.dto.request.CreateBookingRequest;
import com.ticket.system.entity.*;
import com.ticket.system.repository.BookingRepository;
import com.ticket.system.repository.EventRepository;
import com.ticket.system.repository.SeatRepository;
import com.ticket.system.repository.UserRepository;
import com.ticket.system.security.JwtService;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Slf4j
public class BookingConcurrencyIntegrationTest {

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

    @Autowired
    private JwtService jwtService;

    @Test
    @DisplayName("Simultaneous 50 requests on the same seat demonstrate race conditions and DB index collisions")
    void simultaneousBookingsOnSameSeat_demonstratesConcurrencyIssues() throws Exception {
        final int numberOfThreads = 50;

        // 1. Create target event with exactly 1 available seat
        Event event = Event.builder()
                .name("Flash Sale Concurrency Event " + UUID.randomUUID())
                .venue("Virtual Arena")
                .startTime(Instant.now().plus(10, ChronoUnit.DAYS))
                .totalSeats(1)
                .build();

        Seat targetSeat = Seat.builder()
                .seatNumber("SEAT-001")
                .status(SeatStatus.AVAILABLE)
                .version(0L)
                .build();
        event.addSeat(targetSeat);
        event = eventRepository.save(event);
        Long targetSeatId = event.getSeats().get(0).getId();
        Long eventId = event.getId();

        // 2. Pre-generate 50 users and JWT tokens so all threads simulate distinct concurrent customers
        List<String> userTokens = new ArrayList<>(numberOfThreads);
        for (int i = 0; i < numberOfThreads; i++) {
            User user = userRepository.save(User.builder()
                    .name("Customer " + i)
                    .email("concurrent-customer-" + i + "-" + UUID.randomUUID() + "@example.com")
                    .passwordHash("$2a$10$7EqJtq98hPqEX7fNZaFWoO.8/k0aT9U08p0V21R1lQ177Qp5l.D8i")
                    .role(Role.USER)
                    .build());
            userTokens.add(jwtService.generateToken(user));
        }

        // 3. Coordinate all 50 threads to trigger simultaneously
        ExecutorService executorService = Executors.newFixedThreadPool(numberOfThreads);
        CountDownLatch readyLatch = new CountDownLatch(numberOfThreads);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(numberOfThreads);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger conflictCount = new AtomicInteger(0);
        AtomicInteger otherErrors = new AtomicInteger(0);

        for (int i = 0; i < numberOfThreads; i++) {
            final String token = userTokens.get(i);
            executorService.submit(() -> {
                readyLatch.countDown();
                try {
                    // Wait at the gate until all 50 threads are queued and ready
                    startGate.await();

                    CreateBookingRequest request = CreateBookingRequest.builder()
                            .eventId(eventId)
                            .seatId(targetSeatId)
                            .idempotencyKey(UUID.randomUUID().toString())
                            .build();

                    MvcResult result = mockMvc.perform(post("/api/v1/bookings")
                                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(request)))
                            .andReturn();

                    int status = result.getResponse().getStatus();
                    if (status == 201) {
                        successCount.incrementAndGet();
                    } else if (status == 409) {
                        conflictCount.incrementAndGet();
                    } else {
                        otherErrors.incrementAndGet();
                    }
                } catch (Exception e) {
                    otherErrors.incrementAndGet();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        // Ensure all 50 threads have spawned, then release the gate simultaneously
        readyLatch.await(10, TimeUnit.SECONDS);
        startGate.countDown();

        // Wait for all 50 threads to complete
        boolean completed = doneLatch.await(30, TimeUnit.SECONDS);
        executorService.shutdown();
        assertThat(completed).isTrue();

        // 4. Inspect final state in database
        Seat finalSeat = seatRepository.findById(targetSeatId).orElseThrow();
        List<Booking> confirmedBookings = bookingRepository.findByEventId(eventId).stream()
                .filter(b -> b.getSeat().getId().equals(targetSeatId) && b.getStatus() == BookingStatus.CONFIRMED)
                .toList();

        // 5. Comprehensive reporting of concurrency experiment
        log.info("======================================================================");
        log.info(" CONCURRENCY TEST SUMMARY (50 simultaneous requests on single seat)");
        log.info("======================================================================");
        log.info(" Total Concurrent Requests: {}", numberOfThreads);
        log.info(" HTTP 201 Created (Succeeded): {}", successCount.get());
        log.info(" HTTP 409 Conflict (Rejected by DB constraint / read-check): {}", conflictCount.get());
        log.info(" Other Errors (e.g. 500s or timeouts): {}", otherErrors.get());
        log.info(" Final Seat Status in DB: {}", finalSeat.getStatus());
        log.info(" Number of CONFIRMED Bookings in DB: {}", confirmedBookings.size());
        log.info("======================================================================");

        // Assertions:
        // Due to partial unique index (uq_seat_confirmed_booking), at most 1 confirmed booking can persist in DB
        assertThat(confirmedBookings.size()).isEqualTo(1);
        assertThat(finalSeat.getStatus()).isEqualTo(SeatStatus.BOOKED);

        // Exactly 1 request successfully committed, and all 49 other requests were rejected (HTTP 409)
        assertThat(successCount.get()).isEqualTo(1);
        assertThat(conflictCount.get()).isEqualTo(49);
        assertThat(otherErrors.get()).isZero();
    }
}

