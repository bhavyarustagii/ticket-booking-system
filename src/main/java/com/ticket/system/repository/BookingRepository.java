package com.ticket.system.repository;

import com.ticket.system.entity.Booking;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BookingRepository extends JpaRepository<Booking, Long> {

    List<Booking> findByEventId(Long eventId);

    List<Booking> findByUserId(Long userId);

    Optional<Booking> findByIdempotencyKey(String idempotencyKey);
}

