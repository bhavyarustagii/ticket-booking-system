package com.ticket.system.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EventResponse {
    private Long id;
    private String name;
    private String venue;
    private Instant startTime;
    private Integer totalSeats;
    private Instant createdAt;
}

