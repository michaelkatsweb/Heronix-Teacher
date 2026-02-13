package com.heronix.teacher.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for creating / displaying behavior incidents via the SIS REST API.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BehaviorIncidentDTO {

    private Long studentId;
    private Long courseId;
    private Long reportingTeacherId;
    private String incidentDate;
    private String incidentTime;
    private String behaviorType;       // POSITIVE, NEGATIVE
    private String behaviorCategory;   // DISRUPTION, TARDINESS, etc.
    private String severityLevel;      // MINOR, MODERATE, MAJOR, SEVERE
    private String incidentLocation;   // CLASSROOM, HALLWAY, CAFETERIA, etc.
    private String incidentDescription;
    private String interventionApplied;
    private Boolean parentContacted;
    private Boolean adminReferralRequired;
}
