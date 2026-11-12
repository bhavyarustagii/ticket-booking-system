package com.ticket.system.exception;

import com.ticket.system.dto.response.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    @DisplayName("Should translate ResourceNotFoundException into HTTP 404 ErrorResponse")
    void shouldHandleResourceNotFound() {
        HttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/events/999");
        ResourceNotFoundException ex = new ResourceNotFoundException("Event not found with id: 999");

        ResponseEntity<ErrorResponse> response = handler.handleNotFound(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getStatus()).isEqualTo(404);
        assertThat(response.getBody().getMessage()).isEqualTo("Event not found with id: 999");
        assertThat(response.getBody().getPath()).isEqualTo("/api/v1/events/999");
    }

    @Test
    @DisplayName("Should translate ConflictException into HTTP 409 ErrorResponse")
    void shouldHandleConflict() {
        HttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/bookings");
        ConflictException ex = new ConflictException("Seat A1 is already booked");

        ResponseEntity<ErrorResponse> response = handler.handleConflict(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getStatus()).isEqualTo(409);
        assertThat(response.getBody().getMessage()).isEqualTo("Seat A1 is already booked");
    }

    @Test
    @DisplayName("Should translate unexpected Exception into HTTP 500 ErrorResponse")
    void shouldHandleGeneralException() {
        HttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/events");
        Exception ex = new RuntimeException("Unexpected database failure");

        ResponseEntity<ErrorResponse> response = handler.handleGeneralException(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getStatus()).isEqualTo(500);
        assertThat(response.getBody().getMessage()).contains("An unexpected error occurred");
    }
}

