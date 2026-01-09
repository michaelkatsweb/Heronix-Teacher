package com.heronix.teacher.repository;

import com.heronix.teacher.model.domain.AssignmentCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for AssignmentCategory entity
 *
 * Provides data access for assignment categories with weighted grading
 *
 * @author EduScheduler Team
 * @version 1.0.0
 */
@Repository
public interface AssignmentCategoryRepository extends JpaRepository<AssignmentCategory, Long> {

    /**
     * Find all active categories
     */
    List<AssignmentCategory> findByActiveTrueOrderByDisplayOrderAsc();

    /**
     * Find categories by course ID
     */
    List<AssignmentCategory> findByCourseIdAndActiveTrueOrderByDisplayOrderAsc(Long courseId);

    /**
     * Find category by name and course
     */
    Optional<AssignmentCategory> findByNameAndCourseId(String name, Long courseId);

    /**
     * Find global categories (no course ID)
     */
    @Query("SELECT c FROM AssignmentCategory c WHERE c.courseId IS NULL AND c.active = true ORDER BY c.displayOrder ASC")
    List<AssignmentCategory> findGlobalCategories();

    /**
     * Find extra credit categories
     */
    List<AssignmentCategory> findByIsExtraCreditTrueAndActiveTrue();

    /**
     * Find categories needing sync
     */
    @Query("SELECT c FROM AssignmentCategory c WHERE LOWER(c.syncStatus) = 'pending'")
    List<AssignmentCategory> findNeedingSync();

    /**
     * Count active categories
     */
    long countByActiveTrue();

    /**
     * Find categories with drop lowest enabled
     */
    @Query("SELECT c FROM AssignmentCategory c WHERE c.dropLowest > 0 AND c.active = true")
    List<AssignmentCategory> findCategoriesWithDropLowest();

    /**
     * Check if category name exists for course
     */
    boolean existsByNameAndCourseId(String name, Long courseId);
}
