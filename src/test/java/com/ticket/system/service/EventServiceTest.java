package com.ticket.system.service;

import com.ticket.system.dto.request.CreateEventRequest;
import com.ticket.system.dto.response.EventDetailResponse;
import com.ticket.system.dto.response.EventResponse;
import com.ticket.system.entity.Event;
import com.ticket.system.entity.Seat;
import com.ticket.system.entity.SeatStatus;
import com.ticket.system.exception.ResourceNotFoundException;
import com.ticket.system.repository.EventRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EventServiceTest {

    @Mock
    private EventRepository eventRepository;

    @InjectMocks
    private EventService eventService;

    @Test
    @DisplayName("createEvent should persist event and auto-generate the requested number of available seats")
    void createEvent_shouldGenerateSeats() {
        CreateEventRequest request = CreateEventRequest.builder()
                .name("Coldplay Live in Mumbai")
                .venue("DY Patil Stadium")
                .startTime(Instant.now().plus(7, ChronoUnit.DAYS))
                .totalSeats(5)
                .build();

        when(eventRepository.save(any(Event.class))).thenAnswer(invocation -> {
            Event saved = invocation.getArgument(0);
            saved.setId(100L);
            saved.setCreatedAt(Instant.now());
            return saved;
        });

        EventResponse response = eventService.createEvent(request);

        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(100L);
        assertThat(response.getName()).isEqualTo("Coldplay Live in Mumbai");
        assertThat(response.getTotalSeats()).isEqualTo(5);

        ArgumentCaptor<Event> captor = ArgumentCaptor.forClass(Event.class);
        verify(eventRepository).save(captor.capture());
        Event captured = captor.getValue();

        // Verify that exact number of seats were pre-generated with AVAILABLE status
        assertThat(captured.getSeats()).hasSize(5);
        for (int i = 0; i < 5; i++) {
            Seat seat = captured.getSeats().get(i);
            assertThat(seat.getSeatNumber()).isEqualTo(String.format("SEAT-%03d", i + 1));
            assertThat(seat.getStatus()).isEqualTo(SeatStatus.AVAILABLE);
            assertThat(seat.getVersion()).isZero();
            assertThat(seat.getEvent()).isEqualTo(captured);
        }
    }

    @Test
    @DisplayName("getAllEvents should return paged event responses")
    void getAllEvents_shouldReturnPagedResponses() {
        Event event1 = Event.builder().id(1L).name("Event 1").venue("V1").totalSeats(50).startTime(Instant.now()).build();
        Event event2 = Event.builder().id(2L).name("Event 2").venue("V2").totalSeats(100).startTime(Instant.now()).build();

        when(eventRepository.findAll(any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(event1, event2)));

        Page<EventResponse> result = eventService.getAllEvents(PageRequest.of(0, 10));

        assertThat(result.getContent()).hasSize(2);
        assertThat(result.getContent().get(0).getName()).isEqualTo("Event 1");
        assertThat(result.getContent().get(1).getName()).isEqualTo("Event 2");
    }

    @Test
    @DisplayName("getEventWithSeats should aggregate seat availability statistics")
    void getEventWithSeats_shouldAggregateAvailability() {
        Event event = Event.builder()
                .id(1L)
                .name("Rock Festival")
                .venue("Arena")
                .startTime(Instant.now().plus(2, ChronoUnit.DAYS))
                .totalSeats(3)
                .seats(new ArrayList<>())
                .createdAt(Instant.now())
                .build();

        event.addSeat(Seat.builder().id(11L).seatNumber("SEAT-001").status(SeatStatus.AVAILABLE).build());
        event.addSeat(Seat.builder().id(12L).seatNumber("SEAT-002").status(SeatStatus.HELD).build());
        event.addSeat(Seat.builder().id(13L).seatNumber("SEAT-003").status(SeatStatus.BOOKED).build());

        when(eventRepository.findByIdWithSeats(1L)).thenReturn(Optional.of(event));

        EventDetailResponse detail = eventService.getEventWithSeats(1L);

        assertThat(detail.getId()).isEqualTo(1L);
        assertThat(detail.getTotalSeats()).isEqualTo(3);
        assertThat(detail.getAvailableSeats()).isEqualTo(1);
        assertThat(detail.getHeldSeats()).isEqualTo(1);
        assertThat(detail.getBookedSeats()).isEqualTo(1);
        assertThat(detail.getSeats()).hasSize(3);
    }

    @Test
    @DisplayName("getEventWithSeats should throw ResourceNotFoundException when id is missing")
    void getEventWithSeats_notFound_shouldThrowException() {
        when(eventRepository.findByIdWithSeats(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> eventService.getEventWithSeats(999L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("999");
    }
}

