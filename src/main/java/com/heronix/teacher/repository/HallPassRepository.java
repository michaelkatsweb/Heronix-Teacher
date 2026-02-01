package com.heronix.teacher.repository;

import com.heronix.teacher.model.domain.HallPass;
import com.heronix.teacher.model.domain.Student;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * Hall Pass Repository
 *
 * Data access for hall pass records
 *
 * @author EduScheduler Team
 * @version 1.0.0
 */
@Repository
public interface HallPassRepository extends JpaRepository<HallPass, Long> {

    /**
     * Find all hall passes for a student
     */
    List<HallPass> findByStudentOrderByPassDateDescTimeOutDesc(Student student);

    /**
     * Find hall passes for a specific date
     */
    List<HallPass> findByPassDateOrderByTimeOutDesc(LocalDate date);

    /**
     * Find hall passes for date range
     */
    List<HallPass> findByPassDateBetweenOrderByPassDateDescTimeOutDesc(LocalDate startDate, LocalDate endDate);

    /**
     * Find active hall passes (students currently out)
     */
    @Query("SELECT h FROM HallPass h WHERE h.status = 'ACTIVE' ORDER BY h.timeOut ASC")
    List<HallPass> findActivePasses();

    /**
     * Find active passes for today
     */
    @Query("SELECT h FROM HallPass h WHERE h.passDate = :date AND h.status = 'ACTIVE' ORDER BY h.timeOut ASC")
    List<HallPass> findActivePasses(@Param("date") LocalDate date);

    /**
     * Find overdue passes (active for more than threshold minutes)
     */
    @Query("SELECT h FROM HallPass h WHERE h.status = 'ACTIVE' AND h.passDate = :date AND h.timeOut < :thresholdTime")
    List<HallPass> findOverduePasses(@Param("date") LocalDate date, @Param("thresholdTime") LocalTime thresholdTime);

    /**
     * Find flagged passes
     */
    @Query("SELECT h FROM HallPass h WHERE h.flagged = true ORDER BY h.passDate DESC, h.timeOut DESC")
    List<HallPass> findFlaggedPasses();

    /**
     * Find passes by destination
     */
    List<HallPass> findByDestinationOrderByPassDateDescTimeOutDesc(String destination);

    /**
     * Find passes by destination for a specific date
     */
    @Query("SELECT h FROM HallPass h WHERE h.passDate = :date AND h.destination = :destination ORDER BY h.timeOut DESC")
    List<HallPass> findByDateAndDestination(@Param("date") LocalDate date, @Param("destination") String destination);

    /**
     * Count active passes
     */
    @Query("SELECT COUNT(h) FROM HallPass h WHERE h.status = 'ACTIVE'")
    long countActivePasses();

    /**
     * Count passes for today
     */
    @Query("SELECT COUNT(h) FROM HallPass h WHERE h.passDate = :date")
    long countPassesByDate(@Param("date") LocalDate date);

    /**
     * Count passes for a student
     */
    long countByStudent(Student student);

    /**
     * Count passes for a student on a specific date
     */
    @Query("SELECT COUNT(h) FROM HallPass h WHERE h.student = :student AND h.passDate = :date")
    long countByStudentAndDate(@Param("student") Student student, @Param("date") LocalDate date);

    /**
     * Find records needing sync
     */
    @Query("SELECT h FROM HallPass h WHERE h.syncStatus = 'pending'")
    List<HallPass> findNeedingSync();

    /**
     * Find records by sync status
     */
    List<HallPass> findBySyncStatus(String syncStatus);

    /**
     * Find recent passes (last 7 days)
     */
    @Query("SELECT h FROM HallPass h WHERE h.passDate >= :date ORDER BY h.passDate DESC, h.timeOut DESC")
    List<HallPass> findRecentPasses(@Param("date") LocalDate date);

    /**
     * Get average duration by destination
     */
    @Query("SELECT h.destination, AVG(h.durationMinutes) FROM HallPass h WHERE h.durationMinutes IS NOT NULL GROUP BY h.destination")
    List<Object[]> getAverageDurationByDestination();

    /**
     * Find students with excessive passes (more than threshold in a day)
     */
    @Query("SELECT h.student, COUNT(h) as passCount " +
           "FROM HallPass h " +
           "WHERE h.passDate = :date " +
           "GROUP BY h.student " +
           "HAVING COUNT(h) >= :threshold")
    List<Object[]> findStudentsWithExcessivePasses(@Param("date") LocalDate date, @Param("threshold") long threshold);

    /**
     * Get pass statistics for a date
     */
    @Query("SELECT h.destination, COUNT(h), AVG(h.durationMinutes) " +
           "FROM HallPass h " +
           "WHERE h.passDate = :date AND h.durationMinutes IS NOT NULL " +
           "GROUP BY h.destination")
    List<Object[]> getPassStatisticsByDate(@Param("date") LocalDate date);
}
