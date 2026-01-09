package com.heronix.teacher.model.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Assignment Category Entity
 *
 * Groups assignments together for weighted grading calculations
 * Examples: Homework, Quizzes, Tests, Projects, Participation
 *
 * Features:
 * - Weighted grading by category
 * - Drop lowest N scores
 * - Extra credit support
 * - Auto-sync to main server
 *
 * Based on research from Canvas, PowerSchool, Schoology best practices
 *
 * @author EduScheduler Team
 * @version 1.0.0
 */
@Entity
@Table(name = "assignment_categories",
       uniqueConstraints = @UniqueConstraint(columnNames = {"course_id", "name"}))
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AssignmentCategory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Category name (e.g., "Homework", "Tests", "Quizzes", "Projects")
     */
    @Column(name = "name", nullable = false)
    private String name;

    /**
     * Category description
     */
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    /**
     * Course ID (optional - null means applies to all courses)
     */
    @Column(name = "course_id")
    private Long courseId;

    /**
     * Course name for display
     */
    @Column(name = "course_name")
    private String courseName;

    /**
     * Weight percentage (0.0 to 1.0)
     * Example: 0.40 = 40% of final grade
     * If null, categories are equally weighted
     */
    @Column(name = "weight")
    private Double weight;

    /**
     * Drop lowest N scores
     * Example: dropLowest = 2 drops the 2 lowest scores from grade calculation
     * Default: 0 (don't drop any)
     */
    @Column(name = "drop_lowest")
    @Builder.Default
    private Integer dropLowest = 0;

    /**
     * Drop highest N scores (rare, but some teachers use this)
     * Default: 0 (don't drop any)
     */
    @Column(name = "drop_highest")
    @Builder.Default
    private Integer dropHighest = 0;

    /**
     * Is this an extra credit category?
     * Extra credit categories add bonus points but don't affect points possible
     */
    @Column(name = "is_extra_credit")
    @Builder.Default
    private Boolean isExtraCredit = false;

    /**
     * Display order for sorting categories
     */
    @Column(name = "display_order")
    @Builder.Default
    private Integer displayOrder = 0;

    /**
     * Active status
     */
    @Column(name = "active")
    @Builder.Default
    private Boolean active = true;

    /**
     * Color code for UI display (hex color)
     */
    @Column(name = "color_code")
    private String colorCode;

    /**
     * Sync status: synced, pending, conflict
     */
    @Column(name = "sync_status")
    @Builder.Default
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
     * Assignments in this category
     */
    @OneToMany(mappedBy = "category", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private List<Assignment> assignments = new ArrayList<>();

    // === Business Logic Methods ===

    /**
     * Check if needs sync
     */
    public boolean needsSync() {
        return "pending".equalsIgnoreCase(syncStatus);
    }

    /**
     * Get display name with weight
     */
    public String getDisplayNameWithWeight() {
        if (weight != null) {
            return name + " (" + String.format("%.0f%%", weight * 100) + ")";
        }
        return name;
    }

    /**
     * Check if drop lowest is enabled
     */
    public boolean hasDropLowest() {
        return dropLowest != null && dropLowest > 0;
    }

    /**
     * Check if drop highest is enabled
     */
    public boolean hasDropHighest() {
        return dropHighest != null && dropHighest > 0;
    }

    /**
     * Get number of scores to drop
     */
    public int getTotalScoresToDrop() {
        int total = 0;
        if (dropLowest != null) total += dropLowest;
        if (dropHighest != null) total += dropHighest;
        return total;
    }

    /**
     * Validate drop settings
     * Can't drop more scores than exist in category
     */
    public boolean validateDropSettings(int totalAssignments) {
        int scoresToDrop = getTotalScoresToDrop();
        return scoresToDrop < totalAssignments;
    }

    /**
     * Get weight as percentage for display
     */
    public String getWeightPercentageDisplay() {
        if (weight == null) {
            return "Auto";
        }
        return String.format("%.1f%%", weight * 100);
    }

    // === JPA Lifecycle Callbacks ===

    @PrePersist
    protected void onCreate() {
        createdDate = LocalDateTime.now();
        modifiedDate = LocalDateTime.now();

        if (syncStatus == null) {
            syncStatus = "pending";
        }

        if (active == null) {
            active = true;
        }

        if (dropLowest == null) {
            dropLowest = 0;
        }

        if (dropHighest == null) {
            dropHighest = 0;
        }

        if (isExtraCredit == null) {
            isExtraCredit = false;
        }

        if (displayOrder == null) {
            displayOrder = 0;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        modifiedDate = LocalDateTime.now();

        // Mark as pending sync when updated
        if ("synced".equalsIgnoreCase(syncStatus)) {
            syncStatus = "pending";
        }
    }

    @Override
    public String toString() {
        return name + (weight != null ? " (" + String.format("%.0f%%", weight * 100) + ")" : "");
    }
}
