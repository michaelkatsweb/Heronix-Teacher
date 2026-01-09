package com.heronix.teacher.model.enums;

/**
 * Grading style enumeration
 *
 * Defines different grading systems teachers can use:
 * - LETTER: Letter grades (A, B, C, D, F)
 * - POINTS: Points-based grading (e.g., 85/100)
 * - PERCENTAGE: Percentage-based grading (e.g., 85%)
 *
 * @author EduScheduler Team
 * @version 1.0.0
 */
public enum GradingStyle {

    /**
     * Letter grades (A, B, C, D, F)
     * Standard: A = 90-100, B = 80-89, C = 70-79, D = 60-69, F = 0-59
     */
    LETTER("Letter Grade (A-F)"),

    /**
     * Points-based grading
     * Example: 85/100 points, 47/50 points
     */
    POINTS("Points"),

    /**
     * Percentage-based grading
     * Example: 85%, 94%
     */
    PERCENTAGE("Percentage (%)");

    private final String displayName;

    GradingStyle(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
