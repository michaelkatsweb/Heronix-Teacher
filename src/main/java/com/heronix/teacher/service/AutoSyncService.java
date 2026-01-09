package com.heronix.teacher.service;

import com.heronix.teacher.model.domain.Assignment;
import com.heronix.teacher.model.domain.AssignmentCategory;
import com.heronix.teacher.model.domain.Attendance;
import com.heronix.teacher.model.domain.Grade;
import com.heronix.teacher.model.domain.HallPass;
import com.heronix.teacher.model.domain.Student;
import com.heronix.teacher.repository.AssignmentCategoryRepository;
import com.heronix.teacher.repository.AssignmentRepository;
import com.heronix.teacher.repository.AttendanceRepository;
import com.heronix.teacher.repository.GradeRepository;
import com.heronix.teacher.repository.HallPassRepository;
import com.heronix.teacher.repository.StudentRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Automatic sync service
 *
 * Syncs local H2 database to EduScheduler-Pro main server
 *
 * Features:
 * - Configurable sync interval (default: 15 seconds)
 * - Automatic retry on failure
 * - Network disruption handling
 * - Power failure protection (data saved locally first)
 * - Batch sync for efficiency
 *
 * Architecture:
 * 1. Teacher enters data → Saved to local H2 database
 * 2. Auto-sync every 15s → Sends pending changes to main server
 * 3. On network failure → Continues storing locally
 * 4. On network recovery → Auto-resumes sync
 *
 * @author EduScheduler Team
 * @version 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AutoSyncService {

    private final StudentRepository studentRepository;
    private final AssignmentRepository assignmentRepository;
    private final AssignmentCategoryRepository categoryRepository;
    private final GradeRepository gradeRepository;
    private final AttendanceRepository attendanceRepository;
    private final HallPassRepository hallPassRepository;
    private final GradebookService gradebookService;
    private final AttendanceService attendanceService;
    private final HallPassService hallPassService;
    private final NetworkMonitorService networkMonitor;
    private final ObjectMapper objectMapper;

    @Value("${sync.enabled:true}")
    private boolean syncEnabled;

    @Value("${sync.interval.seconds:15}")
    private int syncIntervalSeconds;

    @Value("${sync.admin-server.url}")
    private String adminServerUrl;

    @Value("${sync.admin-server.api-path:/api/teacher-sync}")
    private String syncApiPath;

    @Value("${sync.batch-size:100}")
    private int batchSize;

    @Value("${sync.retry.max-attempts:3}")
    private int maxRetryAttempts;

    private ScheduledExecutorService scheduler;
    private CloseableHttpClient httpClient;
    private boolean isRunning = false;
    private long lastSyncTime = 0;
    private long totalSyncedItems = 0;
    private long failedSyncAttempts = 0;

    /**
     * Initialize auto-sync service
     */
    @PostConstruct
    public void initialize() {
        if (!syncEnabled) {
            log.info("Auto-sync is disabled in configuration");
            return;
        }

        log.info("Initializing Auto-Sync Service");
        log.info("Sync interval: {} seconds", syncIntervalSeconds);
        log.info("Admin server: {}", adminServerUrl);
        log.info("Batch size: {}", batchSize);

        httpClient = HttpClients.createDefault();
        scheduler = Executors.newScheduledThreadPool(1);

        // Schedule periodic sync
        scheduler.scheduleAtFixedRate(
            this::performSync,
            syncIntervalSeconds, // Initial delay
            syncIntervalSeconds, // Period
            TimeUnit.SECONDS
        );

        isRunning = true;
        log.info("Auto-Sync Service started successfully");
    }

    /**
     * Perform sync operation
     */
    public void performSync() {
        if (!syncEnabled || !isRunning) {
            return;
        }

        try {
            // Check network availability
            if (!networkMonitor.isNetworkAvailable()) {
                log.debug("Network unavailable - skipping sync (data safely stored locally)");
                return;
            }

            log.debug("Starting sync operation...");

            // Sync students
            int studentsSynced = syncStudents();

            // Sync assignment categories (must sync before assignments)
            int categoriesSynced = syncCategories();

            // Sync assignments
            int assignmentsSynced = syncAssignments();

            // Sync grades
            int gradesSynced = syncGrades();

            // Sync attendance
            int attendanceSynced = syncAttendance();

            // Sync hall passes
            int hallPassesSynced = syncHallPasses();

            int totalSynced = studentsSynced + categoriesSynced + assignmentsSynced + gradesSynced + attendanceSynced + hallPassesSynced;

            if (totalSynced > 0) {
                log.info("Sync completed: {} students, {} categories, {} assignments, {} grades, {} attendance, {} hall passes → Total: {} items",
                         studentsSynced, categoriesSynced, assignmentsSynced, gradesSynced, attendanceSynced, hallPassesSynced, totalSynced);
                totalSyncedItems += totalSynced;
            }

            lastSyncTime = System.currentTimeMillis();

        } catch (Exception e) {
            failedSyncAttempts++;
            log.error("Sync failed (attempt {}): {} - Data remains safely stored locally",
                     failedSyncAttempts, e.getMessage());
        }
    }

    /**
     * Sync students
     */
    @Transactional
    private int syncStudents() {
        List<Student> pendingStudents = studentRepository.findNeedingSync();

        if (pendingStudents.isEmpty()) {
            return 0;
        }

        log.debug("Syncing {} students...", pendingStudents.size());

        int synced = 0;
        for (Student student : pendingStudents) {
            try {
                if (sendToServer("students", student)) {
                    gradebookService.markStudentSynced(student.getId());
                    synced++;
                }
            } catch (Exception e) {
                log.error("Failed to sync student {}: {}", student.getStudentId(), e.getMessage());
            }
        }

        return synced;
    }

    /**
     * Sync assignment categories
     */
    @Transactional
    private int syncCategories() {
        List<AssignmentCategory> pendingCategories = categoryRepository.findNeedingSync();

        if (pendingCategories.isEmpty()) {
            return 0;
        }

        log.debug("Syncing {} assignment categories...", pendingCategories.size());

        int synced = 0;
        for (AssignmentCategory category : pendingCategories) {
            try {
                if (sendToServer("categories", category)) {
                    gradebookService.markCategorySynced(category.getId());
                    synced++;
                }
            } catch (Exception e) {
                log.error("Failed to sync category {}: {}", category.getName(), e.getMessage());
            }
        }

        return synced;
    }

    /**
     * Sync assignments
     */
    @Transactional
    private int syncAssignments() {
        List<Assignment> pendingAssignments = assignmentRepository.findNeedingSync();

        if (pendingAssignments.isEmpty()) {
            return 0;
        }

        log.debug("Syncing {} assignments...", pendingAssignments.size());

        int synced = 0;
        for (Assignment assignment : pendingAssignments) {
            try {
                if (sendToServer("assignments", assignment)) {
                    gradebookService.markAssignmentSynced(assignment.getId());
                    synced++;
                }
            } catch (Exception e) {
                log.error("Failed to sync assignment {}: {}", assignment.getName(), e.getMessage());
            }
        }

        return synced;
    }

    /**
     * Sync grades
     */
    @Transactional
    private int syncGrades() {
        List<Grade> pendingGrades = gradeRepository.findNeedingSync();

        if (pendingGrades.isEmpty()) {
            return 0;
        }

        log.debug("Syncing {} grades...", pendingGrades.size());

        int synced = 0;
        for (Grade grade : pendingGrades) {
            try {
                if (sendToServer("grades", grade)) {
                    gradebookService.markGradeSynced(grade.getId());
                    synced++;
                }
            } catch (Exception e) {
                log.error("Failed to sync grade: {}", e.getMessage());
            }
        }

        return synced;
    }

    /**
     * Sync attendance
     */
    @Transactional
    private int syncAttendance() {
        List<Attendance> pendingAttendance = attendanceRepository.findNeedingSync();

        if (pendingAttendance.isEmpty()) {
            return 0;
        }

        log.debug("Syncing {} attendance records...", pendingAttendance.size());

        int synced = 0;
        for (Attendance attendance : pendingAttendance) {
            try {
                if (sendToServer("attendance", attendance)) {
                    attendanceService.markAttendanceSynced(attendance.getId());
                    synced++;
                }
            } catch (Exception e) {
                log.error("Failed to sync attendance: {}", e.getMessage());
            }
        }

        return synced;
    }

    /**
     * Sync hall passes
     */
    @Transactional
    private int syncHallPasses() {
        List<HallPass> pendingHallPasses = hallPassRepository.findNeedingSync();

        if (pendingHallPasses.isEmpty()) {
            return 0;
        }

        log.debug("Syncing {} hall passes...", pendingHallPasses.size());

        int synced = 0;
        for (HallPass hallPass : pendingHallPasses) {
            try {
                if (sendToServer("hallpasses", hallPass)) {
                    hallPassService.markPassSynced(hallPass.getId());
                    synced++;
                }
            } catch (Exception e) {
                log.error("Failed to sync hall pass: {}", e.getMessage());
            }
        }

        return synced;
    }

    /**
     * Send entity to main server
     */
    private boolean sendToServer(String entityType, Object entity) {
        try {
            String url = adminServerUrl + syncApiPath + "/" + entityType;
            HttpPost request = new HttpPost(url);

            String json = objectMapper.writeValueAsString(entity);
            request.setEntity(new StringEntity(json, ContentType.APPLICATION_JSON));

            try (CloseableHttpResponse response = httpClient.execute(request)) {
                int statusCode = response.getCode();
                if (statusCode >= 200 && statusCode < 300) {
                    return true;
                } else {
                    log.warn("Server returned status {}: {}", statusCode, response.getReasonPhrase());
                    return false;
                }
            }

        } catch (Exception e) {
            log.error("Failed to send {} to server: {}", entityType, e.getMessage());
            return false;
        }
    }

    /**
     * Trigger immediate sync (manual sync)
     */
    public void syncNow() {
        log.info("Manual sync triggered");
        performSync();
    }

    /**
     * Get sync statistics
     */
    public Map<String, Object> getSyncStatistics() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("enabled", syncEnabled);
        stats.put("running", isRunning);
        stats.put("lastSyncTime", lastSyncTime);
        stats.put("totalSyncedItems", totalSyncedItems);
        stats.put("failedAttempts", failedSyncAttempts);
        stats.put("networkAvailable", networkMonitor.getLastKnownStatus());
        stats.put("pendingStudents", studentRepository.findNeedingSync().size());
        stats.put("pendingCategories", categoryRepository.findNeedingSync().size());
        stats.put("pendingAssignments", assignmentRepository.findNeedingSync().size());
        stats.put("pendingGrades", gradeRepository.findNeedingSync().size());
        stats.put("pendingAttendance", attendanceRepository.findNeedingSync().size());
        stats.put("pendingHallPasses", hallPassRepository.findNeedingSync().size());
        return stats;
    }

    /**
     * Get total pending items
     */
    public long getPendingItemsCount() {
        return gradebookService.countItemsNeedingSync();
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
     * Shutdown auto-sync service
     */
    @PreDestroy
    public void shutdown() {
        log.info("Shutting down Auto-Sync Service");

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

        if (httpClient != null) {
            try {
                httpClient.close();
            } catch (Exception e) {
                log.error("Error closing HTTP client", e);
            }
        }

        log.info("Auto-Sync Service stopped. Total synced: {} items", totalSyncedItems);
    }
}
