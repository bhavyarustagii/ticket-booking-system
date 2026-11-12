package com.ticket.system;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class TicketBookingApplicationTests {

    @Test
    @DisplayName("Verify Spring application context loads successfully")
    void contextLoads() {
        // Confirms all beans and configuration load without errors
    }
}

