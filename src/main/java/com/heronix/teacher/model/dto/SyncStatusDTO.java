package com.heronix.teacher.model.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Data Transfer Object for Sync Status
 * Shows what needs to be synced and any conflicts
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SyncStatusDTO {

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime lastSuccessfulSync;

    private Integer pendingGrades;
    private Integer pendingAttendance;
    private Integer conflictingGrades;
    private Integer conflictingStudents;

    private List<ConflictDTO> conflicts;

    private Boolean syncRequired;
    private String message;

    // Helper methods
    public boolean hasPendingItems() {
        return (pendingGrades != null && pendingGrades > 0) ||
               (pendingAttendance != null && pendingAttendance > 0);
    }

    public boolean hasConflicts() {
        return (conflictingGrades != null && conflictingGrades > 0) ||
               (conflictingStudents != null && conflictingStudents > 0);
    }

    public int getTotalPending() {
        int total = 0;
        if (pendingGrades != null) total += pendingGrades;
        if (pendingAttendance != null) total += pendingAttendance;
        return total;
    }
}
