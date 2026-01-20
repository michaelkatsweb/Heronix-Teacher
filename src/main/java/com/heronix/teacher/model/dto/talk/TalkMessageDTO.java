package com.heronix.teacher.model.dto.talk;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Message DTO from Heronix-Talk server
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TalkMessageDTO {
    private Long id;
    private String messageUuid;
    private Long channelId;
    private String channelName;
    private Long senderId;
    private String senderName;
    private String senderAvatar;
    private String content;
    private String messageType;    // TEXT, FILE, IMAGE, SYSTEM, ANNOUNCEMENT, REPLY, REACTION, EDITED, DELETED
    private String status;         // SENT, DELIVERED, READ
    private boolean edited;
    private boolean deleted;
    private boolean pinned;
    private boolean important;
    private LocalDateTime timestamp;
    private LocalDateTime editedAt;

    // Reply support
    private Long replyToId;
    private String replyToPreview;
    private String replyToSenderName;
    private int replyCount;

    // Attachment support
    private String attachmentPath;
    private String attachmentName;
    private String attachmentType;
    private Long attachmentSize;

    // Reactions and mentions (JSON strings from server)
    private String reactions;
    private String mentions;

    // Client-side ID for deduplication
    private String clientId;
}
