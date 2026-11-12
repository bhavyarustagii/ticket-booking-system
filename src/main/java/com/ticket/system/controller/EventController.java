package com.ticket.system.controller;

import com.ticket.system.dto.request.CreateEventRequest;
import com.ticket.system.dto.response.ApiResponse;
import com.ticket.system.dto.response.EventDetailResponse;
import com.ticket.system.dto.response.EventResponse;
import com.ticket.system.service.EventService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/events")
@RequiredArgsConstructor
public class EventController {

    private final EventService eventService;

    @PostMapping
    public ResponseEntity<ApiResponse<EventResponse>> createEvent(
            @Valid @RequestBody CreateEventRequest request
    ) {
        EventResponse created = eventService.createEvent(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(created, "Event created successfully"));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<Page<EventResponse>>> listEvents(
            @PageableDefault(size = 20, sort = "startTime", direction = Sort.Direction.ASC) Pageable pageable
    ) {
        Page<EventResponse> events = eventService.getAllEvents(pageable);
        return ResponseEntity.ok(ApiResponse.ok(events, "Events retrieved successfully"));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<EventDetailResponse>> getEvent(
            @PathVariable Long id
    ) {
        EventDetailResponse event = eventService.getEventWithSeats(id);
        return ResponseEntity.ok(ApiResponse.ok(event, "Event details retrieved successfully"));
    }
}

