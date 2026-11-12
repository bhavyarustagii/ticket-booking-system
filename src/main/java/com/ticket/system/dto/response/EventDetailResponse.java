package com.ticket.system.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EventDetailResponse {
    private Long id;
    private String name;
    private String venue;
    private Instant startTime;
    private Integer totalSeats;
    private long availableSeats;
    private long heldSeats;
    private long bookedSeats;
    private List<SeatResponse> seats;
    private Instant createdAt;
}

