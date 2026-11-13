package com.ticket.system.dto.response;

import com.ticket.system.entity.BookingStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookingResponse {

    private Long id;
    private Long userId;
    private Long eventId;
    private Long seatId;
    private String seatNumber;
    private BookingStatus status;
    private String idempotencyKey;
    private Instant createdAt;
}

