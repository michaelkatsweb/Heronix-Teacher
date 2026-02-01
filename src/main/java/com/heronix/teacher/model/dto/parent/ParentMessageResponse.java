package com.heronix.teacher.model.dto.parent;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Response DTOs for Parent Portal messaging
 *
 * Represents responses from the Heronix-Talk Parent Portal
 * messaging service.
 *
 * @author Heronix Teacher Team
 * @version 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ParentMessageResponse {

    private boolean success;
    private String message;
    private String messageRef;
    private LocalDateTime timestamp;
    private DeliveryStatus deliveryStatus;
    private Map<String, Object> metadata;

    /**
     * Delivery status for each channel
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DeliveryStatus {
        private ChannelStatus inApp;
        private ChannelStatus pushNotification;
        private ChannelStatus email;
        private ChannelStatus sms;

        @Data
        @Builder
        @NoArgsConstructor
        @AllArgsConstructor
        public static class ChannelStatus {
            private boolean attempted;
            private boolean delivered;
            private String status;
            private LocalDateTime deliveredAt;
            private String errorMessage;
        }
    }

    /**
     * Service status response
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ServiceStatus {
        private boolean available;
        private boolean parentPortalEnabled;
        private String serviceVersion;
        private String message;
        private List<String> supportedChannels;
        private List<String> supportedCategories;
    }

    /**
     * Bulk send response
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BulkSendResponse {
        private boolean success;
        private String message;
        private int totalRecipients;
        private int successCount;
        private int failureCount;
        private List<String> messageRefs;
        private List<FailedDelivery> failures;

        @Data
        @Builder
        @NoArgsConstructor
        @AllArgsConstructor
        public static class FailedDelivery {
            private String parentToken;
            private String reason;
        }
    }

    /**
     * Message acknowledgment response
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AcknowledgmentResponse {
        private String messageRef;
        private boolean acknowledged;
        private LocalDateTime acknowledgedAt;
        private String acknowledgedBy;
    }
}
