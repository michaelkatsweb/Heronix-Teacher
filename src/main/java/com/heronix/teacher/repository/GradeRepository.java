package com.heronix.teacher.repository;

import com.heronix.teacher.model.domain.Grade;
import com.heronix.teacher.model.domain.Student;
import com.heronix.teacher.model.domain.Assignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for Grade entity
 *
 * Provides data access methods for student grades
 *
 * @author EduScheduler Team
 * @version 1.0.0
 */
@Repository
public interface GradeRepository extends JpaRepository<Grade, Long> {

    /**
     * Find grade by student and assignment
     */
    Optional<Grade> findByStudentAndAssignment(Student student, Assignment assignment);

    /**
     * Find all grades for a student
     */
    List<Grade> findByStudent(Student student);

    /**
     * Find all grades for an assignment
     */
    List<Grade> findByAssignment(Assignment assignment);

    /**
     * Find grades by student ID
     */
    @Query("SELECT g FROM Grade g WHERE g.student.id = :studentId")
    List<Grade> findByStudentId(@Param("studentId") Long studentId);

    /**
     * Find grades by assignment ID
     */
    @Query("SELECT g FROM Grade g WHERE g.assignment.id = :assignmentId")
    List<Grade> findByAssignmentId(@Param("assignmentId") Long assignmentId);

    /**
     * Find missing grades for a student
     */
    @Query("SELECT g FROM Grade g WHERE g.student.id = :studentId AND g.missing = true")
    List<Grade> findMissingByStudentId(@Param("studentId") Long studentId);

    /**
     * Find late grades for a student
     */
    @Query("SELECT g FROM Grade g WHERE g.student.id = :studentId AND g.late = true")
    List<Grade> findLateByStudentId(@Param("studentId") Long studentId);

    /**
     * Find failing grades (< 60%)
     */
    @Query("SELECT g FROM Grade g WHERE g.student.id = :studentId AND " +
           "(g.score / g.assignment.maxPoints * 100) < 60")
    List<Grade> findFailingByStudentId(@Param("studentId") Long studentId);

    /**
     * Find grades needing sync
     */
    @Query("SELECT g FROM Grade g WHERE g.syncStatus = 'pending' OR g.syncStatus = 'conflict'")
    List<Grade> findNeedingSync();

    /**
     * Count grades for assignment
     */
    long countByAssignment(Assignment assignment);

    /**
     * Count missing grades for assignment
     */
    @Query("SELECT COUNT(g) FROM Grade g WHERE g.assignment.id = :assignmentId AND g.missing = true")
    long countMissingByAssignmentId(@Param("assignmentId") Long assignmentId);

    /**
     * Calculate average score for assignment
     */
    @Query("SELECT AVG(g.score) FROM Grade g WHERE g.assignment.id = :assignmentId AND g.score IS NOT NULL")
    Double getAverageScoreByAssignmentId(@Param("assignmentId") Long assignmentId);

    /**
     * Calculate GPA for student
     */
    @Query("SELECT AVG(g.gpaPoints) FROM Grade g WHERE g.student.id = :studentId AND g.gpaPoints IS NOT NULL")
    Double calculateGpaByStudentId(@Param("studentId") Long studentId);

    /**
     * Find all grades for course
     */
    @Query("SELECT g FROM Grade g WHERE g.assignment.courseId = :courseId")
    List<Grade> findByCourseId(@Param("courseId") Long courseId);
}
