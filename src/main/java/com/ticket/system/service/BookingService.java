package com.ticket.system.service;

import com.ticket.system.dto.request.CreateBookingRequest;
import com.ticket.system.dto.response.BookingResponse;
import com.ticket.system.entity.Booking;
import com.ticket.system.entity.BookingStatus;
import com.ticket.system.entity.Seat;
import com.ticket.system.entity.SeatStatus;
import com.ticket.system.entity.User;
import com.ticket.system.exception.BadRequestException;
import com.ticket.system.exception.ConflictException;
import com.ticket.system.exception.ResourceNotFoundException;
import com.ticket.system.repository.BookingRepository;
import com.ticket.system.repository.SeatRepository;
import com.ticket.system.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class BookingService {

    private static final int MAX_RETRIES = 3;
    private static final long BACKOFF_MS  = 50;

    private final BookingRepository bookingRepository;
    private final SeatRepository seatRepository;
    private final UserRepository userRepository;

    /**
     * Public entry-point: retries the booking up to MAX_RETRIES times on
     * ObjectOptimisticLockingFailureException, each retry in its own fresh
     * transaction so the EntityManager is not left in a rolled-back state.
     * After exhausting retries, or on any other conflict, throws ConflictException
     * which the GlobalExceptionHandler maps to HTTP 409.
     */
    public BookingResponse bookSeat(CreateBookingRequest request, Long userId) {
        int attempt = 0;
        while (true) {
            try {
                return attemptBookSeat(request, userId);
            } catch (ObjectOptimisticLockingFailureException ex) {
                attempt++;
                log.warn("Optimistic lock collision on seat {} (attempt {}/{})",
                        request.getSeatId(), attempt, MAX_RETRIES);
                if (attempt >= MAX_RETRIES) {
                    throw new ConflictException(
                            "Seat is being booked by another request. Please try again.");
                }
                try {
                    Thread.sleep(BACKOFF_MS * attempt);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new ConflictException("Booking interrupted. Please try again.");
                }
            }
        }
    }

    /**
     * Core booking logic, executed inside its own transaction so that optimistic
     * lock failures on commit roll back cleanly before the outer retry loop
     * starts a fresh transaction.
     *
     * How the version check prevents double-booking:
     *   When this method reads the Seat, Hibernate records the current version
     *   number.  On flush/commit, Hibernate issues:
     *     UPDATE seats SET status='BOOKED', version=N+1 WHERE id=? AND version=N
     *   If a concurrent transaction already incremented the version, the WHERE
     *   clause matches 0 rows → Hibernate throws ObjectOptimisticLockingFailureException
     *   before the booking row is ever inserted, guaranteeing that only one winner
     *   can commit for each version value.
     */
    @Transactional
    public BookingResponse attemptBookSeat(CreateBookingRequest request, Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));

        // 1. Read seat from database (version captured here)
        Seat seat = seatRepository.findById(request.getSeatId())
                .orElseThrow(() -> new ResourceNotFoundException("Seat not found with id: " + request.getSeatId()));

        if (!seat.getEvent().getId().equals(request.getEventId())) {
            throw new BadRequestException("Seat " + request.getSeatId() + " does not belong to event " + request.getEventId());
        }

        // 2. Check if seat is currently available (fast-fail before hitting the DB)
        if (seat.getStatus() != SeatStatus.AVAILABLE) {
            throw new ConflictException("Seat " + seat.getSeatNumber() + " is already " + seat.getStatus());
        }

        // 3. Mark seat BOOKED — version is incremented on flush;
        //    concurrent transactions with the same old version will fail here.
        seat.setStatus(SeatStatus.BOOKED);
        seatRepository.save(seat);

        // 4. Persist confirmed booking record
        String idempotencyKey = (request.getIdempotencyKey() != null && !request.getIdempotencyKey().isBlank())
                ? request.getIdempotencyKey()
                : UUID.randomUUID().toString();

        Booking booking = Booking.builder()
                .user(user)
                .event(seat.getEvent())
                .seat(seat)
                .status(BookingStatus.CONFIRMED)
                .idempotencyKey(idempotencyKey)
                .build();

        Booking savedBooking = bookingRepository.save(booking);

        return mapToResponse(savedBooking);
    }

    private BookingResponse mapToResponse(Booking booking) {
        return BookingResponse.builder()
                .id(booking.getId())
                .userId(booking.getUser().getId())
                .eventId(booking.getEvent().getId())
                .seatId(booking.getSeat().getId())
                .seatNumber(booking.getSeat().getSeatNumber())
                .status(booking.getStatus())
                .idempotencyKey(booking.getIdempotencyKey())
                .createdAt(booking.getCreatedAt())
                .build();
    }
}

