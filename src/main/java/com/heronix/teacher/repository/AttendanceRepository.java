package com.heronix.teacher.repository;

import com.heronix.teacher.model.domain.Attendance;
import com.heronix.teacher.model.domain.Student;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Attendance Repository
 *
 * Data access for attendance records
 *
 * @author EduScheduler Team
 * @version 1.0.0
 */
@Repository
public interface AttendanceRepository extends JpaRepository<Attendance, Long> {

    /**
     * Find attendance by student and date
     */
    Optional<Attendance> findByStudentAndAttendanceDate(Student student, LocalDate date);

    /**
     * Find all attendance for a student
     */
    List<Attendance> findByStudentOrderByAttendanceDateDesc(Student student);

    /**
     * Find attendance for a specific date
     */
    List<Attendance> findByAttendanceDate(LocalDate date);

    /**
     * Find attendance for date range
     */
    List<Attendance> findByAttendanceDateBetween(LocalDate startDate, LocalDate endDate);

    /**
     * Find all absent students for a date
     */
    @Query("SELECT a FROM Attendance a WHERE a.attendanceDate = :date AND a.status = 'ABSENT'")
    List<Attendance> findAbsentByDate(@Param("date") LocalDate date);

    /**
     * Find all tardy students for a date
     */
    @Query("SELECT a FROM Attendance a WHERE a.attendanceDate = :date AND a.status = 'TARDY'")
    List<Attendance> findTardyByDate(@Param("date") LocalDate date);

    /**
     * Find all present students for a date
     */
    @Query("SELECT a FROM Attendance a WHERE a.attendanceDate = :date AND a.status = 'PRESENT'")
    List<Attendance> findPresentByDate(@Param("date") LocalDate date);

    /**
     * Count absences for a student
     */
    @Query("SELECT COUNT(a) FROM Attendance a WHERE a.student = :student AND a.status = 'ABSENT'")
    long countAbsencesByStudent(@Param("student") Student student);

    /**
     * Count tardies for a student
     */
    @Query("SELECT COUNT(a) FROM Attendance a WHERE a.student = :student AND a.status = 'TARDY'")
    long countTardiesByStudent(@Param("student") Student student);

    /**
     * Find records needing sync
     */
    @Query("SELECT a FROM Attendance a WHERE a.syncStatus = 'pending'")
    List<Attendance> findNeedingSync();

    /**
     * Find recent attendance (last 30 days)
     */
    @Query("SELECT a FROM Attendance a WHERE a.attendanceDate >= :date ORDER BY a.attendanceDate DESC")
    List<Attendance> findRecentAttendance(@Param("date") LocalDate date);

    /**
     * Get attendance rate for a student
     */
    @Query("SELECT (COUNT(a) * 100.0 / (SELECT COUNT(a2) FROM Attendance a2 WHERE a2.student = :student)) " +
           "FROM Attendance a WHERE a.student = :student AND a.status = 'PRESENT'")
    Double getAttendanceRateByStudent(@Param("student") Student student);

    /**
     * Find students with excessive absences
     */
    @Query("SELECT a.student, COUNT(a) as absenceCount " +
           "FROM Attendance a " +
           "WHERE a.status = 'ABSENT' " +
           "GROUP BY a.student " +
           "HAVING COUNT(a) >= :threshold")
    List<Object[]> findStudentsWithExcessiveAbsences(@Param("threshold") long threshold);

    // ==================== Period-Based Attendance Queries ====================

    /**
     * Find attendance records by date and period
     */
    @Query("SELECT a FROM Attendance a WHERE a.attendanceDate = :date AND a.periodNumber = :period")
    List<Attendance> findByDateAndPeriod(@Param("date") LocalDate date, @Param("period") Integer period);

    /**
     * Count attendance by date, period, and status
     */
    @Query("SELECT COUNT(a) FROM Attendance a WHERE a.attendanceDate = :date AND a.periodNumber = :period AND a.status = :status")
    long countByDatePeriodAndStatus(@Param("date") LocalDate date, @Param("period") Integer period, @Param("status") String status);

    /**
     * Get all distinct periods for a date (to determine which tabs to show)
     */
    @Query("SELECT DISTINCT a.periodNumber FROM Attendance a WHERE a.attendanceDate = :date ORDER BY a.periodNumber")
    List<Integer> findDistinctPeriodsByDate(@Param("date") LocalDate date);
}
