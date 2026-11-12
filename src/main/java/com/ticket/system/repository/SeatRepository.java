package com.ticket.system.repository;

import com.ticket.system.entity.Seat;
import com.ticket.system.entity.SeatStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SeatRepository extends JpaRepository<Seat, Long> {

    List<Seat> findByEventIdOrderBySeatNumberAsc(Long eventId);

    long countByEventIdAndStatus(Long eventId, SeatStatus status);
}

