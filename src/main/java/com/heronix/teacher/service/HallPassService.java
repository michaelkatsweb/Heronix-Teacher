package com.heronix.teacher.service;

import com.heronix.teacher.model.domain.HallPass;
import com.heronix.teacher.model.domain.Student;
import com.heronix.teacher.repository.HallPassRepository;
import com.heronix.teacher.repository.StudentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Hall Pass Service
 *
 * Business logic for electronic hall pass management
 *
 * Features:
 * - Issue and track hall passes
 * - Monitor active passes
 * - Detect overdue passes
 * - Calculate pass statistics
 * - Flag excessive usage
 * - Auto-sync to main server
 *
 * @author EduScheduler Team
 * @version 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HallPassService {

    private final HallPassRepository hallPassRepository;
    private final StudentRepository studentRepository;

    // Default time threshold for flagging passes (15 minutes)
    private static final int DEFAULT_TIME_THRESHOLD = 15;

    // === Hall Pass Management ===

    /**
     * Issue a new hall pass
     */
    @Transactional
    public HallPass issuePass(Long studentId, String destination, String notes) {
        log.debug("Issuing hall pass for student {} to {}", studentId, destination);

        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new RuntimeException("Student not found: " + studentId));

        // Check if student already has an active pass
        List<HallPass> activePasses = hallPassRepository.findActivePasses(LocalDate.now());
        boolean hasActivePass = activePasses.stream()
                .anyMatch(p -> p.getStudent().getId().equals(studentId));

        if (hasActivePass) {
            throw new RuntimeException("Student already has an active hall pass");
        }

        HallPass hallPass = HallPass.builder()
                .student(student)
                .passDate(LocalDate.now())
                .timeOut(LocalTime.now())
                .destination(destination)
                .notes(notes)
                .status("ACTIVE")
                .build();

        return hallPassRepository.save(hallPass);
    }

    /**
     * Mark student as returned
     */
    @Transactional
    public HallPass markReturned(Long passId) {
        log.debug("Marking hall pass {} as returned", passId);

        HallPass hallPass = hallPassRepository.findById(passId)
                .orElseThrow(() -> new RuntimeException("Hall pass not found: " + passId));

        if (!hallPass.isActive()) {
            throw new RuntimeException("Hall pass is not active");
        }

        hallPass.markReturned(LocalTime.now());
        return hallPassRepository.save(hallPass);
    }

    /**
     * Update pass notes
     */
    @Transactional
    public HallPass updateNotes(Long passId, String notes) {
        HallPass hallPass = hallPassRepository.findById(passId)
                .orElseThrow(() -> new RuntimeException("Hall pass not found"));

        hallPass.setNotes(notes);
        return hallPassRepository.save(hallPass);
    }

    /**
     * Flag a pass for review
     */
    @Transactional
    public HallPass flagPass(Long passId, boolean flagged) {
        HallPass hallPass = hallPassRepository.findById(passId)
                .orElseThrow(() -> new RuntimeException("Hall pass not found"));

        hallPass.setFlagged(flagged);
        return hallPassRepository.save(hallPass);
    }

    /**
     * Mark pass as overdue
     */
    @Transactional
    public HallPass markOverdue(Long passId) {
        HallPass hallPass = hallPassRepository.findById(passId)
                .orElseThrow(() -> new RuntimeException("Hall pass not found"));

        hallPass.setStatus("OVERDUE");
        hallPass.setFlagged(true);
        return hallPassRepository.save(hallPass);
    }

    // === Queries ===

    /**
     * Get all active passes
     */
    public List<HallPass> getActivePasses() {
        return hallPassRepository.findActivePasses();
    }

    /**
     * Get active passes for today
     */
    public List<HallPass> getTodayActivePasses() {
        return hallPassRepository.findActivePasses(LocalDate.now());
    }

    /**
     * Get passes for a specific date
     */
    public List<HallPass> getPassesByDate(LocalDate date) {
        return hallPassRepository.findByPassDateOrderByTimeOutDesc(date);
    }

    /**
     * Get passes for a student
     */
    public List<HallPass> getPassesByStudent(Long studentId) {
        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new RuntimeException("Student not found"));
        return hallPassRepository.findByStudentOrderByPassDateDescTimeOutDesc(student);
    }

    /**
     * Get passes for date range
     */
    public List<HallPass> getPassesByDateRange(LocalDate startDate, LocalDate endDate) {
        return hallPassRepository.findByPassDateBetweenOrderByPassDateDescTimeOutDesc(startDate, endDate);
    }

    /**
     * Get flagged passes
     */
    public List<HallPass> getFlaggedPasses() {
        return hallPassRepository.findFlaggedPasses();
    }

    /**
     * Get recent passes (last 7 days)
     */
    public List<HallPass> getRecentPasses() {
        LocalDate sevenDaysAgo = LocalDate.now().minusDays(7);
        return hallPassRepository.findRecentPasses(sevenDaysAgo);
    }

    /**
     * Get recent hall pass records for dashboard
     * Returns formatted strings for display
     */
    public List<String> getRecentHallPasses(int limit) {
        return hallPassRepository.findAll().stream()
            .filter(p -> p.getPassDate() != null)
            .sorted((p1, p2) -> {
                int dateCompare = p2.getPassDate().compareTo(p1.getPassDate());
                if (dateCompare != 0) return dateCompare;
                if (p1.getTimeOut() == null) return 1;
                if (p2.getTimeOut() == null) return -1;
                return p2.getTimeOut().compareTo(p1.getTimeOut());
            })
            .limit(limit)
            .map(p -> String.format("  %s: %s at %s",
                p.getStudent() != null ? p.getStudent().getFullName() : "Unknown",
                p.getDestination() != null ? p.getDestination() : "Unknown",
                p.getTimeOut() != null ? p.getTimeOut() : "Unknown"))
            .collect(Collectors.toList());
    }

    /**
     * Get count of currently active hall passes
     */
    public long getActiveHallPassesCount() {
        return hallPassRepository.findActivePasses(LocalDate.now()).size();
    }

    /**
     * Get overdue passes for today
     */
    public List<HallPass> getOverduePasses() {
        LocalDate today = LocalDate.now();
        LocalTime thresholdTime = LocalTime.now().minusMinutes(DEFAULT_TIME_THRESHOLD);
        return hallPassRepository.findOverduePasses(today, thresholdTime);
    }

    /**
     * Check if student has active pass
     */
    public boolean hasActivePass(Long studentId) {
        List<HallPass> activePasses = hallPassRepository.findActivePasses(LocalDate.now());
        return activePasses.stream()
                .anyMatch(p -> p.getStudent().getId().equals(studentId));
    }

    // === Statistics ===

    /**
     * Get today's statistics
     */
    public Map<String, Object> getTodayStatistics() {
        LocalDate today = LocalDate.now();

        List<HallPass> todayPasses = hallPassRepository.findByPassDateOrderByTimeOutDesc(today);
        long activeCount = todayPasses.stream().filter(HallPass::isActive).count();
        long returnedCount = todayPasses.stream().filter(HallPass::isReturned).count();
        long overdueCount = todayPasses.stream().filter(HallPass::isOverdue).count();

        OptionalDouble avgDuration = todayPasses.stream()
                .filter(p -> p.getDurationMinutes() != null)
                .mapToInt(HallPass::getDurationMinutes)
                .average();

        Map<String, Object> stats = new HashMap<>();
        stats.put("date", today);
        stats.put("totalPasses", todayPasses.size());
        stats.put("active", activeCount);
        stats.put("returned", returnedCount);
        stats.put("overdue", overdueCount);
        stats.put("averageDuration", avgDuration.isPresent() ?
                String.format("%.1f min", avgDuration.getAsDouble()) : "N/A");

        return stats;
    }

    /**
     * Get destination statistics for today
     */
    public Map<String, Long> getDestinationStatistics() {
        LocalDate today = LocalDate.now();
        List<HallPass> todayPasses = hallPassRepository.findByPassDateOrderByTimeOutDesc(today);

        return todayPasses.stream()
                .collect(Collectors.groupingBy(
                        HallPass::getDestination,
                        Collectors.counting()
                ));
    }

    /**
     * Get student statistics
     */
    public Map<String, Object> getStudentStatistics(Long studentId) {
        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new RuntimeException("Student not found"));

        List<HallPass> allPasses = hallPassRepository
                .findByStudentOrderByPassDateDescTimeOutDesc(student);

        long totalPasses = allPasses.size();
        long todayPasses = hallPassRepository.countByStudentAndDate(student, LocalDate.now());

        OptionalDouble avgDuration = allPasses.stream()
                .filter(p -> p.getDurationMinutes() != null)
                .mapToInt(HallPass::getDurationMinutes)
                .average();

        Map<String, Object> stats = new HashMap<>();
        stats.put("studentId", studentId);
        stats.put("studentName", student.getFullName());
        stats.put("totalPasses", totalPasses);
        stats.put("todayPasses", todayPasses);
        stats.put("averageDuration", avgDuration.isPresent() ?
                String.format("%.1f min", avgDuration.getAsDouble()) : "N/A");

        return stats;
    }

    /**
     * Get students with excessive passes today
     */
    public List<Map<String, Object>> getStudentsWithExcessivePasses() {
        LocalDate today = LocalDate.now();
        List<Object[]> results = hallPassRepository.findStudentsWithExcessivePasses(today, 3L);

        return results.stream().map(row -> {
            Student student = (Student) row[0];
            Long passCount = (Long) row[1];

            Map<String, Object> data = new HashMap<>();
            data.put("student", student);
            data.put("passCount", passCount);
            return data;
        }).collect(Collectors.toList());
    }

    /**
     * Get pass statistics by date
     */
    public List<Map<String, Object>> getPassStatisticsByDate(LocalDate date) {
        List<Object[]> results = hallPassRepository.getPassStatisticsByDate(date);

        return results.stream().map(row -> {
            String destination = (String) row[0];
            Long count = (Long) row[1];
            Double avgDuration = (Double) row[2];

            Map<String, Object> data = new HashMap<>();
            data.put("destination", destination);
            data.put("count", count);
            data.put("averageDuration", String.format("%.1f min", avgDuration));
            return data;
        }).collect(Collectors.toList());
    }

    // === Auto-Detection ===

    /**
     * Detect and mark overdue passes
     */
    @Transactional
    public List<HallPass> detectOverduePasses() {
        log.debug("Detecting overdue passes");

        List<HallPass> overduePasses = getOverduePasses();

        for (HallPass pass : overduePasses) {
            if (pass.isActive()) {
                pass.setStatus("OVERDUE");
                pass.setFlagged(true);
                hallPassRepository.save(pass);
            }
        }

        return overduePasses;
    }

    /**
     * Get pass duration threshold warnings
     */
    public List<HallPass> getPassesNearingThreshold(int minutesThreshold) {
        List<HallPass> activePasses = getTodayActivePasses();

        return activePasses.stream()
                .filter(pass -> pass.getCurrentDuration() >= minutesThreshold - 2
                        && pass.getCurrentDuration() < minutesThreshold)
                .collect(Collectors.toList());
    }

    // === Auto-Sync Support ===

    /**
     * Mark pass as synced
     */
    @Transactional
    public void markPassSynced(Long passId) {
        HallPass hallPass = hallPassRepository.findById(passId)
                .orElseThrow(() -> new RuntimeException("Hall pass not found"));
        hallPass.setSyncStatus("synced");
        hallPassRepository.save(hallPass);
    }

    /**
     * Count items needing sync
     */
    public long countItemsNeedingSync() {
        return hallPassRepository.findNeedingSync().size();
    }

    /**
     * Get all items needing sync
     */
    public List<HallPass> getItemsNeedingSync() {
        return hallPassRepository.findNeedingSync();
    }

    // === Utility Methods ===

    /**
     * Delete hall pass
     */
    @Transactional
    public void deletePass(Long passId) {
        hallPassRepository.deleteById(passId);
        log.info("Deleted hall pass: {}", passId);
    }

    /**
     * Get available destinations
     */
    public List<String> getAvailableDestinations() {
        return Arrays.asList(
                "RESTROOM",
                "NURSE",
                "OFFICE",
                "COUNSELOR",
                "WATER_FOUNTAIN",
                "LOCKER",
                "OTHER"
        );
    }

    /**
     * Validate pass issuance
     */
    public boolean canIssuePass(Long studentId) {
        // Check if student exists
        Optional<Student> student = studentRepository.findById(studentId);
        if (student.isEmpty()) {
            return false;
        }

        // Check if student already has active pass
        return !hasActivePass(studentId);
    }
}
