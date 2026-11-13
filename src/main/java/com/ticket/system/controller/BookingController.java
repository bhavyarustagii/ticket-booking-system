package com.ticket.system.controller;

import com.ticket.system.dto.request.CreateBookingRequest;
import com.ticket.system.dto.response.ApiResponse;
import com.ticket.system.dto.response.BookingResponse;
import com.ticket.system.security.UserPrincipal;
import com.ticket.system.service.BookingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/bookings")
@RequiredArgsConstructor
public class BookingController {

    private final BookingService bookingService;

    @PostMapping
    public ResponseEntity<ApiResponse<BookingResponse>> bookSeat(
            @Valid @RequestBody CreateBookingRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        BookingResponse response = bookingService.bookSeat(request, principal.getId());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(response, "Seat booked successfully"));
    }
}

