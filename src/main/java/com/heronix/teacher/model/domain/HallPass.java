package com.heronix.teacher.model.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Hall Pass entity - Electronic hall pass tracking
 *
 * Features:
 * - Student hall pass issuance
 * - Destination tracking
 * - Time out/in tracking
 * - Duration calculation
 * - Pass status management
 * - Auto-sync to main server
 *
 * @author EduScheduler Team
 * @version 1.0.0
 */
@Entity
@Table(name = "hall_pass")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HallPass {

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
     * Date of hall pass
     */
    @Column(name = "pass_date", nullable = false)
    private LocalDate passDate;

    /**
     * Time student left classroom
     */
    @Column(name = "time_out", nullable = false)
    private LocalTime timeOut;

    /**
     * Time student returned to classroom
     */
    @Column(name = "time_in")
    private LocalTime timeIn;

    /**
     * Destination: RESTROOM, NURSE, OFFICE, COUNSELOR, WATER_FOUNTAIN, LOCKER, OTHER
     */
    @Column(name = "destination", nullable = false, length = 30)
    private String destination;

    /**
     * Additional notes or reason
     */
    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    /**
     * Pass status: ACTIVE, RETURNED, OVERDUE
     */
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private String status = "ACTIVE";

    /**
     * Duration in minutes (calculated when returned)
     */
    @Column(name = "duration_minutes")
    private Integer durationMinutes;

    /**
     * Is this pass flagged for review (excessive time, etc.)
     */
    @Column(name = "flagged")
    @Builder.Default
    private Boolean flagged = false;

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
     * Check if pass is active (student hasn't returned)
     */
    public boolean isActive() {
        return "ACTIVE".equalsIgnoreCase(status);
    }

    /**
     * Check if pass is returned
     */
    public boolean isReturned() {
        return "RETURNED".equalsIgnoreCase(status);
    }

    /**
     * Check if pass is overdue
     */
    public boolean isOverdue() {
        return "OVERDUE".equalsIgnoreCase(status);
    }

    /**
     * Check if needs sync
     */
    public boolean needsSync() {
        return "pending".equalsIgnoreCase(syncStatus);
    }

    /**
     * Mark student as returned and calculate duration
     */
    public void markReturned(LocalTime returnTime) {
        this.timeIn = returnTime;
        this.status = "RETURNED";

        if (timeOut != null && timeIn != null) {
            this.durationMinutes = (int) java.time.Duration.between(timeOut, timeIn).toMinutes();

            // Flag if duration is excessive (more than 15 minutes)
            if (durationMinutes > 15) {
                this.flagged = true;
            }
        }
    }

    /**
     * Get duration display string
     */
    public String getDurationDisplay() {
        if (durationMinutes == null) {
            return "N/A";
        }

        if (durationMinutes < 60) {
            return durationMinutes + " min";
        } else {
            int hours = durationMinutes / 60;
            int mins = durationMinutes % 60;
            return hours + "h " + mins + "m";
        }
    }

    /**
     * Get display status with flagged indicator
     */
    public String getDisplayStatus() {
        String statusText = status;
        if (flagged != null && flagged) {
            statusText += " ⚠";
        }
        return statusText;
    }

    /**
     * Calculate current duration for active passes
     */
    public int getCurrentDuration() {
        if (timeIn != null) {
            return durationMinutes != null ? durationMinutes : 0;
        }

        if (timeOut != null) {
            LocalTime now = LocalTime.now();
            return (int) java.time.Duration.between(timeOut, now).toMinutes();
        }

        return 0;
    }

    /**
     * Check if current duration exceeds threshold
     */
    public boolean exceedsTimeLimit(int thresholdMinutes) {
        return getCurrentDuration() > thresholdMinutes;
    }

    // === JPA Lifecycle Callbacks ===

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDate.now();
        updatedAt = LocalDate.now();

        if (status == null) {
            status = "ACTIVE";
        }

        if (syncStatus == null) {
            syncStatus = "pending";
        }

        if (flagged == null) {
            flagged = false;
        }

        if (passDate == null) {
            passDate = LocalDate.now();
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
