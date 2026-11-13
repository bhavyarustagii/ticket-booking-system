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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class BookingService {

    private final BookingRepository bookingRepository;
    private final SeatRepository seatRepository;
    private final UserRepository userRepository;

    /**
     * Naive booking workflow with no distributed locks or concurrency synchronization:
     * Reads seat status, validates availability in memory, sets status to BOOKED, and saves booking.
     * Demonstrates race conditions under simultaneous client traffic.
     */
    @Transactional
    public BookingResponse bookSeat(CreateBookingRequest request, Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));

        // 1. Read seat from database
        Seat seat = seatRepository.findById(request.getSeatId())
                .orElseThrow(() -> new ResourceNotFoundException("Seat not found with id: " + request.getSeatId()));

        if (!seat.getEvent().getId().equals(request.getEventId())) {
            throw new BadRequestException("Seat " + request.getSeatId() + " does not belong to event " + request.getEventId());
        }

        // 2. Check if seat is currently available
        if (seat.getStatus() != SeatStatus.AVAILABLE) {
            throw new ConflictException("Seat " + seat.getSeatNumber() + " is already " + seat.getStatus());
        }

        // 3. Update seat state to BOOKED
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

