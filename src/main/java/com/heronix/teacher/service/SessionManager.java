package com.heronix.teacher.service;

import com.heronix.teacher.model.domain.Teacher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * Session Manager
 *
 * Manages the currently logged-in teacher's session, including
 * login state, session timeout, and activity tracking
 *
 * @author Heronix-Teacher Team
 * @version 1.0.0
 */
@Service
@Slf4j
public class SessionManager {

    private Teacher currentTeacher;
    private LocalDateTime loginTime;
    private LocalDateTime lastActivityTime;

    // Session timeout: 8 hours (full school day)
    private static final int SESSION_TIMEOUT_HOURS = 8;

    /**
     * Set the currently logged-in teacher
     *
     * @param teacher The teacher who logged in
     */
    public void login(Teacher teacher) {
        this.currentTeacher = teacher;
        this.loginTime = LocalDateTime.now();
        this.lastActivityTime = LocalDateTime.now();
        log.info("Teacher logged in: {} ({}) at {}",
                teacher.getFullName(), teacher.getEmployeeId(), loginTime);
    }

    /**
     * Log out the current teacher
     */
    public void logout() {
        if (currentTeacher != null) {
            log.info("Teacher logged out: {} ({}) - Session duration: {} minutes",
                    currentTeacher.getFullName(),
                    currentTeacher.getEmployeeId(),
                    getSessionDuration());
        }
        this.currentTeacher = null;
        this.loginTime = null;
        this.lastActivityTime = null;
    }

    /**
     * Get the currently logged-in teacher
     *
     * @return The current teacher, or null if not logged in
     */
    public Teacher getCurrentTeacher() {
        updateActivity();
        return currentTeacher;
    }

    /**
     * Get the current teacher's ID
     *
     * @return The teacher ID, or null if not logged in
     */
    public Long getCurrentTeacherId() {
        return currentTeacher != null ? currentTeacher.getId() : null;
    }

    /**
     * Get the current teacher's employee ID
     *
     * @return The employee ID, or null if not logged in
     */
    public String getCurrentEmployeeId() {
        return currentTeacher != null ? currentTeacher.getEmployeeId() : null;
    }

    /**
     * Get the current teacher's full name
     *
     * @return The full name, or "Guest" if not logged in
     */
    public String getCurrentTeacherName() {
        return currentTeacher != null ? currentTeacher.getFullName() : "Guest";
    }

    /**
     * Check if a teacher is logged in
     *
     * @return true if logged in, false otherwise
     */
    public boolean isLoggedIn() {
        if (currentTeacher == null) {
            return false;
        }

        // Check for session timeout
        if (isSessionExpired()) {
            log.warn("Session expired for teacher: {} - Last activity: {}",
                    currentTeacher.getEmployeeId(), lastActivityTime);
            logout();
            return false;
        }

        return true;
    }

    /**
     * Check if session has expired due to inactivity
     *
     * @return true if expired, false otherwise
     */
    public boolean isSessionExpired() {
        if (lastActivityTime == null) {
            return true;
        }

        LocalDateTime expirationTime = lastActivityTime.plusHours(SESSION_TIMEOUT_HOURS);
        return LocalDateTime.now().isAfter(expirationTime);
    }

    /**
     * Update last activity time (called on user interaction)
     */
    public void updateActivity() {
        this.lastActivityTime = LocalDateTime.now();
    }

    /**
     * Get session duration in minutes
     *
     * @return Duration in minutes since login, or 0 if not logged in
     */
    public long getSessionDuration() {
        if (loginTime == null) {
            return 0;
        }
        return Duration.between(loginTime, LocalDateTime.now()).toMinutes();
    }

    /**
     * Get time remaining before session expires
     *
     * @return Minutes until session expires, or 0 if not logged in
     */
    public long getTimeUntilExpiration() {
        if (lastActivityTime == null) {
            return 0;
        }

        LocalDateTime expirationTime = lastActivityTime.plusHours(SESSION_TIMEOUT_HOURS);
        Duration remaining = Duration.between(LocalDateTime.now(), expirationTime);

        return Math.max(0, remaining.toMinutes());
    }

    /**
     * Check if session will expire soon (within 30 minutes)
     *
     * @return true if session expires within 30 minutes
     */
    public boolean isSessionExpiringSoon() {
        return isLoggedIn() && getTimeUntilExpiration() <= 30;
    }

    /**
     * Require login - throws exception if not logged in
     *
     * @throws IllegalStateException if not logged in
     */
    public void requireLogin() {
        if (!isLoggedIn()) {
            throw new IllegalStateException("User must be logged in to perform this action");
        }
    }

    /**
     * Get session information for display
     *
     * @return Session information string
     */
    public String getSessionInfo() {
        if (!isLoggedIn()) {
            return "Not logged in";
        }

        return String.format("Logged in as: %s (%s) | Session: %d minutes | Expires in: %d minutes",
                currentTeacher.getFullName(),
                currentTeacher.getEmployeeId(),
                getSessionDuration(),
                getTimeUntilExpiration());
    }
}
