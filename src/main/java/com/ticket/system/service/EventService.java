package com.ticket.system.service;

import com.ticket.system.dto.request.CreateEventRequest;
import com.ticket.system.dto.response.EventDetailResponse;
import com.ticket.system.dto.response.EventResponse;
import com.ticket.system.dto.response.SeatResponse;
import com.ticket.system.entity.Event;
import com.ticket.system.entity.Seat;
import com.ticket.system.entity.SeatStatus;
import com.ticket.system.exception.ResourceNotFoundException;
import com.ticket.system.repository.EventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class EventService {

    private final EventRepository eventRepository;

    /**
     * Creates an event and atomically pre-populates all associated seats
     * so that seat inventory is immediately available for concurrency checks.
     */
    @Transactional
    public EventResponse createEvent(CreateEventRequest request) {
        log.info("Creating event '{}' at '{}' with {} total seats", 
                request.getName(), request.getVenue(), request.getTotalSeats());

        Event event = Event.builder()
                .name(request.getName().trim())
                .venue(request.getVenue().trim())
                .startTime(request.getStartTime())
                .totalSeats(request.getTotalSeats())
                .seats(new ArrayList<>())
                .build();

        // Batch populate seats upfront to guarantee inventory consistency from event inception
        for (int i = 1; i <= request.getTotalSeats(); i++) {
            Seat seat = Seat.builder()
                    .seatNumber(String.format("SEAT-%03d", i))
                    .status(SeatStatus.AVAILABLE)
                    .version(0L)
                    .build();
            event.addSeat(seat);
        }

        Event savedEvent = eventRepository.save(event);
        return mapToEventResponse(savedEvent);
    }

    /**
     * Lists events paginated to prevent memory exhaustion when event count grows.
     */
    @Transactional(readOnly = true)
    public Page<EventResponse> getAllEvents(Pageable pageable) {
        return eventRepository.findAll(pageable)
                .map(this::mapToEventResponse);
    }

    /**
     * Retrieves event with all associated seats and computed availability statistics.
     */
    @Transactional(readOnly = true)
    public EventDetailResponse getEventWithSeats(Long id) {
        Event event = eventRepository.findByIdWithSeats(id)
                .orElseThrow(() -> new ResourceNotFoundException("Event not found with id: " + id));

        long available = 0;
        long held = 0;
        long booked = 0;

        List<SeatResponse> seatResponses = new ArrayList<>(event.getSeats().size());
        for (Seat seat : event.getSeats()) {
            if (seat.getStatus() == SeatStatus.AVAILABLE) available++;
            else if (seat.getStatus() == SeatStatus.HELD) held++;
            else if (seat.getStatus() == SeatStatus.BOOKED) booked++;

            seatResponses.add(SeatResponse.builder()
                    .id(seat.getId())
                    .seatNumber(seat.getSeatNumber())
                    .status(seat.getStatus())
                    .build());
        }

        // Keep deterministic order for predictable client rendering
        seatResponses.sort(Comparator.comparing(SeatResponse::getSeatNumber));

        return EventDetailResponse.builder()
                .id(event.getId())
                .name(event.getName())
                .venue(event.getVenue())
                .startTime(event.getStartTime())
                .totalSeats(event.getTotalSeats())
                .availableSeats(available)
                .heldSeats(held)
                .bookedSeats(booked)
                .seats(seatResponses)
                .createdAt(event.getCreatedAt())
                .build();
    }

    private EventResponse mapToEventResponse(Event event) {
        return EventResponse.builder()
                .id(event.getId())
                .name(event.getName())
                .venue(event.getVenue())
                .startTime(event.getStartTime())
                .totalSeats(event.getTotalSeats())
                .createdAt(event.getCreatedAt())
                .build();
    }
}

