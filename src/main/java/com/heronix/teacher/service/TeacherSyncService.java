package com.heronix.teacher.service;

import com.heronix.teacher.model.domain.Teacher;
import com.heronix.teacher.repository.TeacherRepository;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Teacher Sync Service
 *
 * Syncs teacher data FROM EduScheduler-Pro admin database TO local H2 database
 * This allows teachers to login to Heronix-Teacher portal with credentials
 * created in the admin system.
 *
 * Direction: Admin DB → Local DB (Pull sync)
 *
 * Features:
 * - Automatic periodic sync (default: every 5 minutes)
 * - Preserves encrypted passwords from admin system
 * - Only syncs teachers with passwords (ready to login)
 * - Prevents duplicate teachers
 * - Updates existing teacher information
 * - Configurable sync interval
 *
 * @author EduScheduler Team
 * @version 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TeacherSyncService {

    private final TeacherRepository teacherRepository;

    @Value("${teacher.sync.enabled:true}")
    private boolean syncEnabled;

    @Value("${teacher.sync.interval.seconds:300}") // Default: 5 minutes
    private int syncIntervalSeconds;

    @Value("${teacher.sync.admin-db.url:}")
    private String adminDbUrl;

    @Value("${teacher.sync.admin-db.username:}")
    private String adminDbUsername;

    @Value("${teacher.sync.admin-db.password:}")
    private String adminDbPassword;

    @Value("${teacher.sync.admin-db.driver:com.mysql.cj.jdbc.Driver}")
    private String adminDbDriver;

    private ScheduledExecutorService scheduler;
    private JdbcTemplate adminJdbcTemplate;
    private boolean isRunning = false;
    private long lastSyncTime = 0;
    private int totalTeachersSynced = 0;
    private int failedSyncAttempts = 0;

    /**
     * Initialize teacher sync service
     */
    @PostConstruct
    public void initialize() {
        if (!syncEnabled) {
            log.info("Teacher sync is disabled in configuration");
            return;
        }

        if (adminDbUrl == null || adminDbUrl.trim().isEmpty()) {
            log.warn("Teacher sync enabled but admin database URL not configured");
            log.warn("Set property: teacher.sync.admin-db.url=jdbc:mysql://localhost:3306/eduscheduler");
            return;
        }

        log.info("Initializing Teacher Sync Service");
        log.info("Sync interval: {} seconds ({} minutes)", syncIntervalSeconds, syncIntervalSeconds / 60);
        log.info("Admin database: {}", adminDbUrl);

        try {
            // Create data source for admin database
            DriverManagerDataSource dataSource = new DriverManagerDataSource();
            dataSource.setDriverClassName(adminDbDriver);
            dataSource.setUrl(adminDbUrl);
            dataSource.setUsername(adminDbUsername);
            dataSource.setPassword(adminDbPassword);

            adminJdbcTemplate = new JdbcTemplate(dataSource);

            // Test connection
            adminJdbcTemplate.queryForObject("SELECT 1", Integer.class);
            log.info("Successfully connected to admin database");

            // Schedule periodic sync
            scheduler = Executors.newScheduledThreadPool(1);
            scheduler.scheduleAtFixedRate(
                this::performSync,
                10, // Initial delay: 10 seconds
                syncIntervalSeconds, // Period
                TimeUnit.SECONDS
            );

            isRunning = true;
            log.info("Teacher Sync Service started successfully");

        } catch (Exception e) {
            log.error("Failed to initialize Teacher Sync Service: {}", e.getMessage());
            log.error("Teachers will not be synced automatically from admin system");
        }
    }

    /**
     * Perform sync operation - pull teachers from admin database
     */
    public void performSync() {
        if (!syncEnabled || !isRunning) {
            return;
        }

        try {
            log.debug("Starting teacher sync from admin database...");

            int synced = syncTeachersFromAdmin();

            if (synced > 0) {
                log.info("Teacher sync completed: {} teachers synced", synced);
                totalTeachersSynced += synced;
            } else {
                log.debug("Teacher sync completed: No new or updated teachers");
            }

            lastSyncTime = System.currentTimeMillis();
            failedSyncAttempts = 0; // Reset on success

        } catch (Exception e) {
            failedSyncAttempts++;
            log.error("Teacher sync failed (attempt {}): {}", failedSyncAttempts, e.getMessage());

            if (failedSyncAttempts >= 3) {
                log.error("Multiple teacher sync failures. Check admin database connection.");
            }
        }
    }

    /**
     * Sync teachers from admin database
     * Only syncs teachers with passwords set (ready to login)
     */
    @Transactional
    private int syncTeachersFromAdmin() {
        if (adminJdbcTemplate == null) {
            log.warn("Admin JDBC template not initialized - skipping sync");
            return 0;
        }

        try {
            // Query admin database for teachers with passwords
            String sql = "SELECT id, employee_id, first_name, last_name, email, password, " +
                        "password_expires_at, must_change_password, password_changed_at, " +
                        "department, phone_number, active " +
                        "FROM teachers " +
                        "WHERE password IS NOT NULL AND password != '' " +
                        "AND active = true";

            List<Map<String, Object>> adminTeachers = adminJdbcTemplate.queryForList(sql);

            log.debug("Found {} teachers with passwords in admin database", adminTeachers.size());

            int synced = 0;

            for (Map<String, Object> row : adminTeachers) {
                try {
                    String employeeId = (String) row.get("employee_id");

                    if (employeeId == null || employeeId.trim().isEmpty()) {
                        log.warn("Skipping teacher with null/empty employee_id: {}", row.get("id"));
                        continue;
                    }

                    // Check if teacher already exists locally
                    Teacher localTeacher = teacherRepository.findByEmployeeId(employeeId).orElse(null);

                    if (localTeacher == null) {
                        // Create new teacher
                        localTeacher = createTeacherFromAdminData(row);
                        teacherRepository.save(localTeacher);
                        synced++;
                        log.info("Synced new teacher: {} {} ({})",
                                row.get("first_name"), row.get("last_name"), employeeId);

                    } else {
                        // Update existing teacher
                        boolean updated = updateTeacherFromAdminData(localTeacher, row);
                        if (updated) {
                            teacherRepository.save(localTeacher);
                            synced++;
                            log.debug("Updated existing teacher: {}", employeeId);
                        }
                    }

                } catch (Exception e) {
                    log.error("Failed to sync teacher: {}", e.getMessage());
                }
            }

            return synced;

        } catch (Exception e) {
            log.error("Error querying admin database: {}", e.getMessage());
            throw e;
        }
    }

    /**
     * Create new teacher from admin database data
     */
    private Teacher createTeacherFromAdminData(Map<String, Object> row) {
        Teacher teacher = new Teacher();

        teacher.setEmployeeId((String) row.get("employee_id"));
        teacher.setFirstName((String) row.get("first_name"));
        teacher.setLastName((String) row.get("last_name"));
        teacher.setEmail((String) row.get("email"));

        // IMPORTANT: Preserve encrypted password from admin system
        teacher.setPassword((String) row.get("password"));

        // Copy password expiration fields (new in this session)
        if (row.get("password_expires_at") != null) {
            teacher.setPasswordExpiresAt(convertToLocalDateTime(row.get("password_expires_at")));
        }

        if (row.get("must_change_password") != null) {
            teacher.setMustChangePassword(convertToBoolean(row.get("must_change_password")));
        }

        if (row.get("password_changed_at") != null) {
            teacher.setPasswordChangedAt(convertToLocalDateTime(row.get("password_changed_at")));
        }

        teacher.setDepartment((String) row.get("department"));
        teacher.setPhoneNumber((String) row.get("phone_number"));

        if (row.get("active") != null) {
            teacher.setActive(convertToBoolean(row.get("active")));
        } else {
            teacher.setActive(true); // Default to active
        }

        teacher.setSyncStatus("synced");
        teacher.setCreatedDate(LocalDateTime.now());
        teacher.setModifiedDate(LocalDateTime.now());

        return teacher;
    }

    /**
     * Update existing teacher with admin database data
     * Returns true if any changes were made
     */
    private boolean updateTeacherFromAdminData(Teacher teacher, Map<String, Object> row) {
        boolean updated = false;

        // Update basic fields
        if (!equals(teacher.getFirstName(), row.get("first_name"))) {
            teacher.setFirstName((String) row.get("first_name"));
            updated = true;
        }

        if (!equals(teacher.getLastName(), row.get("last_name"))) {
            teacher.setLastName((String) row.get("last_name"));
            updated = true;
        }

        if (!equals(teacher.getEmail(), row.get("email"))) {
            teacher.setEmail((String) row.get("email"));
            updated = true;
        }

        // Update password if changed in admin system
        String adminPassword = (String) row.get("password");
        if (!equals(teacher.getPassword(), adminPassword)) {
            teacher.setPassword(adminPassword);
            updated = true;
        }

        // Update password expiration fields
        LocalDateTime adminPasswordExpiresAt = convertToLocalDateTime(row.get("password_expires_at"));
        if (!equals(teacher.getPasswordExpiresAt(), adminPasswordExpiresAt)) {
            teacher.setPasswordExpiresAt(adminPasswordExpiresAt);
            updated = true;
        }

        Boolean adminMustChangePassword = convertToBoolean(row.get("must_change_password"));
        if (!equals(teacher.getMustChangePassword(), adminMustChangePassword)) {
            teacher.setMustChangePassword(adminMustChangePassword);
            updated = true;
        }

        LocalDateTime adminPasswordChangedAt = convertToLocalDateTime(row.get("password_changed_at"));
        if (!equals(teacher.getPasswordChangedAt(), adminPasswordChangedAt)) {
            teacher.setPasswordChangedAt(adminPasswordChangedAt);
            updated = true;
        }

        if (!equals(teacher.getDepartment(), row.get("department"))) {
            teacher.setDepartment((String) row.get("department"));
            updated = true;
        }

        if (!equals(teacher.getPhoneNumber(), row.get("phone_number"))) {
            teacher.setPhoneNumber((String) row.get("phone_number"));
            updated = true;
        }

        Boolean adminActive = convertToBoolean(row.get("active"));
        if (!equals(teacher.getActive(), adminActive)) {
            teacher.setActive(adminActive != null ? adminActive : true);
            updated = true;
        }

        if (updated) {
            teacher.setModifiedDate(LocalDateTime.now());
            teacher.setSyncStatus("synced");
        }

        return updated;
    }

    /**
     * Convert database value to LocalDateTime
     */
    private LocalDateTime convertToLocalDateTime(Object value) {
        if (value == null) {
            return null;
        }

        if (value instanceof LocalDateTime) {
            return (LocalDateTime) value;
        }

        if (value instanceof java.sql.Timestamp) {
            return ((java.sql.Timestamp) value).toLocalDateTime();
        }

        if (value instanceof java.sql.Date) {
            return ((java.sql.Date) value).toLocalDate().atStartOfDay();
        }

        return null;
    }

    /**
     * Convert database value to Boolean
     */
    private Boolean convertToBoolean(Object value) {
        if (value == null) {
            return null;
        }

        if (value instanceof Boolean) {
            return (Boolean) value;
        }

        if (value instanceof Number) {
            return ((Number) value).intValue() != 0;
        }

        if (value instanceof String) {
            String str = ((String) value).toLowerCase();
            return str.equals("true") || str.equals("1") || str.equals("yes");
        }

        return null;
    }

    /**
     * Safe equals comparison (handles nulls)
     */
    private boolean equals(Object a, Object b) {
        if (a == null && b == null) return true;
        if (a == null || b == null) return false;
        return a.equals(b);
    }

    /**
     * Trigger immediate sync (manual sync)
     */
    public void syncNow() {
        log.info("Manual teacher sync triggered");
        performSync();
    }

    /**
     * Get sync statistics
     */
    public Map<String, Object> getSyncStatistics() {
        Map<String, Object> stats = new java.util.HashMap<>();
        stats.put("enabled", syncEnabled);
        stats.put("running", isRunning);
        stats.put("lastSyncTime", lastSyncTime);
        stats.put("totalTeachersSynced", totalTeachersSynced);
        stats.put("failedAttempts", failedSyncAttempts);
        stats.put("localTeachersCount", teacherRepository.count());
        stats.put("activeTeachersCount", teacherRepository.findByActiveTrue().size());

        if (adminJdbcTemplate != null) {
            try {
                Integer adminCount = adminJdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM teachers WHERE password IS NOT NULL AND password != ''",
                    Integer.class
                );
                stats.put("adminTeachersCount", adminCount);
            } catch (Exception e) {
                stats.put("adminTeachersCount", "Error: " + e.getMessage());
            }
        }

        return stats;
    }

    /**
     * Check if sync is currently running
     */
    public boolean isRunning() {
        return isRunning;
    }

    /**
     * Get last sync time
     */
    public long getLastSyncTime() {
        return lastSyncTime;
    }

    /**
     * Get total teachers synced
     */
    public int getTotalTeachersSynced() {
        return totalTeachersSynced;
    }

    /**
     * Shutdown teacher sync service
     */
    @PreDestroy
    public void shutdown() {
        log.info("Shutting down Teacher Sync Service");

        isRunning = false;

        if (scheduler != null) {
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

        log.info("Teacher Sync Service stopped. Total synced: {} teachers", totalTeachersSynced);
    }
}
