package com.heronix.teacher.model.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Attendance entity - Daily attendance tracking
 *
 * Features:
 * - Present/Absent/Tardy/Excused status
 * - Arrival time tracking
 * - Notes for absences/tardiness
 * - Auto-sync to main server
 *
 * @author EduScheduler Team
 * @version 1.0.0
 */
@Entity
@Table(name = "attendance",
       uniqueConstraints = @UniqueConstraint(columnNames = {"student_id", "attendance_date", "period_number"}))
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Attendance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Student reference
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    /**
     * Date of attendance record
     */
    @Column(name = "attendance_date", nullable = false)
    private LocalDate attendanceDate;

    /**
     * Period number: 0=Homeroom/Study Hall, 1-8=Class periods
     * NULL = General daily attendance (backward compatibility)
     */
    @Column(name = "period_number")
    private Integer periodNumber;

    /**
     * Attendance status: PRESENT, ABSENT, TARDY, EXCUSED
     */
    @Column(name = "status", nullable = false, length = 20)
    private String status;

    /**
     * Time student arrived (for tardiness tracking)
     */
    @Column(name = "arrival_time")
    private LocalTime arrivalTime;

    /**
     * Minutes late (calculated if tardy)
     */
    @Column(name = "minutes_late")
    private Integer minutesLate;

    /**
     * Notes about absence or tardiness
     */
    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    /**
     * Is absence excused (doctor note, etc.)
     */
    @Column(name = "excused")
    @Builder.Default
    private Boolean excused = false;

    /**
     * Sync status: pending, synced, conflict
     */
    @Column(name = "sync_status", length = 20)
    @Builder.Default
    private String syncStatus = "pending";

    /**
     * Record creation timestamp
     */
    @Column(name = "created_at")
    private LocalDate createdAt;

    /**
     * Record update timestamp
     */
    @Column(name = "updated_at")
    private LocalDate updatedAt;

    // === Business Logic Methods ===

    /**
     * Check if student is present
     */
    public boolean isPresent() {
        return "PRESENT".equalsIgnoreCase(status);
    }

    /**
     * Check if student is absent
     */
    public boolean isAbsent() {
        return "ABSENT".equalsIgnoreCase(status);
    }

    /**
     * Check if student is tardy
     */
    public boolean isTardy() {
        return "TARDY".equalsIgnoreCase(status);
    }

    /**
     * Check if needs sync
     */
    public boolean needsSync() {
        return "pending".equalsIgnoreCase(syncStatus);
    }

    /**
     * Get display status with excused indicator
     */
    public String getDisplayStatus() {
        if (excused != null && excused) {
            return status + " (Excused)";
        }
        return status;
    }

    /**
     * Get period display name
     */
    public String getPeriodDisplay() {
        if (periodNumber == null) {
            return "Daily";
        } else if (periodNumber == 0) {
            return "Homeroom";
        } else {
            return "Period " + periodNumber;
        }
    }

    /**
     * Calculate minutes late based on arrival time
     */
    public void calculateMinutesLate(LocalTime schoolStartTime) {
        if (arrivalTime != null && schoolStartTime != null) {
            if (arrivalTime.isAfter(schoolStartTime)) {
                minutesLate = (int) java.time.Duration.between(schoolStartTime, arrivalTime).toMinutes();
                status = "TARDY";
            } else {
                minutesLate = 0;
                if ("TARDY".equalsIgnoreCase(status)) {
                    status = "PRESENT";
                }
            }
        }
    }

    // === JPA Lifecycle Callbacks ===

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDate.now();
        updatedAt = LocalDate.now();

        if (syncStatus == null) {
            syncStatus = "pending";
        }

        if (excused == null) {
            excused = false;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDate.now();

        // Mark for sync if already synced
        if ("synced".equalsIgnoreCase(syncStatus)) {
            syncStatus = "pending";
        }
    }
}
