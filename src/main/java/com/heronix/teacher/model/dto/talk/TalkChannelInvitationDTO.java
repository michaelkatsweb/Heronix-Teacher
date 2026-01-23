package com.heronix.teacher.model.dto.talk;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Channel invitation DTO from Heronix-Talk server
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class TalkChannelInvitationDTO {
    private Long id;

    // Channel info
    private Long channelId;
    private String channelName;
    private String channelDescription;
    private String channelType;
    private String channelIcon;
    private int channelMemberCount;

    // Inviter info
    private Long inviterId;
    private String inviterName;
    private String inviterRole;

    // Invitee info
    private Long inviteeId;
    private String inviteeName;

    // Invitation details
    private String status;      // PENDING, ACCEPTED, DECLINED, EXPIRED, CANCELLED
    private String message;
    private LocalDateTime createdAt;
    private LocalDateTime respondedAt;
    private LocalDateTime expiresAt;
    private boolean expired;

    /**
     * Check if the invitation is still pending and can be responded to
     */
    public boolean canRespond() {
        return "PENDING".equals(status) && !expired;
    }

    /**
     * Get display icon for the channel type
     */
    public String getDisplayIcon() {
        if (channelIcon != null && !channelIcon.isEmpty()) {
            return channelIcon;
        }
        if (channelType == null) return "#";
        return switch (channelType) {
            case "PRIVATE" -> "\uD83D\uDD12";      // Lock
            case "GROUP", "GROUP_MESSAGE" -> "\uD83D\uDC65";  // Group
            case "DEPARTMENT" -> "\uD83C\uDFE2";   // Office
            case "ANNOUNCEMENT" -> "\uD83D\uDCE2"; // Megaphone
            case "DIRECT_MESSAGE" -> "\uD83D\uDCAC"; // Speech bubble
            default -> "#";
        };
    }
}
