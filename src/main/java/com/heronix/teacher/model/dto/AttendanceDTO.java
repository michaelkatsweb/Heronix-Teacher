package com.heronix.teacher.model.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * Data Transfer Object for Attendance data
 * Used for submitting attendance from Heronix-Teacher to EduScheduler-Pro
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AttendanceDTO {

    private Long id;
    private Long studentId;
    private Long courseId;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate date;

    private String status; // present, absent, tardy, excused
    private String notes;

    @JsonFormat(pattern = "HH:mm:ss")
    private LocalTime timeIn;

    @JsonFormat(pattern = "HH:mm:ss")
    private LocalTime timeOut;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime createdDate;

    // Helper methods
    public boolean isPresent() {
        return "present".equalsIgnoreCase(status);
    }

    public boolean isAbsent() {
        return "absent".equalsIgnoreCase(status);
    }

    public boolean isTardy() {
        return "tardy".equalsIgnoreCase(status);
    }
}
