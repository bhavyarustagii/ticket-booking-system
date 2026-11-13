package com.ticket.system.service;

import com.ticket.system.dto.request.CreateBookingRequest;
import com.ticket.system.dto.response.BookingResponse;
import com.ticket.system.entity.*;
import com.ticket.system.exception.BadRequestException;
import com.ticket.system.exception.ConflictException;
import com.ticket.system.exception.ResourceNotFoundException;
import com.ticket.system.repository.BookingRepository;
import com.ticket.system.repository.SeatRepository;
import com.ticket.system.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BookingServiceTest {

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private SeatRepository seatRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private BookingService bookingService;

    @Test
    @DisplayName("bookSeat should successfully book an available seat and return BookingResponse")
    void bookSeat_success() {
        User user = User.builder().id(1L).email("user@example.com").name("User").role(Role.USER).build();
        Event event = Event.builder().id(10L).name("Concert").build();
        Seat seat = Seat.builder().id(100L).seatNumber("SEAT-001").status(SeatStatus.AVAILABLE).event(event).version(0L).build();

        CreateBookingRequest request = CreateBookingRequest.builder()
                .eventId(10L)
                .seatId(100L)
                .build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(seatRepository.findById(100L)).thenReturn(Optional.of(seat));
        when(seatRepository.save(any(Seat.class))).thenReturn(seat);
        when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> {
            Booking b = inv.getArgument(0);
            b.setId(500L);
            b.setCreatedAt(Instant.now());
            return b;
        });

        BookingResponse response = bookingService.bookSeat(request, 1L);

        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(500L);
        assertThat(response.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(seat.getStatus()).isEqualTo(SeatStatus.BOOKED);

        verify(seatRepository).save(seat);
        verify(bookingRepository).save(any(Booking.class));
    }

    @Test
    @DisplayName("bookSeat when seat is not AVAILABLE should throw ConflictException")
    void bookSeat_alreadyBooked_throwsConflict() {
        User user = User.builder().id(1L).build();
        Event event = Event.builder().id(10L).build();
        Seat seat = Seat.builder().id(100L).seatNumber("SEAT-001").status(SeatStatus.BOOKED).event(event).build();

        CreateBookingRequest request = CreateBookingRequest.builder()
                .eventId(10L)
                .seatId(100L)
                .build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(seatRepository.findById(100L)).thenReturn(Optional.of(seat));

        assertThatThrownBy(() -> bookingService.bookSeat(request, 1L))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("already BOOKED");

        verify(seatRepository, never()).save(any());
        verify(bookingRepository, never()).save(any());
    }

    @Test
    @DisplayName("bookSeat when seat does not belong to specified event should throw BadRequestException")
    void bookSeat_eventMismatch_throwsBadRequest() {
        User user = User.builder().id(1L).build();
        Event event = Event.builder().id(20L).build(); // Different event ID
        Seat seat = Seat.builder().id(100L).seatNumber("SEAT-001").status(SeatStatus.AVAILABLE).event(event).build();

        CreateBookingRequest request = CreateBookingRequest.builder()
                .eventId(10L)
                .seatId(100L)
                .build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(seatRepository.findById(100L)).thenReturn(Optional.of(seat));

        assertThatThrownBy(() -> bookingService.bookSeat(request, 1L))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("does not belong to event");
    }

    @Test
    @DisplayName("bookSeat when seat is missing should throw ResourceNotFoundException")
    void bookSeat_missingSeat_throwsNotFound() {
        User user = User.builder().id(1L).build();
        CreateBookingRequest request = CreateBookingRequest.builder().eventId(10L).seatId(999L).build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(seatRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> bookingService.bookSeat(request, 1L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("999");
    }
}

