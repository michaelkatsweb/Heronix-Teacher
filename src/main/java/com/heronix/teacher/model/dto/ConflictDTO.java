package com.heronix.teacher.model.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Data Transfer Object for Sync Conflicts
 * Represents a conflict between local and server data
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConflictDTO {

    private Long id;
    private String entityType; // "grade", "student", "assignment"
    private Long entityId;
    private String fieldName;

    private String localValue;
    private String serverValue;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime localTimestamp;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime serverTimestamp;

    private String description;
    private String resolution; // "keep_local", "keep_server", "merge", "pending"

    // Helper methods
    public boolean isResolved() {
        return resolution != null && !"pending".equalsIgnoreCase(resolution);
    }

    public boolean serverIsNewer() {
        if (localTimestamp == null || serverTimestamp == null) {
            return false;
        }
        return serverTimestamp.isAfter(localTimestamp);
    }

    public String getConflictSummary() {
        return String.format("%s conflict in %s (ID: %d)",
            entityType, fieldName, entityId);
    }
}
