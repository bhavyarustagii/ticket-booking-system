package com.ticket.system.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateBookingRequest {

    @NotNull(message = "Event ID is required")
    private Long eventId;

    @NotNull(message = "Seat ID is required")
    private Long seatId;

    // Optional client-supplied idempotency key
    private String idempotencyKey;
}

