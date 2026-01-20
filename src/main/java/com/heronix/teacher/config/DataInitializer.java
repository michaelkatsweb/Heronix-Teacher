package com.heronix.teacher.config;

import com.heronix.teacher.service.AuthenticationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Data Initializer
 *
 * Creates test teacher accounts for development and testing
 * Only runs if teachers table is empty
 *
 * @author Heronix-Teacher Team
 * @version 1.0.0
 */
@Configuration
@Slf4j
@RequiredArgsConstructor
public class DataInitializer {

    private final AuthenticationService authenticationService;

    /**
     * Initialize test teacher accounts
     *
     * Creates 5 sample teachers with different departments
     * All teachers have password: "password123"
     *
     * @return CommandLineRunner bean
     */
    @Bean
    public CommandLineRunner initializeTestData() {
        return args -> {
            log.info("Checking if test data initialization is needed...");

            try {
                // Create admin test account (matches Heronix-Talk admin)
                createTestTeacher("admin", "System", "Administrator", "admin@heronix.local",
                                "admin123", "Administration");

                // Create test teachers
                createTestTeacher("T001", "John", "Smith", "john.smith@school.edu",
                                "password123", "Mathematics");

                createTestTeacher("T002", "Sarah", "Johnson", "sarah.johnson@school.edu",
                                "password123", "English");

                createTestTeacher("T003", "Michael", "Davis", "michael.davis@school.edu",
                                "password123", "Science");

                createTestTeacher("T004", "Emily", "Wilson", "emily.wilson@school.edu",
                                "password123", "History");

                createTestTeacher("T005", "David", "Martinez", "david.martinez@school.edu",
                                "password123", "Physical Education");

                log.info("Test data initialization completed successfully");

            } catch (Exception e) {
                log.error("Error during test data initialization", e);
            }
        };
    }

    /**
     * Create a test teacher account (only if it doesn't exist)
     */
    private void createTestTeacher(String employeeId, String firstName, String lastName,
                                  String email, String password, String department) {
        try {
            authenticationService.createTeacher(
                employeeId, firstName, lastName, email, password, department
            );
            log.info("Created test teacher: {} {} ({})", firstName, lastName, employeeId);
        } catch (IllegalArgumentException e) {
            // Teacher already exists, skip
            log.debug("Teacher {} already exists, skipping", employeeId);
        } catch (Exception e) {
            log.error("Failed to create teacher {}: {}", employeeId, e.getMessage());
        }
    }
}
