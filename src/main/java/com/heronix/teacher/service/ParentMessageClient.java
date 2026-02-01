package com.heronix.teacher.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.heronix.teacher.model.dto.parent.ParentMessageRequest;
import com.heronix.teacher.model.dto.parent.ParentMessageResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * REST API Client for Parent Portal Messaging
 *
 * Communicates with Heronix-Talk's Parent Portal messaging service
 * to send tokenized messages to parents.
 *
 * Features:
 * - Send direct messages to parents
 * - Send notifications and alerts
 * - Hall pass notifications
 * - School announcements
 * - Async message delivery
 * - Fallback URL support
 *
 * Note: All student data is tokenized by Heronix-Talk before
 * being sent to the external Parent Portal application.
 *
 * @author Heronix Teacher Team
 * @version 1.0.0
 */
@Slf4j
@Service
public class ParentMessageClient {

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String baseUrl;
    private final String[] fallbackUrls;
    private String activeBaseUrl;
    private String sessionToken;

    private static final String PARENT_PORTAL_API_PATH = "/api/parent-portal/messages";

    public ParentMessageClient(
            ObjectMapper objectMapper,
            @Value("${talk.server.url:http://localhost:9680}") String baseUrl,
            @Value("${talk.server.fallback-urls:}") String fallbackUrlsStr) {
        this.objectMapper = objectMapper;
        this.baseUrl = baseUrl;
        this.activeBaseUrl = baseUrl;
        this.fallbackUrls = fallbackUrlsStr.isEmpty() ? new String[0] : fallbackUrlsStr.split(",");
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    /**
     * Set session token for authenticated requests
     */
    public void setSessionToken(String sessionToken) {
        this.sessionToken = sessionToken;
    }

    // ========================================================================
    // MESSAGE SENDING
    // ========================================================================

    /**
     * Send a message to a parent
     */
    public CompletableFuture<ParentMessageResponse> sendMessage(ParentMessageRequest request) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Map<String, Object> requestBody = new HashMap<>();
                requestBody.put("studentId", request.getStudentId());
                requestBody.put("category", request.getCategory());
                requestBody.put("priority", request.getPriority());
                requestBody.put("subject", request.getSubject());
                requestBody.put("content", request.getContent());
                requestBody.put("parentToken", request.getParentToken());
                requestBody.put("requiresAcknowledgment", request.isRequiresAcknowledgment());

                if (request.getDeliveryOptions() != null) {
                    Map<String, Boolean> delivery = new HashMap<>();
                    delivery.put("inApp", request.getDeliveryOptions().isInApp());
                    delivery.put("pushNotification", request.getDeliveryOptions().isPushNotification());
                    delivery.put("email", request.getDeliveryOptions().isEmail());
                    delivery.put("sms", request.getDeliveryOptions().isSms());
                    requestBody.put("deliveryChannels", delivery);
                }

                if (request.getMetadata() != null) {
                    requestBody.put("metadata", request.getMetadata());
                }

                return executePost(PARENT_PORTAL_API_PATH + "/send", requestBody);

            } catch (Exception e) {
                log.error("Failed to send parent message", e);
                return ParentMessageResponse.builder()
                        .success(false)
                        .message("Failed to send message: " + e.getMessage())
                        .timestamp(LocalDateTime.now())
                        .build();
            }
        });
    }

    /**
     * Send a notification to a parent
     */
    public CompletableFuture<ParentMessageResponse> sendNotification(
            ParentMessageRequest.NotificationRequest request) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Map<String, Object> requestBody = new HashMap<>();
                requestBody.put("studentId", request.getStudentId());
                requestBody.put("category", request.getCategory());
                requestBody.put("title", request.getTitle());
                requestBody.put("message", request.getMessage());
                requestBody.put("parentToken", request.getParentToken());

                return executePost(PARENT_PORTAL_API_PATH + "/notify", requestBody);

            } catch (Exception e) {
                log.error("Failed to send notification", e);
                return ParentMessageResponse.builder()
                        .success(false)
                        .message("Failed to send notification: " + e.getMessage())
                        .timestamp(LocalDateTime.now())
                        .build();
            }
        });
    }

    /**
     * Send a teacher message to a parent
     */
    public CompletableFuture<ParentMessageResponse> sendTeacherMessage(
            ParentMessageRequest.TeacherMessageRequest request) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Map<String, Object> requestBody = new HashMap<>();
                requestBody.put("studentId", request.getStudentId());
                requestBody.put("subject", request.getSubject());
                requestBody.put("message", request.getMessage());
                requestBody.put("parentToken", request.getParentToken());
                requestBody.put("requestMeeting", request.isRequestMeeting());
                requestBody.put("preferredContactMethod", request.getPreferredContactMethod());

                return executePost(PARENT_PORTAL_API_PATH + "/teacher-message", requestBody);

            } catch (Exception e) {
                log.error("Failed to send teacher message", e);
                return ParentMessageResponse.builder()
                        .success(false)
                        .message("Failed to send teacher message: " + e.getMessage())
                        .timestamp(LocalDateTime.now())
                        .build();
            }
        });
    }

    /**
     * Send an urgent alert to a parent
     */
    public CompletableFuture<ParentMessageResponse> sendAlert(
            ParentMessageRequest.AlertRequest request) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Map<String, Object> requestBody = new HashMap<>();
                requestBody.put("studentId", request.getStudentId());
                requestBody.put("alertType", request.getAlertType());
                requestBody.put("title", request.getTitle());
                requestBody.put("message", request.getMessage());
                requestBody.put("parentToken", request.getParentToken());
                requestBody.put("requiresImmediateAction", request.isRequiresImmediateAction());
                requestBody.put("actionUrl", request.getActionUrl());

                return executePost(PARENT_PORTAL_API_PATH + "/alert", requestBody);

            } catch (Exception e) {
                log.error("Failed to send alert", e);
                return ParentMessageResponse.builder()
                        .success(false)
                        .message("Failed to send alert: " + e.getMessage())
                        .timestamp(LocalDateTime.now())
                        .build();
            }
        });
    }

    /**
     * Send a hall pass notification to a parent
     */
    public CompletableFuture<ParentMessageResponse> sendHallPassNotification(
            ParentMessageRequest.HallPassNotificationRequest request) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Map<String, Object> requestBody = new HashMap<>();
                requestBody.put("studentId", request.getStudentId());
                requestBody.put("passType", request.getPassType());
                requestBody.put("destination", request.getDestination());
                requestBody.put("parentToken", request.getParentToken());

                if (request.getDepartureTime() != null) {
                    requestBody.put("departureTime", request.getDepartureTime().toString());
                }
                if (request.getReturnTime() != null) {
                    requestBody.put("returnTime", request.getReturnTime().toString());
                }
                if (request.getDurationMinutes() != null) {
                    requestBody.put("durationMinutes", request.getDurationMinutes());
                }

                return executePost(PARENT_PORTAL_API_PATH + "/hall-pass", requestBody);

            } catch (Exception e) {
                log.error("Failed to send hall pass notification", e);
                return ParentMessageResponse.builder()
                        .success(false)
                        .message("Failed to send hall pass notification: " + e.getMessage())
                        .timestamp(LocalDateTime.now())
                        .build();
            }
        });
    }

    /**
     * Send a school announcement to multiple parents
     */
    public CompletableFuture<ParentMessageResponse.BulkSendResponse> sendAnnouncement(
            ParentMessageRequest.AnnouncementRequest request) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Map<String, Object> requestBody = new HashMap<>();
                requestBody.put("subject", request.getSubject());
                requestBody.put("content", request.getContent());
                requestBody.put("category", request.getCategory());
                requestBody.put("parentTokens", request.getParentTokens());

                if (request.getEffectiveDate() != null) {
                    requestBody.put("effectiveDate", request.getEffectiveDate().toString());
                }
                if (request.getExpirationDate() != null) {
                    requestBody.put("expirationDate", request.getExpirationDate().toString());
                }

                String jsonBody = objectMapper.writeValueAsString(requestBody);

                HttpRequest httpRequest = HttpRequest.newBuilder()
                        .uri(URI.create(activeBaseUrl + PARENT_PORTAL_API_PATH + "/announcement"))
                        .header("Content-Type", "application/json")
                        .header("X-Session-Token", sessionToken != null ? sessionToken : "")
                        .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                        .timeout(Duration.ofSeconds(30))
                        .build();

                HttpResponse<String> response = httpClient.send(httpRequest,
                        HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200) {
                    return objectMapper.readValue(response.body(),
                            ParentMessageResponse.BulkSendResponse.class);
                } else {
                    log.error("Announcement send failed with status: {}", response.statusCode());
                    return ParentMessageResponse.BulkSendResponse.builder()
                            .success(false)
                            .message("Failed with status: " + response.statusCode())
                            .build();
                }

            } catch (Exception e) {
                log.error("Failed to send announcement", e);
                return ParentMessageResponse.BulkSendResponse.builder()
                        .success(false)
                        .message("Failed to send announcement: " + e.getMessage())
                        .build();
            }
        });
    }

    // ========================================================================
    // SERVICE STATUS
    // ========================================================================

    /**
     * Check if parent messaging service is available
     */
    public CompletableFuture<ParentMessageResponse.ServiceStatus> getServiceStatus() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(activeBaseUrl + PARENT_PORTAL_API_PATH + "/status"))
                        .header("X-Session-Token", sessionToken != null ? sessionToken : "")
                        .GET()
                        .timeout(Duration.ofSeconds(10))
                        .build();

                HttpResponse<String> response = httpClient.send(request,
                        HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200) {
                    return objectMapper.readValue(response.body(),
                            ParentMessageResponse.ServiceStatus.class);
                } else {
                    return ParentMessageResponse.ServiceStatus.builder()
                            .available(false)
                            .message("Service returned status: " + response.statusCode())
                            .build();
                }

            } catch (Exception e) {
                log.error("Failed to check service status", e);
                return ParentMessageResponse.ServiceStatus.builder()
                        .available(false)
                        .message("Service unavailable: " + e.getMessage())
                        .build();
            }
        });
    }

    /**
     * Check if service is available (synchronous, quick check)
     */
    public boolean isServiceAvailable() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(activeBaseUrl + PARENT_PORTAL_API_PATH + "/status"))
                    .header("X-Session-Token", sessionToken != null ? sessionToken : "")
                    .GET()
                    .timeout(Duration.ofSeconds(5))
                    .build();

            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString());

            return response.statusCode() == 200;

        } catch (Exception e) {
            log.debug("Service availability check failed: {}", e.getMessage());
            return tryFallbackAvailability();
        }
    }

    private boolean tryFallbackAvailability() {
        for (String fallbackUrl : fallbackUrls) {
            try {
                String trimmedUrl = fallbackUrl.trim();

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(trimmedUrl + PARENT_PORTAL_API_PATH + "/status"))
                        .GET()
                        .timeout(Duration.ofSeconds(5))
                        .build();

                HttpResponse<String> response = httpClient.send(request,
                        HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200) {
                    this.activeBaseUrl = trimmedUrl;
                    log.info("Switched to fallback URL: {}", trimmedUrl);
                    return true;
                }
            } catch (Exception e) {
                log.debug("Fallback {} unavailable: {}", fallbackUrl, e.getMessage());
            }
        }
        return false;
    }

    // ========================================================================
    // CONVENIENCE METHODS
    // ========================================================================

    /**
     * Quick send: Simple notification to parent
     */
    public CompletableFuture<ParentMessageResponse> quickNotify(
            Long studentId, String parentToken, String title, String message) {
        return sendNotification(ParentMessageRequest.NotificationRequest.builder()
                .studentId(studentId)
                .parentToken(parentToken)
                .category("NOTIFICATION")
                .title(title)
                .message(message)
                .build());
    }

    /**
     * Quick send: Hall pass departure notification
     */
    public CompletableFuture<ParentMessageResponse> notifyHallPassDeparture(
            Long studentId, String parentToken, String destination) {
        return sendHallPassNotification(ParentMessageRequest.HallPassNotificationRequest.builder()
                .studentId(studentId)
                .parentToken(parentToken)
                .passType("DEPARTURE")
                .destination(destination)
                .departureTime(LocalDateTime.now())
                .build());
    }

    /**
     * Quick send: Hall pass return notification
     */
    public CompletableFuture<ParentMessageResponse> notifyHallPassReturn(
            Long studentId, String parentToken, String destination, int durationMinutes) {
        return sendHallPassNotification(ParentMessageRequest.HallPassNotificationRequest.builder()
                .studentId(studentId)
                .parentToken(parentToken)
                .passType("RETURN")
                .destination(destination)
                .returnTime(LocalDateTime.now())
                .durationMinutes(durationMinutes)
                .build());
    }

    /**
     * Quick send: Attendance alert
     */
    public CompletableFuture<ParentMessageResponse> sendAttendanceAlert(
            Long studentId, String parentToken, String alertType, String message) {
        return sendAlert(ParentMessageRequest.AlertRequest.builder()
                .studentId(studentId)
                .parentToken(parentToken)
                .alertType("ATTENDANCE_" + alertType)
                .title("Attendance Alert")
                .message(message)
                .requiresImmediateAction(false)
                .build());
    }

    /**
     * Quick send: Behavior notification
     */
    public CompletableFuture<ParentMessageResponse> sendBehaviorNotification(
            Long studentId, String parentToken, String subject, String details, boolean urgent) {
        if (urgent) {
            return sendAlert(ParentMessageRequest.AlertRequest.builder()
                    .studentId(studentId)
                    .parentToken(parentToken)
                    .alertType("BEHAVIOR")
                    .title(subject)
                    .message(details)
                    .requiresImmediateAction(true)
                    .build());
        } else {
            return sendNotification(ParentMessageRequest.NotificationRequest.builder()
                    .studentId(studentId)
                    .parentToken(parentToken)
                    .category("BEHAVIOR")
                    .title(subject)
                    .message(details)
                    .build());
        }
    }

    // ========================================================================
    // INTERNAL HELPERS
    // ========================================================================

    private ParentMessageResponse executePost(String path, Map<String, Object> body) {
        try {
            String jsonBody = objectMapper.writeValueAsString(body);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(activeBaseUrl + path))
                    .header("Content-Type", "application/json")
                    .header("X-Session-Token", sessionToken != null ? sessionToken : "")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .timeout(Duration.ofSeconds(15))
                    .build();

            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                return objectMapper.readValue(response.body(), ParentMessageResponse.class);
            } else {
                log.error("Request to {} failed with status: {}", path, response.statusCode());

                // Try fallback
                return tryFallbackPost(path, jsonBody);
            }

        } catch (Exception e) {
            log.error("Request to {} failed", path, e);
            return tryFallbackPost(path, null);
        }
    }

    private ParentMessageResponse tryFallbackPost(String path, String jsonBody) {
        if (jsonBody == null) {
            return ParentMessageResponse.builder()
                    .success(false)
                    .message("Request failed and no body for retry")
                    .timestamp(LocalDateTime.now())
                    .build();
        }

        for (String fallbackUrl : fallbackUrls) {
            try {
                String trimmedUrl = fallbackUrl.trim();
                log.info("Trying fallback URL: {}", trimmedUrl);

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(trimmedUrl + path))
                        .header("Content-Type", "application/json")
                        .header("X-Session-Token", sessionToken != null ? sessionToken : "")
                        .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                        .timeout(Duration.ofSeconds(15))
                        .build();

                HttpResponse<String> response = httpClient.send(request,
                        HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200) {
                    this.activeBaseUrl = trimmedUrl;
                    return objectMapper.readValue(response.body(), ParentMessageResponse.class);
                }

            } catch (Exception e) {
                log.debug("Fallback {} failed: {}", fallbackUrl, e.getMessage());
            }
        }

        return ParentMessageResponse.builder()
                .success(false)
                .message("All servers unavailable")
                .timestamp(LocalDateTime.now())
                .build();
    }
}
