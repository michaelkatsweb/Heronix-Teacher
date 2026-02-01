package com.heronix.teacher.model.dto.parent;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Request DTOs for Parent Portal messaging
 *
 * Used by teachers to send messages to parents through
 * the Heronix-Talk Parent Portal messaging service.
 *
 * Note: All data is tokenized before being sent to the
 * external Parent Portal application.
 *
 * @author Heronix Teacher Team
 * @version 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ParentMessageRequest {

    private Long studentId;
    private String category;
    private String priority;
    private String subject;
    private String content;
    private String parentToken;
    private boolean requiresAcknowledgment;
    private DeliveryOptions deliveryOptions;
    private Map<String, Object> metadata;

    /**
     * Delivery channel options
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DeliveryOptions {
        @Builder.Default
        private boolean inApp = true;
        private boolean pushNotification;
        private boolean email;
        private boolean sms;
    }

    /**
     * Simple notification request
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class NotificationRequest {
        private Long studentId;
        private String category;
        private String title;
        private String message;
        private String parentToken;
    }

    /**
     * Teacher message to parent
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TeacherMessageRequest {
        private Long studentId;
        private String subject;
        private String message;
        private String parentToken;
        private boolean requestMeeting;
        private String preferredContactMethod;
    }

    /**
     * Urgent alert request
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AlertRequest {
        private Long studentId;
        private String alertType;
        private String title;
        private String message;
        private String parentToken;
        private boolean requiresImmediateAction;
        private String actionUrl;
    }

    /**
     * Hall pass notification request
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class HallPassNotificationRequest {
        private Long studentId;
        private String passType;
        private String destination;
        private LocalDateTime departureTime;
        private LocalDateTime returnTime;
        private Integer durationMinutes;
        private String parentToken;
    }

    /**
     * Bulk announcement request
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AnnouncementRequest {
        private String subject;
        private String content;
        private String category;
        private List<String> parentTokens;
        private LocalDateTime effectiveDate;
        private LocalDateTime expirationDate;
    }
}
