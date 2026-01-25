package com.heronix.teacher.service;

import com.heronix.teacher.model.domain.Attendance;
import com.heronix.teacher.model.domain.Student;
import com.heronix.teacher.repository.AttendanceRepository;
import com.heronix.teacher.repository.StudentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Attendance Service
 *
 * Business logic for attendance tracking
 *
 * Features:
 * - Daily attendance recording
 * - Tardy tracking with arrival times
 * - Absence/excused tracking
 * - Attendance statistics
 * - Auto-sync to main server
 *
 * @author EduScheduler Team
 * @version 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AttendanceService {

    private final AttendanceRepository attendanceRepository;
    private final StudentRepository studentRepository;
    private final SessionManager sessionManager;
    private final AdminApiClient adminApiClient;

    @Value("${eduproteacher.roster.mock.enabled:true}")
    private boolean rosterMockEnabled;

    // School start time (configurable)
    private static final LocalTime SCHOOL_START_TIME = LocalTime.of(8, 0);

    // === Daily Attendance Management ===

    /**
     * Record attendance for a student
     */
    @Transactional
    public Attendance recordAttendance(Long studentId, LocalDate date, String status,
                                       LocalTime arrivalTime, String notes) {
        log.debug("Recording attendance for student {} on {}: {}", studentId, date, status);

        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new RuntimeException("Student not found: " + studentId));

        // Check if attendance already exists
        Optional<Attendance> existing = attendanceRepository
                .findByStudentAndAttendanceDate(student, date);

        Attendance attendance;
        if (existing.isPresent()) {
            attendance = existing.get();
            log.debug("Updating existing attendance record");
        } else {
            attendance = Attendance.builder()
                    .student(student)
                    .attendanceDate(date)
                    .build();
        }

        attendance.setStatus(status);
        attendance.setArrivalTime(arrivalTime);
        attendance.setNotes(notes);

        // Calculate tardiness if arrival time provided
        if (arrivalTime != null) {
            attendance.calculateMinutesLate(SCHOOL_START_TIME);
        }

        return attendanceRepository.save(attendance);
    }

    /**
     * Mark student as present
     */
    @Transactional
    public Attendance markPresent(Long studentId, LocalDate date) {
        return recordAttendance(studentId, date, "PRESENT", null, null);
    }

    /**
     * Mark student as absent
     */
    @Transactional
    public Attendance markAbsent(Long studentId, LocalDate date, String reason) {
        return recordAttendance(studentId, date, "ABSENT", null, reason);
    }

    /**
     * Mark student as tardy
     */
    @Transactional
    public Attendance markTardy(Long studentId, LocalDate date, LocalTime arrivalTime, String reason) {
        return recordAttendance(studentId, date, "TARDY", arrivalTime, reason);
    }

    /**
     * Mark absence as excused
     */
    @Transactional
    public Attendance markExcused(Long attendanceId) {
        Attendance attendance = attendanceRepository.findById(attendanceId)
                .orElseThrow(() -> new RuntimeException("Attendance record not found"));

        attendance.setExcused(true);
        return attendanceRepository.save(attendance);
    }

    /**
     * Bulk record attendance for all students (quick attendance)
     */
    @Transactional
    public List<Attendance> recordBulkAttendance(LocalDate date, Map<Long, String> studentStatuses) {
        log.info("Recording bulk attendance for {} students on {}", studentStatuses.size(), date);

        List<Attendance> records = new ArrayList<>();

        for (Map.Entry<Long, String> entry : studentStatuses.entrySet()) {
            Long studentId = entry.getKey();
            String status = entry.getValue();

            try {
                Attendance attendance = recordAttendance(studentId, date, status, null, null);
                records.add(attendance);
            } catch (Exception e) {
                log.error("Failed to record attendance for student {}: {}", studentId, e.getMessage());
            }
        }

        return records;
    }

    // === Queries ===

    /**
     * Get attendance for a specific date
     */
    public List<Attendance> getAttendanceByDate(LocalDate date) {
        return attendanceRepository.findByAttendanceDate(date);
    }

    /**
     * Get attendance for a student
     */
    public List<Attendance> getAttendanceByStudent(Long studentId) {
        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new RuntimeException("Student not found"));
        return attendanceRepository.findByStudentOrderByAttendanceDateDesc(student);
    }

    /**
     * Get attendance for date range
     */
    public List<Attendance> getAttendanceByDateRange(LocalDate startDate, LocalDate endDate) {
        return attendanceRepository.findByAttendanceDateBetween(startDate, endDate);
    }

    /**
     * Get absent students for today
     */
    public List<Attendance> getTodayAbsent() {
        return attendanceRepository.findAbsentByDate(LocalDate.now());
    }

    /**
     * Get tardy students for today
     */
    public List<Attendance> getTodayTardy() {
        return attendanceRepository.findTardyByDate(LocalDate.now());
    }

    /**
     * Get present students for today
     */
    public List<Attendance> getTodayPresent() {
        return attendanceRepository.findPresentByDate(LocalDate.now());
    }

    // === Statistics ===

    /**
     * Get attendance statistics for today
     */
    public Map<String, Object> getTodayStatistics() {
        LocalDate today = LocalDate.now();

        List<Attendance> todayAttendance = attendanceRepository.findByAttendanceDate(today);
        long totalStudents = studentRepository.findByActiveTrue().size();

        long present = todayAttendance.stream().filter(Attendance::isPresent).count();
        long absent = todayAttendance.stream().filter(Attendance::isAbsent).count();
        long tardy = todayAttendance.stream().filter(Attendance::isTardy).count();
        long unmarked = totalStudents - todayAttendance.size();

        double presentRate = totalStudents > 0 ? (present * 100.0 / totalStudents) : 0.0;

        Map<String, Object> stats = new HashMap<>();
        stats.put("date", today);
        stats.put("totalStudents", totalStudents);
        stats.put("present", present);
        stats.put("absent", absent);
        stats.put("tardy", tardy);
        stats.put("unmarked", unmarked);
        stats.put("presentRate", String.format("%.1f%%", presentRate));

        return stats;
    }

    /**
     * Get student attendance statistics
     */
    public Map<String, Object> getStudentStatistics(Long studentId) {
        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new RuntimeException("Student not found"));

        long totalRecords = attendanceRepository.findByStudentOrderByAttendanceDateDesc(student).size();
        long absences = attendanceRepository.countAbsencesByStudent(student);
        long tardies = attendanceRepository.countTardiesByStudent(student);

        Double attendanceRate = attendanceRepository.getAttendanceRateByStudent(student);
        if (attendanceRate == null) attendanceRate = 0.0;

        Map<String, Object> stats = new HashMap<>();
        stats.put("studentId", studentId);
        stats.put("studentName", student.getFullName());
        stats.put("totalDays", totalRecords);
        stats.put("absences", absences);
        stats.put("tardies", tardies);
        stats.put("attendanceRate", String.format("%.1f%%", attendanceRate));

        return stats;
    }

    /**
     * Get students with excessive absences (5+ absences)
     */
    public List<Map<String, Object>> getStudentsWithExcessiveAbsences() {
        List<Object[]> results = attendanceRepository.findStudentsWithExcessiveAbsences(5L);

        return results.stream().map(row -> {
            Student student = (Student) row[0];
            Long absenceCount = (Long) row[1];

            Map<String, Object> data = new HashMap<>();
            data.put("student", student);
            data.put("absenceCount", absenceCount);
            return data;
        }).collect(Collectors.toList());
    }

    /**
     * Get recent attendance (last 30 days)
     */
    public List<Attendance> getRecentAttendance() {
        LocalDate thirtyDaysAgo = LocalDate.now().minusDays(30);
        return attendanceRepository.findRecentAttendance(thirtyDaysAgo);
    }

    /**
     * Get recent attendance records for dashboard
     * Returns formatted strings for display
     */
    public List<String> getRecentAttendance(int limit) {
        return attendanceRepository.findAll().stream()
            .filter(a -> a.getAttendanceDate() != null)
            .sorted((a1, a2) -> a2.getAttendanceDate().compareTo(a1.getAttendanceDate()))
            .limit(limit)
            .map(a -> String.format("  %s: %s on %s",
                a.getStudent() != null ? a.getStudent().getFullName() : "Unknown",
                a.getStatus() != null ? a.getStatus() : "Unknown",
                a.getAttendanceDate()))
            .collect(Collectors.toList());
    }

    /**
     * Get count of present students for a specific date
     */
    public long getPresentCount(LocalDate date) {
        return attendanceRepository.findByAttendanceDate(date).stream()
            .filter(Attendance::isPresent)
            .count();
    }

    /**
     * Get count of absent students for a specific date
     */
    public long getAbsentCount(LocalDate date) {
        return attendanceRepository.findByAttendanceDate(date).stream()
            .filter(Attendance::isAbsent)
            .count();
    }

    /**
     * Get count of tardy students for a specific date
     */
    public long getTardyCount(LocalDate date) {
        return attendanceRepository.findByAttendanceDate(date).stream()
            .filter(Attendance::isTardy)
            .count();
    }

    /**
     * Get weekly attendance summary
     */
    public Map<LocalDate, Map<String, Long>> getWeeklySummary(LocalDate startDate) {
        LocalDate endDate = startDate.plusDays(7);
        List<Attendance> weekAttendance = attendanceRepository
                .findByAttendanceDateBetween(startDate, endDate);

        Map<LocalDate, Map<String, Long>> summary = new LinkedHashMap<>();

        for (Attendance attendance : weekAttendance) {
            LocalDate date = attendance.getAttendanceDate();
            summary.putIfAbsent(date, new HashMap<>());

            Map<String, Long> dayStats = summary.get(date);
            String status = attendance.getStatus();
            dayStats.put(status, dayStats.getOrDefault(status, 0L) + 1);
        }

        return summary;
    }

    // === Auto-Sync Support ===

    /**
     * Mark attendance as synced
     */
    @Transactional
    public void markAttendanceSynced(Long attendanceId) {
        Attendance attendance = attendanceRepository.findById(attendanceId)
                .orElseThrow(() -> new RuntimeException("Attendance not found"));
        attendance.setSyncStatus("synced");
        attendanceRepository.save(attendance);
    }

    /**
     * Count items needing sync
     */
    public long countItemsNeedingSync() {
        return attendanceRepository.findNeedingSync().size();
    }

    /**
     * Get all items needing sync
     */
    public List<Attendance> getItemsNeedingSync() {
        return attendanceRepository.findNeedingSync();
    }

    // === Utility Methods ===

    /**
     * Check if attendance taken for date
     */
    public boolean isAttendanceTakenForDate(LocalDate date) {
        List<Attendance> records = attendanceRepository.findByAttendanceDate(date);
        long activeStudents = studentRepository.findByActiveTrue().size();
        return records.size() >= activeStudents;
    }

    /**
     * Get missing attendance records for date
     */
    public List<Student> getMissingAttendanceForDate(LocalDate date) {
        List<Attendance> takenAttendance = attendanceRepository.findByAttendanceDate(date);
        Set<Long> recordedStudentIds = takenAttendance.stream()
                .map(a -> a.getStudent().getId())
                .collect(Collectors.toSet());

        return studentRepository.findByActiveTrue().stream()
                .filter(s -> !recordedStudentIds.contains(s.getId()))
                .collect(Collectors.toList());
    }

    /**
     * Delete attendance record
     */
    @Transactional
    public void deleteAttendance(Long attendanceId) {
        attendanceRepository.deleteById(attendanceId);
        log.info("Deleted attendance record: {}", attendanceId);
    }

    // ==================== Period-Based Attendance Methods ====================

    /**
     * Get attendance records for a specific date and period
     */
    public List<Attendance> getAttendanceByDateAndPeriod(LocalDate date, Integer period) {
        return attendanceRepository.findByDateAndPeriod(date, period);
    }

    /**
     * Get period statistics for a specific date and period
     */
    public Map<String, Object> getPeriodStatistics(LocalDate date, Integer period) {
        List<Attendance> periodRecords = attendanceRepository.findByDateAndPeriod(date, period);

        long presentCount = periodRecords.stream().filter(Attendance::isPresent).count();
        long absentCount = periodRecords.stream().filter(Attendance::isAbsent).count();
        long tardyCount = periodRecords.stream().filter(Attendance::isTardy).count();
        long totalCount = periodRecords.size();

        Map<String, Object> stats = new HashMap<>();
        stats.put("period", period);
        stats.put("periodDisplay", period == 0 ? "Homeroom" : "Period " + period);
        stats.put("present", presentCount);
        stats.put("absent", absentCount);
        stats.put("tardy", tardyCount);
        stats.put("total", totalCount);
        stats.put("unmarked", 0L); // Will be calculated by controller based on expected students

        return stats;
    }

    /**
     * Mark student present for a specific period
     */
    @Transactional
    public void markPresentForPeriod(Long studentId, LocalDate date, Integer period) {
        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new RuntimeException("Student not found"));

        // Check if attendance already exists for this student/date/period
        Optional<Attendance> existing = attendanceRepository.findByDateAndPeriod(date, period)
                .stream()
                .filter(a -> a.getStudent().getId().equals(studentId))
                .findFirst();

        if (existing.isPresent()) {
            // Update existing record
            Attendance attendance = existing.get();
            attendance.setStatus("PRESENT");
            attendance.setArrivalTime(LocalTime.now());
            attendance.setMinutesLate(null);
            attendanceRepository.save(attendance);
        } else {
            // Create new record
            Attendance attendance = Attendance.builder()
                    .student(student)
                    .attendanceDate(date)
                    .periodNumber(period)
                    .status("PRESENT")
                    .arrivalTime(LocalTime.now())
                    .excused(false)
                    .syncStatus("pending")
                    .build();
            attendanceRepository.save(attendance);
        }
    }

    /**
     * Mark student absent for a specific period
     */
    @Transactional
    public void markAbsentForPeriod(Long studentId, LocalDate date, Integer period, String reason) {
        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new RuntimeException("Student not found"));

        Optional<Attendance> existing = attendanceRepository.findByDateAndPeriod(date, period)
                .stream()
                .filter(a -> a.getStudent().getId().equals(studentId))
                .findFirst();

        if (existing.isPresent()) {
            Attendance attendance = existing.get();
            attendance.setStatus("ABSENT");
            attendance.setNotes(reason);
            attendanceRepository.save(attendance);
        } else {
            Attendance attendance = Attendance.builder()
                    .student(student)
                    .attendanceDate(date)
                    .periodNumber(period)
                    .status("ABSENT")
                    .notes(reason)
                    .excused(false)
                    .syncStatus("pending")
                    .build();
            attendanceRepository.save(attendance);
        }
    }

    /**
     * Mark student tardy for a specific period
     */
    @Transactional
    public void markTardyForPeriod(Long studentId, LocalDate date, Integer period, LocalTime arrivalTime, String notes) {
        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new RuntimeException("Student not found"));

        Optional<Attendance> existing = attendanceRepository.findByDateAndPeriod(date, period)
                .stream()
                .filter(a -> a.getStudent().getId().equals(studentId))
                .findFirst();

        if (existing.isPresent()) {
            Attendance attendance = existing.get();
            attendance.setStatus("TARDY");
            attendance.setArrivalTime(arrivalTime);
            attendance.setNotes(notes);
            // Minutes late can be calculated if we know the period start time
            attendanceRepository.save(attendance);
        } else {
            Attendance attendance = Attendance.builder()
                    .student(student)
                    .attendanceDate(date)
                    .periodNumber(period)
                    .status("TARDY")
                    .arrivalTime(arrivalTime)
                    .notes(notes)
                    .excused(false)
                    .syncStatus("pending")
                    .build();
            attendanceRepository.save(attendance);
        }
    }

    /**
     * Get students assigned to a specific period
     *
     * Uses mock roster if eduproteacher.roster.mock.enabled=true
     * Otherwise integrates with main EduScheduler schedule/enrollment data
     *
     * @param period Period number (1-8)
     * @return List of students enrolled in teacher's class for this period
     */
    public List<Student> getStudentsForPeriod(Integer period) {
        log.debug("Getting students for period {}, mock mode: {}", period, rosterMockEnabled);

        if (rosterMockEnabled) {
            // Mock mode: return all active students for testing
            // This allows teachers to test attendance UI without real enrollment data
            List<Student> students = studentRepository.findByActiveTrue();
            log.debug("Mock roster: returning {} active students", students.size());
            return students;
        } else {
            // Production mode: query actual enrollment from EduScheduler Pro
            return getEnrolledStudentsForPeriod(period);
        }
    }

    /**
     * Get enrolled students for a specific period from Heronix-SIS
     *
     * Queries the admin server API for the teacher's class roster for the given period.
     * Falls back to local student repository if API is unavailable.
     *
     * @param period Period number
     * @return List of enrolled students
     */
    @SuppressWarnings("unchecked")
    private List<Student> getEnrolledStudentsForPeriod(Integer period) {
        Long teacherId = getCurrentTeacherId();

        if (teacherId == null) {
            log.warn("Cannot fetch roster - no teacher logged in, falling back to all active students");
            return studentRepository.findByActiveTrue();
        }

        try {
            // Call the Heronix-SIS API to get roster for this period
            java.util.Map<String, Object> rosterData = adminApiClient.getTeacherRosterForPeriod(teacherId, period);

            if (rosterData == null || rosterData.get("roster") == null) {
                log.debug("No class scheduled for period {} - returning empty list", period);
                return new ArrayList<>();
            }

            // Extract the roster object
            java.util.Map<String, Object> roster = (java.util.Map<String, Object>) rosterData.get("roster");
            if (roster == null) {
                log.debug("Roster is null for period {} - no class scheduled", period);
                return new ArrayList<>();
            }

            // Extract enrolled students from roster
            List<java.util.Map<String, Object>> enrolledStudents =
                    (List<java.util.Map<String, Object>>) roster.get("students");

            if (enrolledStudents == null || enrolledStudents.isEmpty()) {
                log.debug("No students enrolled for period {}", period);
                return new ArrayList<>();
            }

            // Convert API student data to local Student entities
            // Try to find matching students in local repository, or create temporary objects
            List<Student> students = new ArrayList<>();
            for (java.util.Map<String, Object> studentData : enrolledStudents) {
                Long studentId = studentData.get("id") != null ?
                        ((Number) studentData.get("id")).longValue() : null;

                if (studentId != null) {
                    // Try to find in local repository first
                    Optional<Student> localStudent = studentRepository.findById(studentId);
                    if (localStudent.isPresent()) {
                        students.add(localStudent.get());
                    } else {
                        // Create a transient student object from API data
                        Student apiStudent = Student.builder()
                                .id(studentId)
                                .firstName((String) studentData.get("firstName"))
                                .lastName((String) studentData.get("lastName"))
                                .studentId((String) studentData.get("studentNumber"))
                                .gradeLevel(studentData.get("gradeLevel") != null ?
                                        ((Number) studentData.get("gradeLevel")).intValue() : null)
                                .active(true)
                                .build();
                        students.add(apiStudent);
                    }
                }
            }

            log.debug("Retrieved {} enrolled students for period {} from Heronix-SIS API",
                    students.size(), period);
            return students;

        } catch (Exception e) {
            log.error("Failed to fetch roster from API for period {}: {}", period, e.getMessage());
            log.warn("Falling back to all active students from local repository");
            return studentRepository.findByActiveTrue();
        }
    }

    /**
     * Get current teacher ID from session
     * Helper method for enrollment integration
     *
     * @return Teacher ID, or null if not logged in
     */
    private Long getCurrentTeacherId() {
        Long teacherId = sessionManager.getCurrentTeacherId();
        if (teacherId == null) {
            log.warn("No teacher logged in - session manager returned null");
        }
        return teacherId;
    }
}
