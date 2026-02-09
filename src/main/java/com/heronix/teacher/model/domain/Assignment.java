package com.heronix.teacher.model.domain;

import com.heronix.teacher.model.enums.AssignmentType;
import com.heronix.teacher.model.enums.GradingStyle;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Assignment entity for gradebook
 *
 * Created by teachers for their courses
 * Includes weighting, categorization, and flexible grading styles
 *
 * Features:
 * - Multiple grading styles (Letter, Points, Percentage)
 * - Canvas-like assignment types (Homework, Quiz, Test, Exam, etc.)
 * - Automatic weighting based on type
 * - Auto-sync to main server
 *
 * @author EduScheduler Team
 * @version 1.0.0
 */
@Entity
@Table(name = "assignments")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Assignment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Course ID (from admin system)
     */
    @Column(name = "course_id")
    private Long courseId;

    /**
     * Course name for display
     */
    @Column(name = "course_name")
    private String courseName;

    /**
     * Period number this assignment belongs to (from enrollment schedule)
     */
    @Column(name = "period_number")
    private Integer periodNumber;

    /**
     * Assignment name
     */
    @Column(name = "name", nullable = false)
    private String name;

    /**
     * Assignment description
     */
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    /**
     * Assignment type (HOMEWORK, QUIZ, TEST, EXAM, etc.)
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "assignment_type", nullable = false)
    @Builder.Default
    private AssignmentType assignmentType = AssignmentType.HOMEWORK;

    /**
     * Assignment Category (for weighted grading)
     */
    @com.fasterxml.jackson.annotation.JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private AssignmentCategory category;

    /**
     * Legacy category string field for backward compatibility
     * Values: homework, quiz, test, project, participation, other
     */
    @Column(name = "category_name")
    private String categoryName;

    /**
     * Is this an extra credit assignment?
     * Extra credit assignments add bonus points but don't affect points possible
     */
    @Column(name = "is_extra_credit")
    @Builder.Default
    private Boolean isExtraCredit = false;

    /**
     * Grading style for this assignment
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "grading_style", nullable = false)
    @Builder.Default
    private GradingStyle gradingStyle = GradingStyle.POINTS;

    /**
     * Maximum points possible
     */
    @Column(name = "max_points", nullable = false)
    private Double maxPoints;

    /**
     * Weight percentage (0.0 to 1.0)
     * Example: 0.30 = 30% of final grade
     * Auto-calculated from assignment type if not set
     */
    @Column(name = "weight")
    private Double weight;

    /**
     * Due date
     */
    @Column(name = "due_date")
    private LocalDate dueDate;

    /**
     * Active status
     */
    @Column(name = "active")
    private Boolean active = true;

    /**
     * Sync status: synced, pending, conflict
     */
    @Column(name = "sync_status")
    private String syncStatus = "pending";

    /**
     * Created timestamp
     */
    @Column(name = "created_date")
    private LocalDateTime createdDate;

    /**
     * Modified timestamp
     */
    @Column(name = "modified_date")
    private LocalDateTime modifiedDate;

    /**
     * Check if needs sync
     */
    public boolean needsSync() {
        return "pending".equalsIgnoreCase(syncStatus);
    }

    /**
     * Check if overdue
     */
    public boolean isOverdue() {
        if (dueDate == null) {
            return false;
        }
        return LocalDate.now().isAfter(dueDate);
    }

    /**
     * Get display name with assignment type
     */
    public String getDisplayName() {
        return name + " (" + assignmentType.getDisplayName() + ")";
    }

    /**
     * Get grading display (e.g., "Points: 100", "Letter Grade", "Percentage")
     */
    public String getGradingDisplay() {
        return switch (gradingStyle) {
            case LETTER -> "Letter Grade (A-F)";
            case POINTS -> "Points: " + maxPoints.intValue();
            case PERCENTAGE -> "Percentage (%)";
        };
    }

    /**
     * Auto-calculate weight from assignment type if not manually set
     */
    public void autoCalculateWeight() {
        if (weight == null || weight == 0.0) {
            weight = assignmentType.getDefaultWeight();
        }
    }

    /**
     * Sync legacy category field with new category entity
     */
    private void syncCategoryField() {
        if (category != null) {
            categoryName = category.getName();
        } else if (assignmentType != null) {
            categoryName = assignmentType.name().toLowerCase();
        }
    }

    /**
     * Check if this is extra credit
     */
    public boolean isExtraCredit() {
        if (isExtraCredit != null && isExtraCredit) {
            return true;
        }
        if (category != null && category.getIsExtraCredit() != null) {
            return category.getIsExtraCredit();
        }
        return false;
    }

    /**
     * Pre-persist callback
     */
    @PrePersist
    protected void onCreate() {
        createdDate = LocalDateTime.now();
        modifiedDate = LocalDateTime.now();
        autoCalculateWeight();
        syncCategoryField();
    }

    /**
     * Pre-update callback
     */
    @PreUpdate
    protected void onUpdate() {
        modifiedDate = LocalDateTime.now();
        syncCategoryField();
        // Mark as pending sync when updated
        if ("synced".equalsIgnoreCase(syncStatus)) {
            syncStatus = "pending";
        }
    }
}

