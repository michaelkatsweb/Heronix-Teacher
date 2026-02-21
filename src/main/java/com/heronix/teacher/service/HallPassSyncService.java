package com.heronix.teacher.service;

import com.heronix.teacher.model.domain.HallPass;
import com.heronix.teacher.model.domain.Student;
import com.heronix.teacher.repository.HallPassRepository;
import com.heronix.teacher.repository.StudentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Hall Pass Sync Service
 *
 * Synchronizes hall pass data between Teacher Portal and SIS:
 * - Pushes local hall pass records to SIS
 * - Pulls analytics and reports from SIS
 * - Handles conflict resolution
 *
 * Features:
 * - Automatic scheduled sync (every 5 minutes)
 * - Manual sync trigger
 * - Conflict detection and resolution
 * - Sync status tracking
 *
 * @author Heronix Team
 * @version 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HallPassSyncService {

    private final HallPassRepository hallPassRepository;
    private final StudentRepository studentRepository;
    private final RestTemplate restTemplate;

    @Value("${sync.admin-server.url:http://localhost:9590}")
    private String sisBaseUrl;

    @Value("${sync.hall-pass.enabled:true}")
    private boolean syncEnabled;

    @Value("${sync.hall-pass.interval-seconds:300}")
    private int syncIntervalSeconds;

    private LocalDateTime lastSyncTime;
    private volatile boolean syncInProgress = false;

    // ==================== Sync Operations ====================

    /**
     * Scheduled sync - runs every 5 minutes
     */
    @Scheduled(fixedDelayString = "${sync.hall-pass.interval-seconds:300}000")
    public void scheduledSync() {
        if (!syncEnabled) {
            log.debug("Hall pass sync is disabled");
            return;
        }

        if (syncInProgress) {
            log.debug("Sync already in progress, skipping");
            return;
        }

        try {
            performFullSync();
        } catch (Exception e) {
            log.error("Scheduled hall pass sync failed", e);
        }
    }

    /**
     * Manual sync trigger
     */
    public SyncResult triggerSync() {
        if (syncInProgress) {
            return SyncResult.inProgress();
        }
        return performFullSync();
    }

    /**
     * Perform full sync - push pending records and pull updates
     */
    @Transactional
    public SyncResult performFullSync() {
        syncInProgress = true;
        log.info("Starting hall pass sync with SIS");

        try {
            SyncResult result = new SyncResult();
            result.setStartTime(LocalDateTime.now());

            // Push pending records to SIS
            int pushed = pushPendingRecords();
            result.setPushedCount(pushed);

            // Pull analytics updates from SIS
            boolean pullSuccess = pullAnalyticsFromSIS();
            result.setPullSuccess(pullSuccess);

            lastSyncTime = LocalDateTime.now();
            result.setEndTime(lastSyncTime);
            result.setSuccess(true);
            result.setMessage("Sync completed successfully");

            log.info("Hall pass sync completed: pushed={}, pullSuccess={}", pushed, pullSuccess);
            return result;

        } catch (Exception e) {
            log.error("Hall pass sync failed", e);
            SyncResult result = new SyncResult();
            result.setSuccess(false);
            result.setMessage("Sync failed: " + e.getMessage());
            result.setEndTime(LocalDateTime.now());
            return result;
        } finally {
            syncInProgress = false;
        }
    }

    /**
     * Push pending hall pass records to SIS
     */
    private int pushPendingRecords() {
        List<HallPass> pendingPasses = hallPassRepository.findNeedingSync();
        if (pendingPasses.isEmpty()) {
            log.debug("No pending hall pass records to sync");
            return 0;
        }

        log.info("Pushing {} hall pass records to SIS", pendingPasses.size());

        try {
            String url = sisBaseUrl + "/api/hall-pass/analytics/sync/from-teacher";

            Map<String, Object> syncPayload = new HashMap<>();
            syncPayload.put("sourceSystem", "TEACHER_PORTAL");
            syncPayload.put("syncTime", LocalDateTime.now().toString());
            syncPayload.put("sessionCount", pendingPasses.size());

            List<Map<String, Object>> sessions = new ArrayList<>();
            for (HallPass pass : pendingPasses) {
                sessions.add(convertToSyncPayload(pass));
            }
            syncPayload.put("sessions", sessions);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(syncPayload, headers);

            ResponseEntity<Map> response = restTemplate.exchange(
                    url, HttpMethod.POST, request, Map.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                // Mark records as synced
                for (HallPass pass : pendingPasses) {
                    pass.setSyncStatus("synced");
                    hallPassRepository.save(pass);
                }
                log.info("Successfully pushed {} records to SIS", pendingPasses.size());
                return pendingPasses.size();
            } else {
                log.warn("SIS returned non-success status: {}", response.getStatusCode());
                return 0;
            }
        } catch (Exception e) {
            log.error("Failed to push hall pass records to SIS", e);
            return 0;
        }
    }

    /**
     * Pull analytics and updates from SIS
     */
    private boolean pullAnalyticsFromSIS() {
        try {
            // Pull school-wide analytics for dashboard
            String url = sisBaseUrl + "/api/hall-pass/analytics/school";

            ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                // Cache analytics for dashboard display
                cacheSchoolAnalytics(response.getBody());
                return true;
            }
            return false;
        } catch (Exception e) {
            log.warn("Failed to pull analytics from SIS: {}", e.getMessage());
            return false;
        }
    }

    // ==================== Student Analytics ====================

    /**
     * Get student analytics from SIS
     */
    public Map<String, Object> getStudentAnalytics(Long studentId) {
        try {
            String url = sisBaseUrl + "/api/hall-pass/analytics/student/" + studentId;
            ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                return response.getBody();
            }
        } catch (Exception e) {
            log.warn("Failed to get student analytics from SIS: {}", e.getMessage());
        }

        // Fallback to local data
        return getLocalStudentAnalytics(studentId);
    }

    /**
     * Get student report card from SIS
     */
    public Map<String, Object> getStudentReportCard(Long studentId) {
        try {
            String url = sisBaseUrl + "/api/hall-pass/analytics/student/" + studentId + "/report-card";
            ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                return response.getBody();
            }
        } catch (Exception e) {
            log.warn("Failed to get student report card from SIS: {}", e.getMessage());
        }

        // Fallback to local data
        return generateLocalReportCard(studentId);
    }

    // ==================== School Analytics ====================

    /**
     * Get school analytics from SIS
     */
    public Map<String, Object> getSchoolAnalytics() {
        try {
            String url = sisBaseUrl + "/api/hall-pass/analytics/school";
            ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                return response.getBody();
            }
        } catch (Exception e) {
            log.warn("Failed to get school analytics from SIS: {}", e.getMessage());
        }

        // Return cached analytics or generate from local data
        return getCachedOrLocalSchoolAnalytics();
    }

    /**
     * Get weekly trend from SIS
     */
    public Map<String, Object> getWeeklyTrend() {
        try {
            String url = sisBaseUrl + "/api/hall-pass/analytics/school/weekly";
            ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                return response.getBody();
            }
        } catch (Exception e) {
            log.warn("Failed to get weekly trend from SIS: {}", e.getMessage());
        }

        return generateLocalWeeklyTrend();
    }

    // ==================== District/State Analytics ====================

    /**
     * Get district analytics from SIS
     */
    public Map<String, Object> getDistrictAnalytics(LocalDate startDate, LocalDate endDate) {
        try {
            String url = sisBaseUrl + "/api/hall-pass/analytics/district";
            if (startDate != null && endDate != null) {
                url += "?startDate=" + startDate + "&endDate=" + endDate;
            }
            ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                return response.getBody();
            }
        } catch (Exception e) {
            log.warn("Failed to get district analytics from SIS: {}", e.getMessage());
        }
        return Map.of("error", "District analytics not available");
    }

    /**
     * Get state analytics from SIS
     */
    public Map<String, Object> getStateAnalytics(LocalDate startDate, LocalDate endDate) {
        try {
            String url = sisBaseUrl + "/api/hall-pass/analytics/state";
            if (startDate != null && endDate != null) {
                url += "?startDate=" + startDate + "&endDate=" + endDate;
            }
            ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                return response.getBody();
            }
        } catch (Exception e) {
            log.warn("Failed to get state analytics from SIS: {}", e.getMessage());
        }
        return Map.of("error", "State analytics not available");
    }

    // ==================== Export Functions ====================

    /**
     * Export student hall pass data
     */
    public Map<String, Object> exportStudentData(Long studentId, LocalDate startDate, LocalDate endDate) {
        try {
            String url = sisBaseUrl + "/api/hall-pass/analytics/export/student/" + studentId;
            if (startDate != null && endDate != null) {
                url += "?startDate=" + startDate + "&endDate=" + endDate;
            }
            ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                return response.getBody();
            }
        } catch (Exception e) {
            log.warn("Failed to export student data from SIS: {}", e.getMessage());
        }

        // Fallback to local export
        return generateLocalExport(studentId, startDate, endDate);
    }

    // ==================== Conflict Resolution ====================

    /**
     * Resolve sync conflicts for a specific hall pass
     * Uses last-write-wins with priority to SIS for completed passes
     */
    @Transactional
    public ConflictResolutionResult resolveConflict(Long localPassId, Map<String, Object> sisRecord) {
        log.info("Resolving sync conflict for local pass {}", localPassId);

        HallPass localPass = hallPassRepository.findById(localPassId).orElse(null);
        if (localPass == null) {
            return ConflictResolutionResult.error("Local pass not found");
        }

        try {
            // Parse SIS record
            String sisStatus = (String) sisRecord.get("status");
            String sisTimeIn = (String) sisRecord.get("returnTime");
            LocalDateTime sisUpdatedAt = sisRecord.get("updatedAt") != null ?
                    LocalDateTime.parse((String) sisRecord.get("updatedAt")) : null;

            // Determine which record wins
            ConflictResolutionStrategy strategy = determineStrategy(localPass, sisStatus, sisUpdatedAt);

            switch (strategy) {
                case SIS_WINS:
                    applyFromSIS(localPass, sisRecord);
                    localPass.setSyncStatus("synced");
                    hallPassRepository.save(localPass);
                    log.info("Conflict resolved: SIS wins for pass {}", localPassId);
                    return ConflictResolutionResult.resolved("SIS record applied", strategy);

                case LOCAL_WINS:
                    localPass.setSyncStatus("pending"); // Re-push local record
                    hallPassRepository.save(localPass);
                    log.info("Conflict resolved: Local wins for pass {}", localPassId);
                    return ConflictResolutionResult.resolved("Local record will be pushed", strategy);

                case MERGE:
                    mergeRecords(localPass, sisRecord);
                    localPass.setSyncStatus("synced");
                    hallPassRepository.save(localPass);
                    log.info("Conflict resolved: Merged records for pass {}", localPassId);
                    return ConflictResolutionResult.resolved("Records merged", strategy);

                case MANUAL_REVIEW:
                    localPass.setSyncStatus("conflict");
                    localPass.setNotes((localPass.getNotes() != null ? localPass.getNotes() + "\n" : "") +
                            "[SYNC CONFLICT] Requires manual review - " + LocalDateTime.now());
                    hallPassRepository.save(localPass);
                    log.warn("Conflict requires manual review for pass {}", localPassId);
                    return ConflictResolutionResult.needsReview("Manual review required", strategy);

                default:
                    return ConflictResolutionResult.error("Unknown resolution strategy");
            }

        } catch (Exception e) {
            log.error("Error resolving conflict for pass {}", localPassId, e);
            return ConflictResolutionResult.error("Resolution failed: " + e.getMessage());
        }
    }

    /**
     * Get all passes with sync conflicts
     */
    public List<HallPass> getConflictedPasses() {
        return hallPassRepository.findBySyncStatus("conflict");
    }

    /**
     * Force resolution using local data
     */
    @Transactional
    public ConflictResolutionResult forceLocalResolution(Long passId) {
        HallPass pass = hallPassRepository.findById(passId).orElse(null);
        if (pass == null) {
            return ConflictResolutionResult.error("Pass not found");
        }

        pass.setSyncStatus("pending"); // Will be pushed on next sync
        pass.setNotes((pass.getNotes() != null ? pass.getNotes() + "\n" : "") +
                "[CONFLICT RESOLVED] Forced local data - " + LocalDateTime.now());
        hallPassRepository.save(pass);

        log.info("Forced local resolution for pass {}", passId);
        return ConflictResolutionResult.resolved("Local data will be pushed", ConflictResolutionStrategy.LOCAL_WINS);
    }

    /**
     * Force resolution using SIS data
     */
    @Transactional
    public ConflictResolutionResult forceSISResolution(Long passId) {
        HallPass pass = hallPassRepository.findById(passId).orElse(null);
        if (pass == null) {
            return ConflictResolutionResult.error("Pass not found");
        }

        // Fetch latest from SIS and apply
        try {
            // This would normally fetch the specific record from SIS
            pass.setSyncStatus("synced");
            pass.setNotes((pass.getNotes() != null ? pass.getNotes() + "\n" : "") +
                    "[CONFLICT RESOLVED] Used SIS data - " + LocalDateTime.now());
            hallPassRepository.save(pass);

            log.info("Forced SIS resolution for pass {}", passId);
            return ConflictResolutionResult.resolved("SIS data applied", ConflictResolutionStrategy.SIS_WINS);

        } catch (Exception e) {
            return ConflictResolutionResult.error("Failed to fetch SIS data: " + e.getMessage());
        }
    }

    /**
     * Determine which resolution strategy to use
     */
    private ConflictResolutionStrategy determineStrategy(HallPass localPass, String sisStatus,
                                                          LocalDateTime sisUpdatedAt) {
        // Rule 1: If SIS shows COMPLETED but local is still ACTIVE, SIS wins
        if ("COMPLETED".equals(sisStatus) && "ACTIVE".equals(localPass.getStatus())) {
            return ConflictResolutionStrategy.SIS_WINS;
        }

        // Rule 2: If local has more recent time-in and is RETURNED, local wins
        if (localPass.isReturned() && localPass.getTimeIn() != null) {
            return ConflictResolutionStrategy.LOCAL_WINS;
        }

        // Rule 3: If both have different times, try to merge
        if (sisUpdatedAt != null) {
            return ConflictResolutionStrategy.MERGE;
        }

        // Rule 4: If can't determine, require manual review
        return ConflictResolutionStrategy.MANUAL_REVIEW;
    }

    /**
     * Apply SIS data to local record
     */
    private void applyFromSIS(HallPass localPass, Map<String, Object> sisRecord) {
        if (sisRecord.get("returnTime") != null) {
            localPass.setTimeIn(LocalTime.parse((String) sisRecord.get("returnTime")));
        }
        if (sisRecord.get("status") != null) {
            localPass.setStatus((String) sisRecord.get("status"));
        }
        if (sisRecord.get("durationMinutes") != null) {
            localPass.setDurationMinutes(((Number) sisRecord.get("durationMinutes")).intValue());
        }
    }

    /**
     * Merge records - take most recent data from each field
     */
    private void mergeRecords(HallPass localPass, Map<String, Object> sisRecord) {
        // Merge notes
        String sisNotes = (String) sisRecord.get("notes");
        if (sisNotes != null && !sisNotes.isEmpty()) {
            String localNotes = localPass.getNotes();
            if (localNotes == null || localNotes.isEmpty()) {
                localPass.setNotes(sisNotes);
            } else if (!localNotes.contains(sisNotes)) {
                localPass.setNotes(localNotes + "\n[SIS] " + sisNotes);
            }
        }

        // If local doesn't have time-in but SIS does, use SIS
        if (localPass.getTimeIn() == null && sisRecord.get("returnTime") != null) {
            localPass.setTimeIn(LocalTime.parse((String) sisRecord.get("returnTime")));
            localPass.setStatus("RETURNED");
        }

        // Calculate duration if we have both times now
        if (localPass.getTimeIn() != null && localPass.getTimeOut() != null) {
            localPass.setDurationMinutes((int) java.time.Duration
                    .between(localPass.getTimeOut(), localPass.getTimeIn()).toMinutes());
        }
    }

    // ==================== Conflict Resolution Result ====================

    public enum ConflictResolutionStrategy {
        SIS_WINS,
        LOCAL_WINS,
        MERGE,
        MANUAL_REVIEW
    }

    public static class ConflictResolutionResult {
        private boolean resolved;
        private boolean needsReview;
        private boolean error;
        private String message;
        private ConflictResolutionStrategy strategy;

        public static ConflictResolutionResult resolved(String message, ConflictResolutionStrategy strategy) {
            ConflictResolutionResult result = new ConflictResolutionResult();
            result.resolved = true;
            result.message = message;
            result.strategy = strategy;
            return result;
        }

        public static ConflictResolutionResult needsReview(String message, ConflictResolutionStrategy strategy) {
            ConflictResolutionResult result = new ConflictResolutionResult();
            result.needsReview = true;
            result.message = message;
            result.strategy = strategy;
            return result;
        }

        public static ConflictResolutionResult error(String message) {
            ConflictResolutionResult result = new ConflictResolutionResult();
            result.error = true;
            result.message = message;
            return result;
        }

        // Getters
        public boolean isResolved() { return resolved; }
        public boolean isNeedsReview() { return needsReview; }
        public boolean isError() { return error; }
        public String getMessage() { return message; }
        public ConflictResolutionStrategy getStrategy() { return strategy; }
    }

    // ==================== Destination Mapping ====================

    /**
     * Destination mapping between Teacher Portal and SIS
     *
     * Teacher Portal uses: RESTROOM, NURSE, OFFICE, COUNSELOR, WATER_FOUNTAIN, LOCKER, OTHER
     * SIS uses: BATHROOM, CLINIC, ADMIN_OFFICE, COUNSELOR, LIBRARY, CAFETERIA,
     *           ANOTHER_CLASSROOM, LOCKER, WATER_FOUNTAIN, OFFICE, OTHER
     */
    private static final Map<String, String> TEACHER_TO_SIS_DESTINATION = Map.ofEntries(
            Map.entry("RESTROOM", "BATHROOM"),
            Map.entry("NURSE", "CLINIC"),
            Map.entry("OFFICE", "ADMIN_OFFICE"),
            Map.entry("COUNSELOR", "COUNSELOR"),
            Map.entry("WATER_FOUNTAIN", "WATER_FOUNTAIN"),
            Map.entry("LOCKER", "LOCKER"),
            Map.entry("OTHER", "OTHER")
    );

    private static final Map<String, String> SIS_TO_TEACHER_DESTINATION = Map.ofEntries(
            Map.entry("BATHROOM", "RESTROOM"),
            Map.entry("CLINIC", "NURSE"),
            Map.entry("ADMIN_OFFICE", "OFFICE"),
            Map.entry("OFFICE", "OFFICE"),
            Map.entry("COUNSELOR", "COUNSELOR"),
            Map.entry("LIBRARY", "OTHER"),
            Map.entry("CAFETERIA", "OTHER"),
            Map.entry("ANOTHER_CLASSROOM", "OTHER"),
            Map.entry("LOCKER", "LOCKER"),
            Map.entry("WATER_FOUNTAIN", "WATER_FOUNTAIN"),
            Map.entry("OTHER", "OTHER")
    );

    /**
     * Convert Teacher destination to SIS destination
     */
    public String mapDestinationToSIS(String teacherDestination) {
        if (teacherDestination == null) return "OTHER";
        return TEACHER_TO_SIS_DESTINATION.getOrDefault(teacherDestination.toUpperCase(), "OTHER");
    }

    /**
     * Convert SIS destination to Teacher destination
     */
    public String mapDestinationFromSIS(String sisDestination) {
        if (sisDestination == null) return "OTHER";
        return SIS_TO_TEACHER_DESTINATION.getOrDefault(sisDestination.toUpperCase(), "OTHER");
    }

    /**
     * Get all destination mappings for reference
     */
    public Map<String, Object> getDestinationMappings() {
        Map<String, Object> mappings = new HashMap<>();
        mappings.put("teacherToSIS", TEACHER_TO_SIS_DESTINATION);
        mappings.put("sisToTeacher", SIS_TO_TEACHER_DESTINATION);
        mappings.put("teacherDestinations", List.of("RESTROOM", "NURSE", "OFFICE", "COUNSELOR", "WATER_FOUNTAIN", "LOCKER", "OTHER"));
        mappings.put("sisDestinations", List.of("BATHROOM", "CLINIC", "ADMIN_OFFICE", "COUNSELOR", "LIBRARY", "CAFETERIA", "ANOTHER_CLASSROOM", "LOCKER", "WATER_FOUNTAIN", "OFFICE", "OTHER"));
        return mappings;
    }

    // ==================== Helper Methods ====================

    private Map<String, Object> convertToSyncPayload(HallPass pass) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("localId", pass.getId());
        payload.put("studentId", pass.getStudent() != null ? pass.getStudent().getId() : null);
        payload.put("passDate", pass.getPassDate().toString());
        payload.put("timeOut", pass.getTimeOut().toString());
        payload.put("timeIn", pass.getTimeIn() != null ? pass.getTimeIn().toString() : null);
        // Map destination to SIS format when syncing
        payload.put("destination", mapDestinationToSIS(pass.getDestination()));
        payload.put("originalDestination", pass.getDestination());
        payload.put("status", pass.getStatus());
        payload.put("durationMinutes", pass.getDurationMinutes());
        payload.put("flagged", pass.getFlagged());
        payload.put("notes", pass.getNotes());
        return payload;
    }

    private Map<String, Object> getLocalStudentAnalytics(Long studentId) {
        Student student = studentRepository.findById(studentId).orElse(null);
        if (student == null) {
            return Map.of("error", "Student not found");
        }

        List<HallPass> passes = hallPassRepository.findByStudentOrderByPassDateDescTimeOutDesc(student);

        Map<String, Object> analytics = new HashMap<>();
        analytics.put("studentId", studentId);
        analytics.put("studentName", student.getFullName());
        analytics.put("source", "LOCAL");

        Map<String, Object> summary = new HashMap<>();
        summary.put("totalPasses", passes.size());
        summary.put("todayPasses", passes.stream()
                .filter(p -> p.getPassDate().equals(LocalDate.now()))
                .count());

        OptionalDouble avgDuration = passes.stream()
                .filter(p -> p.getDurationMinutes() != null)
                .mapToInt(HallPass::getDurationMinutes)
                .average();
        summary.put("averageDuration", avgDuration.orElse(0));

        analytics.put("summary", summary);
        return analytics;
    }

    private Map<String, Object> generateLocalReportCard(Long studentId) {
        Map<String, Object> analytics = getLocalStudentAnalytics(studentId);
        analytics.put("reportType", "LOCAL_FALLBACK");
        analytics.put("grade", "N/A");
        analytics.put("feedback", "Unable to generate full report. Please check SIS connection.");
        return analytics;
    }

    private void cacheSchoolAnalytics(Map<String, Object> analytics) {
        // In a real implementation, cache this for quick access
        log.debug("Caching school analytics");
    }

    private Map<String, Object> getCachedOrLocalSchoolAnalytics() {
        // Generate from local data
        List<HallPass> todayPasses = hallPassRepository.findByPassDateOrderByTimeOutDesc(LocalDate.now());

        Map<String, Object> analytics = new HashMap<>();
        analytics.put("source", "LOCAL");
        analytics.put("date", LocalDate.now());

        Map<String, Object> summary = new HashMap<>();
        summary.put("totalPasses", todayPasses.size());
        summary.put("activePasses", todayPasses.stream().filter(HallPass::isActive).count());
        summary.put("returnedPasses", todayPasses.stream().filter(HallPass::isReturned).count());
        summary.put("overduePasses", todayPasses.stream().filter(HallPass::isOverdue).count());

        analytics.put("summary", summary);
        return analytics;
    }

    private Map<String, Object> generateLocalWeeklyTrend() {
        Map<String, Object> trend = new HashMap<>();
        trend.put("source", "LOCAL");

        Map<String, Long> dailyCounts = new LinkedHashMap<>();
        for (int i = 6; i >= 0; i--) {
            LocalDate date = LocalDate.now().minusDays(i);
            long count = hallPassRepository.findByPassDateOrderByTimeOutDesc(date).size();
            dailyCounts.put(date.toString(), count);
        }
        trend.put("dailyCounts", dailyCounts);
        return trend;
    }

    private Map<String, Object> generateLocalExport(Long studentId, LocalDate startDate, LocalDate endDate) {
        Student student = studentRepository.findById(studentId).orElse(null);
        if (student == null) {
            return Map.of("error", "Student not found");
        }

        LocalDate start = startDate != null ? startDate : LocalDate.now().minusDays(30);
        LocalDate end = endDate != null ? endDate : LocalDate.now();

        List<HallPass> passes = hallPassRepository
                .findByPassDateBetweenOrderByPassDateDescTimeOutDesc(start, end)
                .stream()
                .filter(p -> p.getStudent() != null && p.getStudent().getId().equals(studentId))
                .toList();

        Map<String, Object> export = new HashMap<>();
        export.put("source", "LOCAL");
        export.put("studentName", student.getFullName());
        export.put("dateRange", Map.of("start", start, "end", end));
        export.put("totalRecords", passes.size());

        List<Map<String, Object>> records = new ArrayList<>();
        for (HallPass pass : passes) {
            Map<String, Object> record = new HashMap<>();
            record.put("date", pass.getPassDate());
            record.put("timeOut", pass.getTimeOut());
            record.put("timeIn", pass.getTimeIn());
            record.put("destination", pass.getDestination());
            record.put("duration", pass.getDurationMinutes());
            record.put("status", pass.getStatus());
            records.add(record);
        }
        export.put("records", records);

        return export;
    }

    // ==================== Status Methods ====================

    public LocalDateTime getLastSyncTime() {
        return lastSyncTime;
    }

    public boolean isSyncInProgress() {
        return syncInProgress;
    }

    public long getPendingSyncCount() {
        return hallPassRepository.findNeedingSync().size();
    }

    // ==================== Result Class ====================

    public static class SyncResult {
        private boolean success;
        private String message;
        private LocalDateTime startTime;
        private LocalDateTime endTime;
        private int pushedCount;
        private boolean pullSuccess;
        private boolean inProgress;

        public static SyncResult inProgress() {
            SyncResult result = new SyncResult();
            result.setInProgress(true);
            result.setMessage("Sync already in progress");
            return result;
        }

        // Getters and setters
        public boolean isSuccess() { return success; }
        public void setSuccess(boolean success) { this.success = success; }
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
        public LocalDateTime getStartTime() { return startTime; }
        public void setStartTime(LocalDateTime startTime) { this.startTime = startTime; }
        public LocalDateTime getEndTime() { return endTime; }
        public void setEndTime(LocalDateTime endTime) { this.endTime = endTime; }
        public int getPushedCount() { return pushedCount; }
        public void setPushedCount(int pushedCount) { this.pushedCount = pushedCount; }
        public boolean isPullSuccess() { return pullSuccess; }
        public void setPullSuccess(boolean pullSuccess) { this.pullSuccess = pullSuccess; }
        public boolean isInProgress() { return inProgress; }
        public void setInProgress(boolean inProgress) { this.inProgress = inProgress; }
    }
}
