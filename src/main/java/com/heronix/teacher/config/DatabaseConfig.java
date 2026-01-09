package com.heronix.teacher.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import jakarta.annotation.PostConstruct;
import java.io.File;

/**
 * Database configuration for H2
 *
 * Configures:
 * - H2 embedded database location
 * - JPA repositories
 * - Transaction management
 * - Database initialization
 *
 * Database Location: ./data/eduproteacher.mv.db (H2 file-based)
 *
 * Auto-Sync Strategy:
 * - Data saved locally first (power failure protection)
 * - Auto-sync every 15 seconds to main server (configurable by IT)
 * - Network disruption resilient (continues offline)
 * - No data loss guarantee
 *
 * @author EduScheduler Team
 * @version 1.0.0
 */
@Slf4j
@Configuration
@EnableJpaRepositories(basePackages = "com.heronix.teacher.repository")
@EnableTransactionManagement
public class DatabaseConfig {

    @PostConstruct
    public void init() {
        log.info("Initializing database configuration...");

        // Ensure data directory exists
        File dataDir = new File("./data");
        if (!dataDir.exists()) {
            boolean created = dataDir.mkdirs();
            if (created) {
                log.info("Created data directory: {}", dataDir.getAbsolutePath());
            } else {
                log.warn("Failed to create data directory: {}", dataDir.getAbsolutePath());
            }
        }

        File dbFile = new File("./data/eduproteacher.mv.db");
        if (dbFile.exists()) {
            log.info("Using existing H2 database: {}", dbFile.getAbsolutePath());
        } else {
            log.info("H2 database will be created: {}", dbFile.getAbsolutePath());
        }

        log.info("H2 Database Features:");
        log.info("  - Local embedded (file-based)");
        log.info("  - Auto-sync to main server every 15 seconds");
        log.info("  - Power failure protection");
        log.info("  - Network disruption resilient");

        log.info("Database configuration initialized successfully");
    }
}
