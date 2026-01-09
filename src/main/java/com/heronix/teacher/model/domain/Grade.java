package com.heronix.teacher.model.domain;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/**
 * Grade entity for student assignments
 *
 * Links students to assignments with scores
 * Synced with admin server when network available
 *
 * @author EduScheduler Team
 * @version 1.0.0
 */
@Entity
@Table(name = "grades",
    uniqueConstraints = @UniqueConstraint(columnNames = {"student_id", "assignment_id"})
)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Grade {

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
     * Assignment reference
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assignment_id", nullable = false)
    private Assignment assignment;

    /**
     * Numerical score (out of assignment max_points)
     */
    @Column(name = "score")
    private Double score;

    /**
     * Letter grade (A, B, C, D, F, etc.)
     */
    @Column(name = "letter_grade")
    private String letterGrade;

    /**
     * GPA points (4.0 scale)
     */
    @Column(name = "gpa_points")
    private Double gpaPoints;

    /**
     * Teacher notes for this grade
     */
    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    /**
     * Excused flag (for absences, etc.)
     */
    @Column(name = "excused")
    private Boolean excused = false;

    /**
     * Late submission flag
     */
    @Column(name = "late")
    private Boolean late = false;

    /**
     * Missing/not submitted flag
     */
    @Column(name = "missing")
    private Boolean missing = false;

    /**
     * Sync status: synced, pending, conflict
     */
    @Column(name = "sync_status")
    private String syncStatus = "pending";

    /**
     * Date entered
     */
    @Column(name = "date_entered")
    private LocalDateTime dateEntered;

    /**
     * Last modified
     */
    @Column(name = "modified_date")
    private LocalDateTime modifiedDate;

    /**
     * Calculate percentage
     */
    public Double getPercentage() {
        if (score == null || assignment == null || assignment.getMaxPoints() == null) {
            return null;
        }
        if (assignment.getMaxPoints() == 0) {
            return 0.0;
        }
        return (score / assignment.getMaxPoints()) * 100.0;
    }

    /**
     * Check if passing (>= 60%)
     */
    public boolean isPassing() {
        Double percentage = getPercentage();
        return percentage != null && percentage >= 60.0;
    }

    /**
     * Check if needs sync
     */
    public boolean needsSync() {
        return "pending".equalsIgnoreCase(syncStatus);
    }

    /**
     * Get display grade based on assignment's grading style
     */
    public String getDisplayGrade() {
        if (assignment == null) {
            return getFormattedScore();
        }

        return switch (assignment.getGradingStyle()) {
            case LETTER -> letterGrade != null ? letterGrade : "N/A";
            case POINTS -> score != null ?
                String.format("%.1f / %.0f", score, assignment.getMaxPoints()) : "N/A";
            case PERCENTAGE -> {
                Double pct = getPercentage();
                yield pct != null ? String.format("%.1f%%", pct) : "N/A";
            }
        };
    }

    /**
     * Get formatted score (backward compatibility)
     */
    public String getFormattedScore() {
        if (score == null) {
            return "N/A";
        }
        if (assignment != null && assignment.getMaxPoints() != null) {
            return String.format("%.1f / %.0f", score, assignment.getMaxPoints());
        }
        return String.format("%.1f", score);
    }

    /**
     * Auto-calculate letter grade from score
     */
    public void calculateLetterGrade() {
        Double percentage = getPercentage();
        if (percentage == null) {
            letterGrade = null;
            return;
        }

        if (percentage >= 90) {
            letterGrade = "A";
            gpaPoints = 4.0;
        } else if (percentage >= 80) {
            letterGrade = "B";
            gpaPoints = 3.0;
        } else if (percentage >= 70) {
            letterGrade = "C";
            gpaPoints = 2.0;
        } else if (percentage >= 60) {
            letterGrade = "D";
            gpaPoints = 1.0;
        } else {
            letterGrade = "F";
            gpaPoints = 0.0;
        }
    }

    /**
     * Set letter grade directly (for LETTER grading style)
     */
    public void setLetterGradeDirectly(String letter) {
        this.letterGrade = letter.toUpperCase();

        // Calculate numeric score and GPA from letter
        switch (this.letterGrade) {
            case "A" -> {
                this.gpaPoints = 4.0;
                if (assignment != null && assignment.getMaxPoints() != null) {
                    this.score = assignment.getMaxPoints() * 0.95; // 95%
                }
            }
            case "B" -> {
                this.gpaPoints = 3.0;
                if (assignment != null && assignment.getMaxPoints() != null) {
                    this.score = assignment.getMaxPoints() * 0.85; // 85%
                }
            }
            case "C" -> {
                this.gpaPoints = 2.0;
                if (assignment != null && assignment.getMaxPoints() != null) {
                    this.score = assignment.getMaxPoints() * 0.75; // 75%
                }
            }
            case "D" -> {
                this.gpaPoints = 1.0;
                if (assignment != null && assignment.getMaxPoints() != null) {
                    this.score = assignment.getMaxPoints() * 0.65; // 65%
                }
            }
            case "F" -> {
                this.gpaPoints = 0.0;
                if (assignment != null && assignment.getMaxPoints() != null) {
                    this.score = assignment.getMaxPoints() * 0.50; // 50%
                }
            }
        }
    }

    /**
     * Pre-persist callback
     */
    @PrePersist
    protected void onCreate() {
        dateEntered = LocalDateTime.now();
        modifiedDate = LocalDateTime.now();
        calculateLetterGrade();
    }

    /**
     * Pre-update callback
     */
    @PreUpdate
    protected void onUpdate() {
        modifiedDate = LocalDateTime.now();
        calculateLetterGrade();
        // Mark as pending sync when updated
        if ("synced".equalsIgnoreCase(syncStatus)) {
            syncStatus = "pending";
        }
    }
}
