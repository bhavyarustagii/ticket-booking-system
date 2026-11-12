# Concurrent Event Ticket Booking System

A high-concurrency ticket booking engine designed to handle flash-sale ticket demand with zero over-selling, data consistency, and distributed lock management.

## Tech Stack
- **Language**: Java 21
- **Framework**: Spring Boot 3.3.5 (Spring MVC, Spring Data JPA, Spring Security, Spring AMQP)
- **Database**: PostgreSQL 16
- **Migrations**: Flyway
- **Caching & Distributed Locks**: Redis 7
- **Message Broker**: RabbitMQ 3.13
- **Containerization**: Docker & Docker Compose
- **Testing**: JUnit 5, AssertJ, MockMvc, Testcontainers

## Architecture
The application adheres to a clean layered architecture:
```
src/main/java/com/ticket/system/
├── config/        # Configuration beans (Security, Redis, RabbitMQ)
├── controller/    # REST API endpoints & HTTP request handling
├── service/       # Business logic and transactional boundaries
├── repository/    # Spring Data JPA repositories
├── dto/           # Request and response models (Entities never exposed)
├── entity/        # JPA domain models
└── exception/     # Domain exceptions and @RestControllerAdvice handler
```

---

## Getting Started Locally

### Prerequisites
- **Java 21+** (JDK 21 or higher)
- **Docker & Docker Compose** (for PostgreSQL, Redis, RabbitMQ)

### 1. Start Infrastructure Services
Start PostgreSQL, Redis, and RabbitMQ using Docker Compose:
```bash
docker compose up -d
```

Service endpoints:
- **PostgreSQL**: `localhost:5432` (`ticket_db`, user: `ticket_user`, pass: `ticket_pass`)
- **Redis**: `localhost:6379`
- **RabbitMQ AMQP**: `localhost:5672`
- **RabbitMQ Management UI**: `http://localhost:15672` (user: `guest`, pass: `guest`)

### 2. Environment Variables (Optional)
The system uses environment variables with sensible local defaults matching the Docker Compose configuration. To customize, copy `.env.example`:
```bash
cp .env.example .env
```

### 3. Build & Run the Application
Using the Maven wrapper:

**Linux / macOS:**
```bash
./mvnw clean spring-boot:run
```

**Windows (PowerShell / CMD):**
```powershell
.\mvnw.cmd clean spring-boot:run
```

The application starts on port `8080` by default.

### 4. Verify Health Status
Check that the application and its health probes are healthy:
```bash
curl http://localhost:8080/actuator/health
```

Expected response:
```json
{
  "status": "UP"
}
```

---

## Running Tests
Execute the unit and integration test suite:

**Linux / macOS:**
```bash
./mvnw clean test
```

**Windows:**
```powershell
.\mvnw.cmd clean test
```

