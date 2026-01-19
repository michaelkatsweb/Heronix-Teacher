package com.heronix.teacher.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * API Client for Heronix Ed-Games Server
 * Handles device management operations
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EdGamesApiClient {

    private static final String BASE_URL = "http://localhost:8081/api";
    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    private final ObjectMapper objectMapper;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(TIMEOUT)
            .build();

    private String jwtToken;

    /**
     * Authenticate teacher with Ed-Games server
     */
    public boolean authenticate(String username, String password) {
        try {
            String requestBody = objectMapper.writeValueAsString(Map.of(
                    "username", username,
                    "password", password
            ));

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(BASE_URL + "/teacher/login"))
                    .timeout(TIMEOUT)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                Map<String, Object> responseMap = objectMapper.readValue(response.body(), Map.class);
                this.jwtToken = (String) responseMap.get("token");
                log.info("Ed-Games authentication successful");
                return true;
            }

            log.warn("Ed-Games authentication failed: {}", response.statusCode());
            return false;

        } catch (Exception e) {
            log.error("Error authenticating with Ed-Games server", e);
            return false;
        }
    }

    /**
     * Get all pending devices awaiting approval
     */
    public List<Map<String, Object>> getPendingDevices() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(BASE_URL + "/device/management/pending"))
                    .timeout(TIMEOUT)
                    .header("Authorization", "Bearer " + jwtToken)
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                return objectMapper.readValue(response.body(), List.class);
            }

            log.warn("Failed to get pending devices: {}", response.statusCode());
            return List.of();

        } catch (Exception e) {
            log.error("Error getting pending devices", e);
            return List.of();
        }
    }

    /**
     * Get all active/approved devices
     */
    public List<Map<String, Object>> getActiveDevices() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(BASE_URL + "/device/management/active"))
                    .timeout(TIMEOUT)
                    .header("Authorization", "Bearer " + jwtToken)
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                return objectMapper.readValue(response.body(), List.class);
            }

            log.warn("Failed to get active devices: {}", response.statusCode());
            return List.of();

        } catch (Exception e) {
            log.error("Error getting active devices", e);
            return List.of();
        }
    }

    /**
     * Get devices for a specific student
     */
    public List<Map<String, Object>> getDevicesByStudent(String studentId) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(BASE_URL + "/device/management/student/" + studentId))
                    .timeout(TIMEOUT)
                    .header("Authorization", "Bearer " + jwtToken)
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                return objectMapper.readValue(response.body(), List.class);
            }

            log.warn("Failed to get devices for student {}: {}", studentId, response.statusCode());
            return List.of();

        } catch (Exception e) {
            log.error("Error getting devices for student {}", studentId, e);
            return List.of();
        }
    }

    /**
     * Approve a device and assign it to a student
     */
    public boolean approveDevice(String deviceId, String studentId) {
        try {
            String requestBody = objectMapper.writeValueAsString(Map.of(
                    "studentId", studentId
            ));

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(BASE_URL + "/device/management/" + deviceId + "/approve"))
                    .timeout(TIMEOUT)
                    .header("Authorization", "Bearer " + jwtToken)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                log.info("Device {} approved for student {}", deviceId, studentId);
                return true;
            }

            log.warn("Failed to approve device {}: {}", deviceId, response.statusCode());
            return false;

        } catch (Exception e) {
            log.error("Error approving device {}", deviceId, e);
            return false;
        }
    }

    /**
     * Reject a device registration
     */
    public boolean rejectDevice(String deviceId, String reason) {
        try {
            String url = BASE_URL + "/device/management/" + deviceId + "/reject";
            if (reason != null && !reason.isEmpty()) {
                url += "?reason=" + reason;
            }

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(TIMEOUT)
                    .header("Authorization", "Bearer " + jwtToken)
                    .POST(HttpRequest.BodyPublishers.noBody())
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                log.info("Device {} rejected", deviceId);
                return true;
            }

            log.warn("Failed to reject device {}: {}", deviceId, response.statusCode());
            return false;

        } catch (Exception e) {
            log.error("Error rejecting device {}", deviceId, e);
            return false;
        }
    }

    /**
     * Revoke an approved device
     */
    public boolean revokeDevice(String deviceId, String reason) {
        try {
            String url = BASE_URL + "/device/management/" + deviceId + "/revoke";
            if (reason != null && !reason.isEmpty()) {
                url += "?reason=" + reason;
            }

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(TIMEOUT)
                    .header("Authorization", "Bearer " + jwtToken)
                    .POST(HttpRequest.BodyPublishers.noBody())
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                log.info("Device {} revoked", deviceId);
                return true;
            }

            log.warn("Failed to revoke device {}: {}", deviceId, response.statusCode());
            return false;

        } catch (Exception e) {
            log.error("Error revoking device {}", deviceId, e);
            return false;
        }
    }

    /**
     * Get device statistics
     */
    public Map<String, Object> getDeviceStats() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(BASE_URL + "/device/management/stats"))
                    .timeout(TIMEOUT)
                    .header("Authorization", "Bearer " + jwtToken)
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                return objectMapper.readValue(response.body(), Map.class);
            }

            log.warn("Failed to get device stats: {}", response.statusCode());
            return Map.of();

        } catch (Exception e) {
            log.error("Error getting device stats", e);
            return Map.of();
        }
    }

    /**
     * Check if Ed-Games server is reachable
     */
    public boolean isServerReachable() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(BASE_URL + "/ping"))
                    .timeout(Duration.ofSeconds(3))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return response.statusCode() == 200;

        } catch (Exception e) {
            log.debug("Ed-Games server not reachable", e);
            return false;
        }
    }

    /**
     * Set JWT token (if authenticated elsewhere)
     */
    public void setJwtToken(String token) {
        this.jwtToken = token;
    }

    // =============================================
    // GAME ANALYTICS METHODS
    // =============================================

    /**
     * Get play time report for a specific student (all time)
     */
    public Map<String, Object> getStudentPlayTimeReport(String studentId) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(BASE_URL + "/analytics/student/" + studentId))
                    .timeout(TIMEOUT)
                    .header("Authorization", "Bearer " + jwtToken)
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                return objectMapper.readValue(response.body(), Map.class);
            }

            log.warn("Failed to get student play time report: {}", response.statusCode());
            return Map.of();

        } catch (Exception e) {
            log.error("Error getting student play time report for {}", studentId, e);
            return Map.of();
        }
    }

    /**
     * Get play time report for a specific student within a date range
     */
    public Map<String, Object> getStudentPlayTimeReport(String studentId, String startDate, String endDate) {
        try {
            String url = String.format("%s/analytics/student/%s/range?startDate=%s&endDate=%s",
                    BASE_URL, studentId, startDate, endDate);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(TIMEOUT)
                    .header("Authorization", "Bearer " + jwtToken)
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                return objectMapper.readValue(response.body(), Map.class);
            }

            log.warn("Failed to get student play time report with range: {}", response.statusCode());
            return Map.of();

        } catch (Exception e) {
            log.error("Error getting student play time report for {} with date range", studentId, e);
            return Map.of();
        }
    }

    /**
     * Get play time report for a device
     */
    public Map<String, Object> getDevicePlayTimeReport(String deviceId) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(BASE_URL + "/analytics/device/" + deviceId))
                    .timeout(TIMEOUT)
                    .header("Authorization", "Bearer " + jwtToken)
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                return objectMapper.readValue(response.body(), Map.class);
            }

            log.warn("Failed to get device play time report: {}", response.statusCode());
            return Map.of();

        } catch (Exception e) {
            log.error("Error getting device play time report for {}", deviceId, e);
            return Map.of();
        }
    }

    /**
     * Get class-wide play time report for a date range
     */
    public Map<String, Object> getClassPlayTimeReport(String startDate, String endDate) {
        try {
            String url = String.format("%s/analytics/class?startDate=%s&endDate=%s",
                    BASE_URL, startDate, endDate);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(TIMEOUT)
                    .header("Authorization", "Bearer " + jwtToken)
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                return objectMapper.readValue(response.body(), Map.class);
            }

            log.warn("Failed to get class play time report: {}", response.statusCode());
            return Map.of();

        } catch (Exception e) {
            log.error("Error getting class play time report", e);
            return Map.of();
        }
    }

    /**
     * Export class report as CSV (returns the CSV content as a string)
     */
    public String exportClassReportCSV(String startDate, String endDate) {
        try {
            String url = String.format("%s/analytics/class/export/csv?startDate=%s&endDate=%s",
                    BASE_URL, startDate, endDate);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(TIMEOUT)
                    .header("Authorization", "Bearer " + jwtToken)
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                return response.body();
            }

            log.warn("Failed to export class report CSV: {}", response.statusCode());
            return null;

        } catch (Exception e) {
            log.error("Error exporting class report CSV", e);
            return null;
        }
    }

    /**
     * Get parent-friendly summary for a student
     */
    public String getParentSummary(String studentId) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(BASE_URL + "/analytics/student/" + studentId + "/parent-summary"))
                    .timeout(TIMEOUT)
                    .header("Authorization", "Bearer " + jwtToken)
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                return response.body();
            }

            log.warn("Failed to get parent summary: {}", response.statusCode());
            return null;

        } catch (Exception e) {
            log.error("Error getting parent summary for {}", studentId, e);
            return null;
        }
    }

    /**
     * Get parent-friendly summary for a student with date range
     */
    public String getParentSummary(String studentId, String startDate, String endDate) {
        try {
            String url = String.format("%s/analytics/student/%s/parent-summary/range?startDate=%s&endDate=%s",
                    BASE_URL, studentId, startDate, endDate);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(TIMEOUT)
                    .header("Authorization", "Bearer " + jwtToken)
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                return response.body();
            }

            log.warn("Failed to get parent summary with range: {}", response.statusCode());
            return null;

        } catch (Exception e) {
            log.error("Error getting parent summary for {} with date range", studentId, e);
            return null;
        }
    }

    // =============================================
    // MULTIPLAYER GAME SESSION METHODS
    // =============================================

    /**
     * Create a new multiplayer game session
     */
    public Map<String, Object> createGameSession(String questionSetId, String gameType, int timeLimitMinutes, int targetCredits) {
        try {
            Map<String, Object> requestBody = Map.of(
                    "questionSetId", questionSetId,
                    "gameType", gameType,
                    "timeLimitMinutes", timeLimitMinutes,
                    "targetCredits", targetCredits
            );

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(BASE_URL + "/game/sessions"))
                    .timeout(TIMEOUT)
                    .header("Authorization", "Bearer " + jwtToken)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(requestBody)))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                log.info("Created game session successfully");
                return objectMapper.readValue(response.body(), Map.class);
            }

            log.warn("Failed to create game session: {}", response.statusCode());
            return Map.of("error", "Failed to create session");

        } catch (Exception e) {
            log.error("Error creating game session", e);
            return Map.of("error", e.getMessage());
        }
    }

    /**
     * Get session details
     */
    public Map<String, Object> getGameSession(String sessionCode) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(BASE_URL + "/game/sessions/" + sessionCode))
                    .timeout(TIMEOUT)
                    .header("Authorization", "Bearer " + jwtToken)
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                return objectMapper.readValue(response.body(), Map.class);
            }

            log.warn("Failed to get game session: {}", response.statusCode());
            return Map.of();

        } catch (Exception e) {
            log.error("Error getting game session {}", sessionCode, e);
            return Map.of();
        }
    }

    /**
     * Get all teacher's game sessions
     */
    public List<Map<String, Object>> getTeacherGameSessions() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(BASE_URL + "/game/sessions"))
                    .timeout(TIMEOUT)
                    .header("Authorization", "Bearer " + jwtToken)
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                return objectMapper.readValue(response.body(), List.class);
            }

            log.warn("Failed to get teacher game sessions: {}", response.statusCode());
            return List.of();

        } catch (Exception e) {
            log.error("Error getting teacher game sessions", e);
            return List.of();
        }
    }

    /**
     * Get session leaderboard
     */
    public List<Map<String, Object>> getSessionLeaderboard(String sessionCode) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(BASE_URL + "/game/sessions/" + sessionCode + "/leaderboard"))
                    .timeout(TIMEOUT)
                    .header("Authorization", "Bearer " + jwtToken)
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                return objectMapper.readValue(response.body(), List.class);
            }

            log.warn("Failed to get session leaderboard: {}", response.statusCode());
            return List.of();

        } catch (Exception e) {
            log.error("Error getting session leaderboard for {}", sessionCode, e);
            return List.of();
        }
    }

    /**
     * Get available question sets
     */
    public List<Map<String, Object>> getQuestionSets() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(BASE_URL + "/game/question-sets"))
                    .timeout(TIMEOUT)
                    .header("Authorization", "Bearer " + jwtToken)
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                return objectMapper.readValue(response.body(), List.class);
            }

            log.warn("Failed to get question sets: {}", response.statusCode());
            return List.of();

        } catch (Exception e) {
            log.error("Error getting question sets", e);
            return List.of();
        }
    }

    /**
     * Get preset/public question sets (system-provided)
     */
    public List<Map<String, Object>> getPresetQuestionSets() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(BASE_URL + "/game/question-sets/presets"))
                    .timeout(TIMEOUT)
                    .header("Authorization", "Bearer " + jwtToken)
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                return objectMapper.readValue(response.body(), List.class);
            }

            log.warn("Failed to get preset question sets: {}", response.statusCode());
            return List.of();

        } catch (Exception e) {
            log.error("Error getting preset question sets", e);
            return List.of();
        }
    }

    /**
     * Create a new question set
     */
    public Map<String, Object> createQuestionSet(String name, String description, String subject, String gradeLevel) {
        try {
            Map<String, Object> requestBody = Map.of(
                    "name", name,
                    "description", description != null ? description : "",
                    "subject", subject != null ? subject : "",
                    "gradeLevel", gradeLevel != null ? gradeLevel : "",
                    "isPublic", false
            );

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(BASE_URL + "/game/question-sets"))
                    .timeout(TIMEOUT)
                    .header("Authorization", "Bearer " + jwtToken)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(requestBody)))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                log.info("Created question set: {}", name);
                return objectMapper.readValue(response.body(), Map.class);
            }

            log.warn("Failed to create question set: {}", response.statusCode());
            return Map.of("error", "Failed to create question set");

        } catch (Exception e) {
            log.error("Error creating question set", e);
            return Map.of("error", e.getMessage());
        }
    }

    /**
     * Add a question to a question set
     */
    public Map<String, Object> addQuestion(String setId, String questionText, String correctAnswer,
                                            String wrongAnswer1, String wrongAnswer2, String wrongAnswer3,
                                            int difficulty) {
        try {
            Map<String, Object> requestBody = Map.of(
                    "questionText", questionText,
                    "correctAnswer", correctAnswer,
                    "wrongAnswer1", wrongAnswer1 != null ? wrongAnswer1 : "",
                    "wrongAnswer2", wrongAnswer2 != null ? wrongAnswer2 : "",
                    "wrongAnswer3", wrongAnswer3 != null ? wrongAnswer3 : "",
                    "difficulty", difficulty
            );

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(BASE_URL + "/game/question-sets/" + setId + "/questions"))
                    .timeout(TIMEOUT)
                    .header("Authorization", "Bearer " + jwtToken)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(requestBody)))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                log.info("Added question to set {}", setId);
                return objectMapper.readValue(response.body(), Map.class);
            }

            log.warn("Failed to add question: {}", response.statusCode());
            return Map.of("error", "Failed to add question");

        } catch (Exception e) {
            log.error("Error adding question to set {}", setId, e);
            return Map.of("error", e.getMessage());
        }
    }

    /**
     * Get WebSocket URL for real-time game updates
     */
    public String getWebSocketUrl() {
        return BASE_URL.replace("http://", "ws://").replace("/api", "") + "/ws/game";
    }
}
