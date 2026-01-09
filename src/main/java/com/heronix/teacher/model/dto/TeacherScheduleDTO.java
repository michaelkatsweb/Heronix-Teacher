package com.heronix.teacher.model.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalTime;
import java.util.List;
import java.util.Map;

/**
 * Teacher Schedule DTO
 * Contains teacher's schedule with period assignments
 * Used for Heronix-Teacher sync
 *
 * @author EduScheduler Pro Team
 * @since December 8, 2025
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TeacherScheduleDTO {

    private Long teacherId;
    private String employeeId;
    private String firstName;
    private String lastName;
    private String fullName;
    private String department;
    private String email;

    /**
     * Map of period number to period assignment
     * Period 0 = Homeroom, 1-7 = Class periods
     */
    private Map<Integer, PeriodAssignmentDTO> periods;

    /**
     * Period Assignment - Details for one class period
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PeriodAssignmentDTO {

        private Integer period;
        private Long courseId;
        private String courseName;
        private String courseCode;
        private String subject;

        private Long roomId;
        private String roomNumber;
        private String roomType;

        @JsonFormat(pattern = "HH:mm")
        private LocalTime startTime;

        @JsonFormat(pattern = "HH:mm")
        private LocalTime endTime;

        private Integer enrolledCount;
        private Integer maxCapacity;

        /**
         * List of student IDs enrolled in this period
         * Useful for roster preview
         */
        private List<Long> studentIds;
    }
}
