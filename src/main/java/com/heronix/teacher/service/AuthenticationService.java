package com.heronix.teacher.service;

import com.heronix.teacher.model.domain.Teacher;
import com.heronix.teacher.repository.TeacherRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Authentication Service
 *
 * Handles teacher authentication, password management, and account operations
 *
 * @author Heronix-Teacher Team
 * @version 1.0.0
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AuthenticationService {

    private final TeacherRepository teacherRepository;
    private final PasswordEncoder passwordEncoder;

    /**
     * Authenticate teacher with employee ID and password
     *
     * @param employeeId The employee ID
     * @param password The password (plain text)
     * @return Optional containing the teacher if authentication successful
     */
    public Optional<Teacher> authenticate(String employeeId, String password) {
        log.info("Attempting authentication for employee ID: {}", employeeId);

        Optional<Teacher> teacherOpt = teacherRepository.findByEmployeeId(employeeId);

        if (teacherOpt.isEmpty()) {
            log.warn("Teacher not found: {}", employeeId);
            return Optional.empty();
        }

        Teacher teacher = teacherOpt.get();

        if (!teacher.canLogin()) {
            log.warn("Teacher account is inactive: {}", employeeId);
            return Optional.empty();
        }

        if (passwordEncoder.matches(password, teacher.getPassword())) {
            log.info("Authentication successful for: {} ({})", teacher.getFullName(), employeeId);
            return Optional.of(teacher);
        } else {
            log.warn("Invalid password for: {}", employeeId);
            return Optional.empty();
        }
    }

    /**
     * Create a new teacher account
     *
     * @param employeeId The employee ID
     * @param firstName First name
     * @param lastName Last name
     * @param email Email address
     * @param password Plain text password (will be encrypted)
     * @return The created teacher
     */
    @Transactional
    public Teacher createTeacher(String employeeId, String firstName, String lastName,
                                 String email, String password) {
        return createTeacher(employeeId, firstName, lastName, email, password, null);
    }

    /**
     * Create a new teacher account with department
     *
     * @param employeeId The employee ID
     * @param firstName First name
     * @param lastName Last name
     * @param email Email address
     * @param password Plain text password (will be encrypted)
     * @param department Department (optional)
     * @return The created teacher
     */
    @Transactional
    public Teacher createTeacher(String employeeId, String firstName, String lastName,
                                 String email, String password, String department) {
        if (teacherRepository.existsByEmployeeId(employeeId)) {
            throw new IllegalArgumentException("Employee ID already exists: " + employeeId);
        }

        if (teacherRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("Email already exists: " + email);
        }

        Teacher teacher = Teacher.builder()
                .employeeId(employeeId)
                .firstName(firstName)
                .lastName(lastName)
                .email(email)
                .password(passwordEncoder.encode(password))
                .department(department)
                .active(true)
                .syncStatus("pending")
                .createdDate(LocalDateTime.now())
                .modifiedDate(LocalDateTime.now())
                .build();

        Teacher saved = teacherRepository.save(teacher);
        log.info("Created teacher account: {} ({})", saved.getFullName(), saved.getEmployeeId());

        return saved;
    }

    /**
     * Change password for a teacher
     *
     * @param teacherId The teacher ID
     * @param oldPassword The current password
     * @param newPassword The new password
     * @return true if password changed successfully, false otherwise
     */
    @Transactional
    public boolean changePassword(Long teacherId, String oldPassword, String newPassword) {
        Optional<Teacher> teacherOpt = teacherRepository.findById(teacherId);

        if (teacherOpt.isEmpty()) {
            log.error("Teacher not found with ID: {}", teacherId);
            return false;
        }

        Teacher teacher = teacherOpt.get();

        if (!passwordEncoder.matches(oldPassword, teacher.getPassword())) {
            log.warn("Old password incorrect for teacher ID: {}", teacherId);
            return false;
        }

        teacher.setPassword(passwordEncoder.encode(newPassword));
        teacher.setModifiedDate(LocalDateTime.now());
        teacher.setSyncStatus("pending"); // Mark for sync
        teacherRepository.save(teacher);

        log.info("Password changed successfully for teacher: {} ({})",
                teacher.getFullName(), teacher.getEmployeeId());
        return true;
    }

    /**
     * Reset password for a teacher (admin function)
     *
     * @param employeeId The employee ID
     * @param newPassword The new password
     * @return true if reset successful, false otherwise
     */
    @Transactional
    public boolean resetPassword(String employeeId, String newPassword) {
        Optional<Teacher> teacherOpt = teacherRepository.findByEmployeeId(employeeId);

        if (teacherOpt.isEmpty()) {
            log.error("Teacher not found: {}", employeeId);
            return false;
        }

        Teacher teacher = teacherOpt.get();
        teacher.setPassword(passwordEncoder.encode(newPassword));
        teacher.setModifiedDate(LocalDateTime.now());
        teacher.setSyncStatus("pending");
        teacherRepository.save(teacher);

        log.info("Password reset for teacher: {} ({})",
                teacher.getFullName(), teacher.getEmployeeId());
        return true;
    }

    /**
     * Deactivate a teacher account
     *
     * @param teacherId The teacher ID
     * @return true if deactivated successfully
     */
    @Transactional
    public boolean deactivateAccount(Long teacherId) {
        Optional<Teacher> teacherOpt = teacherRepository.findById(teacherId);

        if (teacherOpt.isEmpty()) {
            return false;
        }

        Teacher teacher = teacherOpt.get();
        teacher.setActive(false);
        teacher.setModifiedDate(LocalDateTime.now());
        teacher.setSyncStatus("pending");
        teacherRepository.save(teacher);

        log.info("Deactivated teacher account: {} ({})",
                teacher.getFullName(), teacher.getEmployeeId());
        return true;
    }

    /**
     * Reactivate a teacher account
     *
     * @param teacherId The teacher ID
     * @return true if reactivated successfully
     */
    @Transactional
    public boolean reactivateAccount(Long teacherId) {
        Optional<Teacher> teacherOpt = teacherRepository.findById(teacherId);

        if (teacherOpt.isEmpty()) {
            return false;
        }

        Teacher teacher = teacherOpt.get();
        teacher.setActive(true);
        teacher.setModifiedDate(LocalDateTime.now());
        teacher.setSyncStatus("pending");
        teacherRepository.save(teacher);

        log.info("Reactivated teacher account: {} ({})",
                teacher.getFullName(), teacher.getEmployeeId());
        return true;
    }

    /**
     * Update teacher profile information
     *
     * @param teacherId The teacher ID
     * @param email New email (optional)
     * @param department New department (optional)
     * @return true if updated successfully
     */
    @Transactional
    public boolean updateProfile(Long teacherId, String email, String department) {
        Optional<Teacher> teacherOpt = teacherRepository.findById(teacherId);

        if (teacherOpt.isEmpty()) {
            return false;
        }

        Teacher teacher = teacherOpt.get();

        if (email != null && !email.equals(teacher.getEmail())) {
            if (teacherRepository.existsByEmail(email)) {
                throw new IllegalArgumentException("Email already in use: " + email);
            }
            teacher.setEmail(email);
        }

        if (department != null) {
            teacher.setDepartment(department);
        }

        teacher.setModifiedDate(LocalDateTime.now());
        teacher.setSyncStatus("pending");
        teacherRepository.save(teacher);

        log.info("Updated profile for teacher: {} ({})",
                teacher.getFullName(), teacher.getEmployeeId());
        return true;
    }
}
