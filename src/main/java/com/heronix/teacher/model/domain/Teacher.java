package com.heronix.teacher.model.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Teacher Entity for Heronix-Teacher Application
 *
 * Represents a teacher user with authentication credentials and profile information.
 * Each teacher can only access their own students, assignments, grades, and attendance data.
 *
 * @author Heronix-Teacher Team
 * @version 1.0.0
 * @since 2025-11-29
 */
@Entity
@Table(name = "teachers")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Teacher {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Employee ID - Used for login (unique identifier)
     * Example: "T001", "EMP12345"
     */
    @Column(nullable = false, unique = true, name = "employee_id")
    private String employeeId;

    /**
     * Teacher's first name
     */
    @Column(nullable = false, name = "first_name")
    private String firstName;

    /**
     * Teacher's last name
     */
    @Column(nullable = false, name = "last_name")
    private String lastName;

    /**
     * Email address (for communication and password reset)
     */
    @Column(nullable = false, name = "email")
    private String email;

    /**
     * Encrypted password (BCrypt)
     * NEVER store plain text passwords!
     */
    @Column(nullable = false, name = "password")
    private String password;

    /**
     * Timestamp when password expires
     * Teachers must change password when this date is reached
     * Synced from admin system (default: 60 days)
     */
    @Column(name = "password_expires_at")
    private LocalDateTime passwordExpiresAt;

    /**
     * Flag to force password change on next login
     * Set to true for new accounts or after password reset
     * Synced from admin system
     */
    @Column(name = "must_change_password")
    private Boolean mustChangePassword = false;

    /**
     * Timestamp when password was last changed
     * Used to track password history and enforce expiration
     * Synced from admin system
     */
    @Column(name = "password_changed_at")
    private LocalDateTime passwordChangedAt;

    /**
     * Department or subject area
     * Example: "Mathematics", "English", "Science", "Special Education"
     */
    @Column(name = "department")
    private String department;

    /**
     * Phone number for contact
     */
    @Column(name = "phone_number")
    private String phoneNumber;

    /**
     * Account active status
     * Inactive accounts cannot log in
     */
    @Column(nullable = false, name = "active")
    private Boolean active = true;

    // ========================================================================
    // SYNC FIELDS (for PostgreSQL synchronization)
    // ========================================================================

    /**
     * Sync status: pending, synced, conflict
     */
    @Column(name = "sync_status")
    private String syncStatus = "pending";

    /**
     * Last successful sync timestamp
     */
    @Column(name = "last_sync_time")
    private LocalDateTime lastSyncTime;

    // ========================================================================
    // AUDIT FIELDS
    // ========================================================================

    /**
     * Account creation date
     */
    @Column(name = "created_date", updatable = false)
    private LocalDateTime createdDate;

    /**
     * Last modification date
     */
    @Column(name = "modified_date")
    private LocalDateTime modifiedDate;

    // ========================================================================
    // HELPER METHODS
    // ========================================================================

    /**
     * Get full name for display
     *
     * @return First name + Last name
     */
    public String getFullName() {
        String first = (firstName != null) ? firstName : "";
        String last = (lastName != null) ? lastName : "";
        return (first + " " + last).trim();
    }

    /**
     * Get display name with title
     *
     * @return "Mr./Ms. LastName" format
     */
    public String getDisplayName() {
        return "Teacher " + lastName;
    }

    /**
     * Check if account is active and can log in
     *
     * @return true if active, false otherwise
     */
    public boolean canLogin() {
        return Boolean.TRUE.equals(active);
    }

    // ========================================================================
    // JPA LIFECYCLE CALLBACKS
    // ========================================================================

    @PrePersist
    protected void onCreate() {
        createdDate = LocalDateTime.now();
        modifiedDate = LocalDateTime.now();
        if (active == null) {
            active = true;
        }
        if (syncStatus == null) {
            syncStatus = "pending";
        }
    }

    @PreUpdate
    protected void onUpdate() {
        modifiedDate = LocalDateTime.now();
    }
}
