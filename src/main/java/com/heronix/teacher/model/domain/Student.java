package com.heronix.teacher.model.domain;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Student entity for teacher's class roster
 *
 * Synced from EduScheduler-Pro admin server
 * Stored locally for offline access
 *
 * @author EduScheduler Team
 * @version 1.0.0
 */
@Entity
@Table(name = "students")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Student {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Unique student ID from admin system
     */
    @Column(name = "student_id", unique = true, nullable = false)
    private String studentId;

    /**
     * Student's first name
     */
    @Column(name = "first_name", nullable = false)
    private String firstName;

    /**
     * Student's last name
     */
    @Column(name = "last_name", nullable = false)
    private String lastName;

    /**
     * Grade level (9, 10, 11, 12)
     */
    @Column(name = "grade_level")
    private Integer gradeLevel;

    /**
     * Student email
     */
    @Column(name = "email")
    private String email;

    /**
     * Parent/guardian email for communication
     */
    @Column(name = "parent_email")
    private String parentEmail;

    /**
     * Date of birth
     */
    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    /**
     * Current cumulative GPA
     */
    @Column(name = "current_gpa")
    private Double currentGpa;

    /**
     * Active status
     */
    @Column(name = "active")
    private Boolean active = true;

    /**
     * IEP student flag
     */
    @Column(name = "has_iep")
    private Boolean hasIep = false;

    /**
     * 504 plan flag
     */
    @Column(name = "has_504")
    private Boolean has504 = false;

    /**
     * Special notes for teacher
     */
    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    /**
     * Unique barcode/QR code for student identification
     * Auto-generated when student is registered
     * Format: STU-[STUDENT_ID]-[CHECKSUM]
     */
    @Column(name = "barcode", unique = true)
    private String barcode;

    /**
     * Sync status: synced, pending, conflict
     */
    @Column(name = "sync_status")
    private String syncStatus = "synced";

    /**
     * Last modified timestamp
     */
    @Column(name = "last_modified")
    private LocalDateTime lastModified;

    /**
     * Created timestamp
     */
    @Column(name = "created_date")
    private LocalDateTime createdDate;

    /**
     * Get full name
     */
    public String getFullName() {
        return firstName + " " + lastName;
    }

    /**
     * Check if needs sync
     */
    public boolean needsSync() {
        return "pending".equalsIgnoreCase(syncStatus);
    }

    /**
     * Check if has conflict
     */
    public boolean hasConflict() {
        return "conflict".equalsIgnoreCase(syncStatus);
    }

    /**
     * Pre-persist callback
     */
    @PrePersist
    protected void onCreate() {
        createdDate = LocalDateTime.now();
        lastModified = LocalDateTime.now();
    }

    /**
     * Pre-update callback
     */
    @PreUpdate
    protected void onUpdate() {
        lastModified = LocalDateTime.now();
    }
}
