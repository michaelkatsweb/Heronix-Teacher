package com.heronix.teacher.model.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Data Transfer Object for Grade data
 * Used for submitting grades from Heronix-Teacher to EduScheduler-Pro
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GradeDTO {

    private Long id;
    private Long studentId;
    private Long assignmentId;
    private Double score;
    private String letterGrade;
    private Double gpaPoints;
    private String notes;
    private Boolean excused;
    private Boolean late;
    private Boolean missing;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime dateEntered;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime modifiedDate;

    // Helper methods
    public boolean isPassing() {
        if (score == null || excused || missing) {
            return false;
        }
        return score >= 60.0;
    }

    public Double getPercentage() {
        // Calculated on server side based on assignment max points
        return score;
    }
}
