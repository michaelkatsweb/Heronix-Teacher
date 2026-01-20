package com.heronix.teacher.model.dto.talk;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * WebSocket message wrapper for Heronix-Talk real-time communication
 *
 * Message Types: MESSAGE, CHANNEL, USER, PRESENCE, TYPING, NOTIFICATION, NEWS, ERROR, ACK, SYSTEM
 * Actions: CREATE, UPDATE, DELETE, JOIN, LEAVE, READ, TYPING_START, TYPING_STOP,
 *          ONLINE, OFFLINE, STATUS_CHANGE, REACTION, PIN, UNPIN, HISTORY
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatWsMessage {
    private String type;
    private String action;
    private Object payload;
    private Long userId;
    private Long channelId;
    private String correlationId;
    private boolean success;
    private String errorMessage;
    private LocalDateTime timestamp;

    // Message type constants
    public static final String TYPE_MESSAGE = "MESSAGE";
    public static final String TYPE_CHANNEL = "CHANNEL";
    public static final String TYPE_USER = "USER";
    public static final String TYPE_PRESENCE = "PRESENCE";
    public static final String TYPE_TYPING = "TYPING";
    public static final String TYPE_NOTIFICATION = "NOTIFICATION";
    public static final String TYPE_ERROR = "ERROR";
    public static final String TYPE_ACK = "ACK";
    public static final String TYPE_SYSTEM = "SYSTEM";

    // Action constants
    public static final String ACTION_CREATE = "CREATE";
    public static final String ACTION_UPDATE = "UPDATE";
    public static final String ACTION_DELETE = "DELETE";
    public static final String ACTION_JOIN = "JOIN";
    public static final String ACTION_LEAVE = "LEAVE";
    public static final String ACTION_READ = "READ";
    public static final String ACTION_TYPING_START = "TYPING_START";
    public static final String ACTION_TYPING_STOP = "TYPING_STOP";
    public static final String ACTION_ONLINE = "ONLINE";
    public static final String ACTION_OFFLINE = "OFFLINE";
    public static final String ACTION_STATUS_CHANGE = "STATUS_CHANGE";
    public static final String ACTION_REACTION = "REACTION";
    public static final String ACTION_PIN = "PIN";
    public static final String ACTION_UNPIN = "UNPIN";
    public static final String ACTION_HISTORY = "HISTORY";

    // Factory methods for common messages
    public static ChatWsMessage sendMessage(Long channelId, String content, String clientId) {
        return ChatWsMessage.builder()
                .type(TYPE_MESSAGE)
                .action(ACTION_CREATE)
                .channelId(channelId)
                .payload(new SendMessagePayload(channelId, content, "TEXT", null, clientId))
                .timestamp(LocalDateTime.now())
                .build();
    }

    public static ChatWsMessage typingStart(Long channelId) {
        return ChatWsMessage.builder()
                .type(TYPE_TYPING)
                .action(ACTION_TYPING_START)
                .channelId(channelId)
                .payload(new TypingPayload(channelId, true))
                .timestamp(LocalDateTime.now())
                .build();
    }

    public static ChatWsMessage typingStop(Long channelId) {
        return ChatWsMessage.builder()
                .type(TYPE_TYPING)
                .action(ACTION_TYPING_STOP)
                .channelId(channelId)
                .payload(new TypingPayload(channelId, false))
                .timestamp(LocalDateTime.now())
                .build();
    }

    public static ChatWsMessage joinChannel(Long channelId) {
        return ChatWsMessage.builder()
                .type(TYPE_CHANNEL)
                .action(ACTION_JOIN)
                .channelId(channelId)
                .payload(new ChannelPayload(channelId))
                .timestamp(LocalDateTime.now())
                .build();
    }

    public static ChatWsMessage markRead(Long channelId, Long messageId) {
        return ChatWsMessage.builder()
                .type(TYPE_CHANNEL)
                .action(ACTION_READ)
                .channelId(channelId)
                .payload(new ReadPayload(channelId, messageId))
                .timestamp(LocalDateTime.now())
                .build();
    }

    public static ChatWsMessage updatePresence(String status, String statusMessage) {
        return ChatWsMessage.builder()
                .type(TYPE_PRESENCE)
                .action(ACTION_STATUS_CHANGE)
                .payload(new PresencePayload(status, statusMessage))
                .timestamp(LocalDateTime.now())
                .build();
    }

    // Payload inner classes
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class SendMessagePayload {
        private Long channelId;
        private String content;
        private String messageType;
        private Long replyToId;
        private String clientId;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class TypingPayload {
        private Long channelId;
        private boolean isTyping;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class ChannelPayload {
        private Long channelId;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class ReadPayload {
        private Long channelId;
        private Long messageId;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class PresencePayload {
        private String status;
        private String statusMessage;
    }
}
