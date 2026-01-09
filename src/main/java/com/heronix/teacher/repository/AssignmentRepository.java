package com.heronix.teacher.repository;

import com.heronix.teacher.model.domain.Assignment;
import com.heronix.teacher.model.domain.AssignmentCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

/**
 * Repository for Assignment entity
 *
 * Provides data access methods for assignments
 *
 * @author EduScheduler Team
 * @version 1.0.0
 */
@Repository
public interface AssignmentRepository extends JpaRepository<Assignment, Long> {

    /**
     * Find assignments by course ID
     */
    List<Assignment> findByCourseIdAndActiveTrue(Long courseId);

    /**
     * Find assignments by category
     */
    List<Assignment> findByCategoryAndActiveTrue(AssignmentCategory category);

    /**
     * Find all active assignments
     */
    List<Assignment> findByActiveTrue();

    /**
     * Find assignments due on or before date
     */
    @Query("SELECT a FROM Assignment a WHERE a.active = true AND a.dueDate <= :date")
    List<Assignment> findDueByDate(@Param("date") LocalDate date);

    /**
     * Find overdue assignments
     */
    @Query("SELECT a FROM Assignment a WHERE a.active = true AND a.dueDate < :today")
    List<Assignment> findOverdue(@Param("today") LocalDate today);

    /**
     * Find assignments needing sync
     */
    @Query("SELECT a FROM Assignment a WHERE a.syncStatus = 'pending' OR a.syncStatus = 'conflict'")
    List<Assignment> findNeedingSync();

    /**
     * Find assignments by course name
     */
    List<Assignment> findByCourseNameAndActiveTrue(String courseName);

    /**
     * Count assignments by course
     */
    long countByCourseIdAndActiveTrue(Long courseId);

    /**
     * Find recent assignments
     */
    @Query("SELECT a FROM Assignment a WHERE a.active = true ORDER BY a.createdDate DESC")
    List<Assignment> findRecent();
}
