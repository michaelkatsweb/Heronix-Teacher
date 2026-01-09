package com.heronix.teacher.repository;

import com.heronix.teacher.model.domain.Club;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Club Repository
 *
 * Data access for clubs and extracurricular activities
 *
 * @author EduScheduler Team
 * @version 1.0.0
 */
@Repository
public interface ClubRepository extends JpaRepository<Club, Long> {

    /**
     * Find club by name
     */
    Optional<Club> findByName(String name);

    /**
     * Find all active clubs
     */
    List<Club> findByActiveTrueOrderByNameAsc();

    /**
     * Find clubs by category
     */
    List<Club> findByCategoryAndActiveTrueOrderByNameAsc(String category);

    /**
     * Find clubs by advisor
     */
    List<Club> findByAdvisorNameOrderByNameAsc(String advisorName);

    /**
     * Find clubs by meeting day
     */
    List<Club> findByMeetingDayAndActiveTrueOrderByMeetingTimeAsc(String meetingDay);

    /**
     * Find clubs with available capacity
     */
    @Query("SELECT c FROM Club c WHERE c.active = true AND (c.maxCapacity IS NULL OR c.currentEnrollment < c.maxCapacity) ORDER BY c.name")
    List<Club> findClubsWithAvailability();

    /**
     * Find clubs at capacity
     */
    @Query("SELECT c FROM Club c WHERE c.active = true AND c.maxCapacity IS NOT NULL AND c.currentEnrollment >= c.maxCapacity ORDER BY c.name")
    List<Club> findClubsAtCapacity();

    /**
     * Find clubs requiring approval
     */
    List<Club> findByRequiresApprovalTrueAndActiveTrueOrderByNameAsc();

    /**
     * Find clubs within date range (seasonal)
     */
    @Query("SELECT c FROM Club c WHERE c.active = true AND ((c.startDate IS NULL AND c.endDate IS NULL) OR (c.startDate <= :date AND (c.endDate IS NULL OR c.endDate >= :date))) ORDER BY c.name")
    List<Club> findActiveForDate(@Param("date") LocalDate date);

    /**
     * Find clubs with upcoming meetings
     */
    @Query("SELECT c FROM Club c WHERE c.active = true AND c.nextMeetingDate >= :date ORDER BY c.nextMeetingDate ASC")
    List<Club> findWithUpcomingMeetings(@Param("date") LocalDate date);

    /**
     * Get distinct categories
     */
    @Query("SELECT DISTINCT c.category FROM Club c WHERE c.category IS NOT NULL AND c.active = true ORDER BY c.category")
    List<String> findDistinctCategories();

    /**
     * Get distinct meeting days
     */
    @Query("SELECT DISTINCT c.meetingDay FROM Club c WHERE c.meetingDay IS NOT NULL AND c.active = true ORDER BY c.meetingDay")
    List<String> findDistinctMeetingDays();

    /**
     * Count active clubs
     */
    long countByActiveTrue();

    /**
     * Count clubs by category
     */
    long countByCategoryAndActiveTrue(String category);

    /**
     * Find clubs needing sync
     */
    @Query("SELECT c FROM Club c WHERE c.syncStatus = 'pending'")
    List<Club> findNeedingSync();

    /**
     * Search clubs by name or description
     */
    @Query("SELECT c FROM Club c WHERE c.active = true AND (LOWER(c.name) LIKE LOWER(CONCAT('%', :search, '%')) OR LOWER(c.description) LIKE LOWER(CONCAT('%', :search, '%'))) ORDER BY c.name")
    List<Club> searchClubs(@Param("search") String search);

    /**
     * Find clubs by student membership
     */
    @Query("SELECT c FROM Club c JOIN c.members m WHERE m.id = :studentId AND c.active = true ORDER BY c.name")
    List<Club> findByStudentId(@Param("studentId") Long studentId);

    /**
     * Count student's club memberships
     */
    @Query("SELECT COUNT(c) FROM Club c JOIN c.members m WHERE m.id = :studentId AND c.active = true")
    long countStudentMemberships(@Param("studentId") Long studentId);
}
