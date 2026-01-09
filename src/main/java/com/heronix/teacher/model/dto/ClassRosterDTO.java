package com.heronix.teacher.model.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalTime;
import java.util.List;

/**
 * Class Roster DTO
 * Contains student roster for a specific class period
 * Used for Heronix-Teacher sync
 *
 * @author EduScheduler Pro Team
 * @since December 8, 2025
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClassRosterDTO {

    // Period information
    private Integer period;
    private String periodDisplay; // "Homeroom" or "Period 1", etc.

    // Course information
    private Long courseId;
    private String courseName;
    private String courseCode;
    private String subject;

    // Room information
    private Long roomId;
    private String roomNumber;
    private String roomType;

    // Time information
    @JsonFormat(pattern = "HH:mm")
    private LocalTime startTime;

    @JsonFormat(pattern = "HH:mm")
    private LocalTime endTime;

    private Integer durationMinutes;

    // Enrollment information
    private Integer enrolledCount;
    private Integer maxCapacity;

    // Student roster
    private List<RosterStudentDTO> students;

    /**
     * Student in roster with essential information
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RosterStudentDTO {

        private Long studentId;
        private String studentNumber;
        private String firstName;
        private String lastName;
        private String fullName;
        private Integer gradeLevel;
        private String email;

        // Special needs indicators
        private Boolean hasIep;
        private Boolean has504;

        // Optional photo URL
        private String photoUrl;

        // Current GPA (for teacher reference)
        private Double currentGpa;

        // Notes specific to this class
        private String notes;
    }
}
