package com.ticket.system.dto.request;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateEventRequest {

    @NotBlank(message = "Event name is required")
    @Size(max = 255, message = "Event name must not exceed 255 characters")
    private String name;

    @NotBlank(message = "Venue is required")
    @Size(max = 255, message = "Venue must not exceed 255 characters")
    private String venue;

    @NotNull(message = "Start time is required")
    @Future(message = "Start time must be in the future")
    private Instant startTime;

    @NotNull(message = "Total seats count is required")
    @Min(value = 1, message = "Total seats must be at least 1")
    @Max(value = 10000, message = "Total seats cannot exceed 10,000 per event")
    private Integer totalSeats;
}

