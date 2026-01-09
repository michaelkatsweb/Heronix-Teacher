package com.heronix.teacher.repository;

import com.heronix.teacher.model.domain.Student;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for Student entity
 *
 * Provides data access methods for student roster
 *
 * @author EduScheduler Team
 * @version 1.0.0
 */
@Repository
public interface StudentRepository extends JpaRepository<Student, Long> {

    /**
     * Find student by student ID
     */
    Optional<Student> findByStudentId(String studentId);

    /**
     * Find all active students
     */
    List<Student> findByActiveTrue();

    /**
     * Find all inactive students
     */
    List<Student> findByActiveFalse();

    /**
     * Find students by grade level
     */
    List<Student> findByGradeLevelAndActiveTrue(Integer gradeLevel);

    /**
     * Find students with IEP
     */
    List<Student> findByHasIepTrueAndActiveTrue();

    /**
     * Find students with 504 plan
     */
    List<Student> findByHas504TrueAndActiveTrue();

    /**
     * Find students needing sync
     */
    @Query("SELECT s FROM Student s WHERE s.syncStatus = 'pending' OR s.syncStatus = 'conflict'")
    List<Student> findNeedingSync();

    /**
     * Search students by name
     */
    @Query("SELECT s FROM Student s WHERE " +
           "LOWER(s.firstName) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
           "LOWER(s.lastName) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
           "LOWER(s.studentId) LIKE LOWER(CONCAT('%', :searchTerm, '%'))")
    List<Student> searchByName(@Param("searchTerm") String searchTerm);

    /**
     * Count active students
     */
    long countByActiveTrue();

    /**
     * Find students with GPA below threshold
     */
    @Query("SELECT s FROM Student s WHERE s.active = true AND s.currentGpa < :threshold")
    List<Student> findByGpaBelowThreshold(@Param("threshold") Double threshold);
}
