package com.heronix.teacher.model.dto.talk;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Presence/Status DTO for Heronix-Talk server
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TalkPresenceDTO {
    private Long userId;
    private String userName;
    private String status;          // ONLINE, AWAY, BUSY, IN_CLASS, IN_MEETING, OFFLINE
    private String statusMessage;
    private LocalDateTime timestamp;
    private LocalDateTime lastSeen;

    // For status update requests
    public static TalkPresenceDTO forStatusUpdate(String status, String statusMessage) {
        return TalkPresenceDTO.builder()
                .status(status)
                .statusMessage(statusMessage)
                .build();
    }
}
