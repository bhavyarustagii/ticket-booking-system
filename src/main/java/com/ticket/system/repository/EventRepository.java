package com.ticket.system.repository;

import com.ticket.system.entity.Event;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface EventRepository extends JpaRepository<Event, Long> {

    // Fetch join loads the seats collection eagerly, preventing N+1 queries when loading seat availability
    @Query("SELECT e FROM Event e LEFT JOIN FETCH e.seats WHERE e.id = :id")
    Optional<Event> findByIdWithSeats(@Param("id") Long id);
}

