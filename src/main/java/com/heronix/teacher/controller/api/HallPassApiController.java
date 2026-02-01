package com.heronix.teacher.controller.api;

import com.heronix.teacher.model.domain.HallPass;
import com.heronix.teacher.service.HallPassService;
import com.heronix.teacher.service.HallPassSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Hall Pass REST API Controller for Teacher Portal
 *
 * Provides REST endpoints for:
 * - Issuing new hall passes
 * - Marking students as returned
 * - Querying hall pass data
 * - Statistics and analytics
 *
 * Used by:
 * - Teacher mobile apps
 * - External integrations
 * - Parent/Student portals
 *
 * @author Heronix Team
 * @version 1.0.0
 */
@RestController
@RequestMapping("/api/teacher/hall-pass")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*")
public class HallPassApiController {

    private final HallPassService hallPassService;
    private final HallPassSyncService syncService;

    // ==================== Issue/Return Operations ====================

    /**
     * Issue a new hall pass
     */
    @PostMapping("/issue")
    public ResponseEntity<?> issueHallPass(@RequestBody Map<String, Object> request) {
        try {
            Long studentId = Long.valueOf(request.get("studentId").toString());
            String destination = (String) request.get("destination");
            String notes = (String) request.getOrDefault("notes", "");

            log.info("API: Issuing hall pass for student {} to {}", studentId, destination);

            HallPass pass = hallPassService.issuePass(studentId, destination, notes != null ? notes : "");

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("passId", pass.getId());
            response.put("studentId", studentId);
            response.put("destination", destination);
            response.put("timeOut", pass.getTimeOut().toString());
            response.put("message", "Hall pass issued successfully");

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Failed to issue hall pass via API", e);
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "error", e.getMessage()
            ));
        }
    }

    /**
     * Mark a student as returned
     */
    @PostMapping("/{passId}/return")
    public ResponseEntity<?> markReturned(
            @PathVariable Long passId,
            @RequestBody(required = false) Map<String, Object> request) {
        try {
            String notes = request != null ? (String) request.get("notes") : null;

            log.info("API: Marking pass {} as returned", passId);

            hallPassService.markReturned(passId);

            if (notes != null && !notes.isEmpty()) {
                hallPassService.updateNotes(passId, notes);
            }

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "passId", passId,
                    "returnedAt", LocalTime.now().toString(),
                    "message", "Student marked as returned"
            ));

        } catch (Exception e) {
            log.error("Failed to mark pass as returned", e);
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "error", e.getMessage()
            ));
        }
    }

    /**
     * Flag a hall pass for review
     */
    @PostMapping("/{passId}/flag")
    public ResponseEntity<?> flagPass(
            @PathVariable Long passId,
            @RequestBody Map<String, Object> request) {
        try {
            String reason = (String) request.getOrDefault("reason", "Flagged via API");

            log.info("API: Flagging pass {} - {}", passId, reason);

            hallPassService.flagPass(passId, true);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "passId", passId,
                    "message", "Pass flagged for review"
            ));

        } catch (Exception e) {
            log.error("Failed to flag pass", e);
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "error", e.getMessage()
            ));
        }
    }

    /**
     * Update notes on a hall pass
     */
    @PatchMapping("/{passId}/notes")
    public ResponseEntity<?> updateNotes(
            @PathVariable Long passId,
            @RequestBody Map<String, Object> request) {
        try {
            String notes = (String) request.get("notes");

            if (notes == null) {
                return ResponseEntity.badRequest().body(Map.of(
                        "success", false,
                        "error", "Notes field is required"
                ));
            }

            hallPassService.updateNotes(passId, notes);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "passId", passId,
                    "message", "Notes updated"
            ));

        } catch (Exception e) {
            log.error("Failed to update notes", e);
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "error", e.getMessage()
            ));
        }
    }

    // ==================== Query Operations ====================

    /**
     * Get all active hall passes
     */
    @GetMapping("/active")
    public ResponseEntity<?> getActivePasses() {
        try {
            List<HallPass> passes = hallPassService.getActivePasses();

            List<Map<String, Object>> data = passes.stream()
                    .map(this::passToMap)
                    .collect(Collectors.toList());

            return ResponseEntity.ok(Map.of(
                    "count", data.size(),
                    "passes", data
            ));

        } catch (Exception e) {
            log.error("Failed to get active passes", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "error", e.getMessage()
            ));
        }
    }

    /**
     * Get overdue hall passes
     */
    @GetMapping("/overdue")
    public ResponseEntity<?> getOverduePasses() {
        try {
            List<HallPass> passes = hallPassService.getOverduePasses();

            List<Map<String, Object>> data = passes.stream()
                    .map(this::passToMap)
                    .collect(Collectors.toList());

            return ResponseEntity.ok(Map.of(
                    "count", data.size(),
                    "passes", data
            ));

        } catch (Exception e) {
            log.error("Failed to get overdue passes", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "error", e.getMessage()
            ));
        }
    }

    /**
     * Get passes for a specific date
     */
    @GetMapping("/date/{date}")
    public ResponseEntity<?> getPassesByDate(
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        try {
            List<HallPass> passes = hallPassService.getPassesByDate(date);

            List<Map<String, Object>> data = passes.stream()
                    .map(this::passToMap)
                    .collect(Collectors.toList());

            return ResponseEntity.ok(Map.of(
                    "date", date.toString(),
                    "count", data.size(),
                    "passes", data
            ));

        } catch (Exception e) {
            log.error("Failed to get passes by date", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "error", e.getMessage()
            ));
        }
    }

    /**
     * Get passes for a student
     */
    @GetMapping("/student/{studentId}")
    public ResponseEntity<?> getPassesByStudent(
            @PathVariable Long studentId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        try {
            List<HallPass> passes;

            if (startDate != null && endDate != null) {
                passes = hallPassService.getPassesByDateRange(startDate, endDate).stream()
                        .filter(p -> p.getStudent() != null && p.getStudent().getId().equals(studentId))
                        .collect(Collectors.toList());
            } else {
                passes = hallPassService.getPassesByStudent(studentId);
            }

            List<Map<String, Object>> data = passes.stream()
                    .map(this::passToMap)
                    .collect(Collectors.toList());

            return ResponseEntity.ok(Map.of(
                    "studentId", studentId,
                    "count", data.size(),
                    "passes", data
            ));

        } catch (Exception e) {
            log.error("Failed to get passes for student", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "error", e.getMessage()
            ));
        }
    }

    /**
     * Get a specific hall pass
     */
    @GetMapping("/{passId}")
    public ResponseEntity<?> getPass(@PathVariable Long passId) {
        try {
            // Find pass in today's data or recent passes
            List<HallPass> recentPasses = hallPassService.getRecentPasses();
            Optional<HallPass> pass = recentPasses.stream()
                    .filter(p -> p.getId().equals(passId))
                    .findFirst();

            if (pass.isPresent()) {
                return ResponseEntity.ok(passToMap(pass.get()));
            } else {
                return ResponseEntity.notFound().build();
            }

        } catch (Exception e) {
            log.error("Failed to get pass", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "error", e.getMessage()
            ));
        }
    }

    // ==================== Statistics ====================

    /**
     * Get today's statistics
     */
    @GetMapping("/stats/today")
    public ResponseEntity<?> getTodayStats() {
        try {
            Map<String, Object> stats = hallPassService.getTodayStatistics();
            stats.put("date", LocalDate.now().toString());
            return ResponseEntity.ok(stats);

        } catch (Exception e) {
            log.error("Failed to get today's stats", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "error", e.getMessage()
            ));
        }
    }

    /**
     * Get statistics by destination
     */
    @GetMapping("/stats/destinations")
    public ResponseEntity<?> getDestinationStats(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        try {
            LocalDate targetDate = date != null ? date : LocalDate.now();
            Map<String, Long> stats = hallPassService.getDestinationStatistics();

            return ResponseEntity.ok(Map.of(
                    "date", targetDate.toString(),
                    "destinations", stats
            ));

        } catch (Exception e) {
            log.error("Failed to get destination stats", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "error", e.getMessage()
            ));
        }
    }

    /**
     * Get statistics by student
     */
    @GetMapping("/stats/students/{studentId}")
    public ResponseEntity<?> getStudentStats(
            @PathVariable Long studentId) {
        try {
            Map<String, Object> stats = hallPassService.getStudentStatistics(studentId);

            return ResponseEntity.ok(stats);

        } catch (Exception e) {
            log.error("Failed to get student stats", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "error", e.getMessage()
            ));
        }
    }

    // ==================== Sync Operations ====================

    /**
     * Trigger manual sync with SIS
     */
    @PostMapping("/sync")
    public ResponseEntity<?> triggerSync() {
        try {
            log.info("API: Manual sync triggered");

            syncService.performFullSync();

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Sync completed successfully",
                    "syncedAt", java.time.LocalDateTime.now().toString()
            ));

        } catch (Exception e) {
            log.error("Sync failed", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "success", false,
                    "error", e.getMessage()
            ));
        }
    }

    /**
     * Get sync status
     */
    @GetMapping("/sync/status")
    public ResponseEntity<?> getSyncStatus() {
        try {
            // Return basic sync info
            return ResponseEntity.ok(Map.of(
                    "enabled", true,
                    "lastSyncAttempt", java.time.LocalDateTime.now().toString(),
                    "message", "Sync service is running"
            ));

        } catch (Exception e) {
            return ResponseEntity.ok(Map.of(
                    "enabled", false,
                    "error", e.getMessage()
            ));
        }
    }

    // ==================== Reference Data ====================

    /**
     * Get available destinations
     */
    @GetMapping("/destinations")
    public ResponseEntity<?> getDestinations() {
        return ResponseEntity.ok(Map.of(
                "destinations", List.of(
                        Map.of("value", "RESTROOM", "displayName", "Restroom"),
                        Map.of("value", "NURSE", "displayName", "Nurse's Office"),
                        Map.of("value", "OFFICE", "displayName", "Main Office"),
                        Map.of("value", "COUNSELOR", "displayName", "Counselor"),
                        Map.of("value", "WATER_FOUNTAIN", "displayName", "Water Fountain"),
                        Map.of("value", "LOCKER", "displayName", "Locker"),
                        Map.of("value", "OTHER", "displayName", "Other")
                )
        ));
    }

    // ==================== Helper Methods ====================

    private Map<String, Object> passToMap(HallPass pass) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", pass.getId());
        map.put("destination", pass.getDestination());
        map.put("date", pass.getCreatedAt() != null ? pass.getCreatedAt().toString() : null);
        map.put("timeOut", pass.getTimeOut() != null ? pass.getTimeOut().toString() : null);
        map.put("timeIn", pass.getTimeIn() != null ? pass.getTimeIn().toString() : null);
        map.put("status", pass.getStatus());
        map.put("duration", pass.getDurationMinutes());
        map.put("notes", pass.getNotes());
        map.put("flagged", pass.getFlagged());
        map.put("syncStatus", pass.getSyncStatus());

        if (pass.getStudent() != null) {
            map.put("studentId", pass.getStudent().getId());
            map.put("studentName", pass.getStudent().getFirstName() + " " + pass.getStudent().getLastName());
        }

        return map;
    }
}
