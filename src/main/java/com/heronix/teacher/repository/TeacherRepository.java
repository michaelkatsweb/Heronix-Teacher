package com.heronix.teacher.repository;

import com.heronix.teacher.model.domain.Teacher;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for Teacher entity
 *
 * Provides data access methods for teacher authentication and management
 *
 * @author Heronix-Teacher Team
 * @version 1.0.0
 */
@Repository
public interface TeacherRepository extends JpaRepository<Teacher, Long> {

    /**
     * Find teacher by employee ID (for login)
     *
     * @param employeeId The employee ID
     * @return Optional containing the teacher if found
     */
    Optional<Teacher> findByEmployeeId(String employeeId);

    /**
     * Find teacher by email
     *
     * @param email The email address
     * @return Optional containing the teacher if found
     */
    Optional<Teacher> findByEmail(String email);

    /**
     * Find all active teachers
     *
     * @return List of active teachers
     */
    List<Teacher> findByActiveTrue();

    /**
     * Check if employee ID exists
     *
     * @param employeeId The employee ID to check
     * @return true if exists, false otherwise
     */
    boolean existsByEmployeeId(String employeeId);

    /**
     * Check if email exists
     *
     * @param email The email to check
     * @return true if exists, false otherwise
     */
    boolean existsByEmail(String email);

    /**
     * Find teachers needing sync
     *
     * @return List of teachers with pending or conflict sync status
     */
    @Query("SELECT t FROM Teacher t WHERE t.syncStatus = 'pending' OR t.syncStatus = 'conflict'")
    List<Teacher> findNeedingSync();
}
