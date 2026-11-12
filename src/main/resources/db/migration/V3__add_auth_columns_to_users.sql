-- V3: Add authentication and authorization columns to users table
ALTER TABLE users
    ADD COLUMN password_hash VARCHAR(255) NOT NULL DEFAULT '',
    ADD COLUMN role VARCHAR(50) NOT NULL DEFAULT 'USER';

-- Remove default on password_hash to require explicit hashed credentials for future inserts
ALTER TABLE users ALTER COLUMN password_hash DROP DEFAULT;

-- Enforce valid roles: USER and ADMIN
ALTER TABLE users
    ADD CONSTRAINT chk_user_role CHECK (role IN ('USER', 'ADMIN'));

