-- V2: Create initial domain tables for Users, Events, Seats, and Bookings

-- 1. Users table
CREATE TABLE users (
    id BIGSERIAL PRIMARY KEY,
    email VARCHAR(255) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL
);

-- 2. Events table
CREATE TABLE events (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    venue VARCHAR(255) NOT NULL,
    start_time TIMESTAMP WITH TIME ZONE NOT NULL,
    total_seats INT NOT NULL CHECK (total_seats > 0),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL
);

CREATE INDEX idx_events_start_time ON events (start_time);

-- 3. Seats table
-- Optimistic locking is supported via the version column
CREATE TABLE seats (
    id BIGSERIAL PRIMARY KEY,
    event_id BIGINT NOT NULL REFERENCES events(id) ON DELETE CASCADE,
    seat_number VARCHAR(50) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'AVAILABLE',
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT uq_event_seat_number UNIQUE (event_id, seat_number),
    CONSTRAINT chk_seat_status CHECK (status IN ('AVAILABLE', 'HELD', 'BOOKED'))
);

CREATE INDEX idx_seats_event_id_status ON seats (event_id, status);

-- 4. Bookings table
CREATE TABLE bookings (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    event_id BIGINT NOT NULL REFERENCES events(id),
    seat_id BIGINT NOT NULL REFERENCES seats(id),
    status VARCHAR(20) NOT NULL,
    idempotency_key VARCHAR(255),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT chk_booking_status CHECK (status IN ('PENDING', 'CONFIRMED', 'CANCELLED', 'EXPIRED'))
);

-- Partial unique index guarantees that a seat can have at most one CONFIRMED booking
CREATE UNIQUE INDEX uq_seat_confirmed_booking ON bookings (seat_id) WHERE status = 'CONFIRMED';

-- Query optimization indexes for fast lookup by user, event, and idempotency key
CREATE INDEX idx_bookings_user_id ON bookings (user_id);
CREATE INDEX idx_bookings_event_id ON bookings (event_id);
CREATE INDEX idx_bookings_idempotency_key ON bookings (idempotency_key);

