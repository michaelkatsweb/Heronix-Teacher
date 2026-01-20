package com.heronix.teacher.model.dto.talk;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Channel DTO from Heronix-Talk server
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TalkChannelDTO {
    private Long id;
    private String name;
    private String description;
    private String channelType;   // PUBLIC, PRIVATE, DEPARTMENT, DIRECT_MESSAGE, GROUP_MESSAGE, ANNOUNCEMENT
    private String icon;
    private Long creatorId;
    private String creatorName;
    private int memberCount;
    private int messageCount;
    private boolean active;
    private boolean archived;
    private boolean pinned;
    private String directMessageKey;
    private LocalDateTime lastMessageTime;
    private LocalDateTime createdDate;

    // User-specific fields
    private boolean muted;
    private boolean favorite;
    private int unreadCount;
    private Long lastReadMessageId;

    public String getDisplayIcon() {
        if (icon != null && !icon.isEmpty()) {
            return icon;
        }
        // Default icons based on channel type
        return switch (channelType) {
            case "PUBLIC" -> "\uD83D\uDCE2";           // Megaphone
            case "PRIVATE" -> "\uD83D\uDD12";          // Lock
            case "DEPARTMENT" -> "\uD83C\uDFE2";       // Office building
            case "DIRECT_MESSAGE" -> "\uD83D\uDCAC";   // Speech bubble
            case "GROUP_MESSAGE" -> "\uD83D\uDC65";    // Bust silhouette
            case "ANNOUNCEMENT" -> "\uD83D\uDCE3";     // Cheering megaphone
            default -> "\uD83D\uDCAC";                 // Default speech bubble
        };
    }
}
