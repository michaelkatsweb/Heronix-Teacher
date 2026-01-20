package com.heronix.teacher.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.heronix.teacher.model.dto.talk.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

/**
 * REST API Client for Heronix-Talk messaging server
 * Handles authentication, channels, messages, users, and presence
 */
@Slf4j
@Service
public class HeronixTalkApiClient {

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String baseUrl;
    private final String[] fallbackUrls;
    private String sessionToken;
    private TalkUserDTO currentUser;
    private String activeBaseUrl;

    public HeronixTalkApiClient(
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

    // ========================================================================
    // AUTHENTICATION
    // ========================================================================

    /**
     * Authenticate with Heronix-Talk server
     */
    public boolean authenticate(String username, String password) {
        try {
            TalkAuthRequest authRequest = TalkAuthRequest.builder()
                    .username(username)
                    .password(password)
                    .clientType("heronix-teacher")
                    .clientVersion("1.0.0")
                    .deviceName(System.getProperty("os.name"))
                    .rememberMe(true)
                    .build();

            String requestBody = objectMapper.writeValueAsString(authRequest);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(activeBaseUrl + "/api/auth/login"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .timeout(Duration.ofSeconds(10))
                    .build();

            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                TalkAuthResponse authResponse = objectMapper.readValue(
                        response.body(), TalkAuthResponse.class);
                if (authResponse.isSuccess()) {
                    this.sessionToken = authResponse.getSessionToken();
                    this.currentUser = authResponse.getUser();
                    log.info("Talk authentication successful for user: {}", username);
                    return true;
                } else {
                    log.warn("Talk authentication failed: {}", authResponse.getMessage());
                    return false;
                }
            } else {
                log.error("Talk authentication failed with status: {}", response.statusCode());
                return false;
            }

        } catch (Exception e) {
            log.error("Error during Talk authentication", e);
            // Try fallback URLs
            return tryFallbackAuthentication(username, password);
        }
    }

    private boolean tryFallbackAuthentication(String username, String password) {
        for (String fallbackUrl : fallbackUrls) {
            try {
                String trimmedUrl = fallbackUrl.trim();
                log.info("Trying fallback Talk server: {}", trimmedUrl);

                TalkAuthRequest authRequest = TalkAuthRequest.builder()
                        .username(username)
                        .password(password)
                        .clientType("heronix-teacher")
                        .clientVersion("1.0.0")
                        .deviceName(System.getProperty("os.name"))
                        .rememberMe(true)
                        .build();

                String requestBody = objectMapper.writeValueAsString(authRequest);

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(trimmedUrl + "/api/auth/login"))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                        .timeout(Duration.ofSeconds(10))
                        .build();

                HttpResponse<String> response = httpClient.send(request,
                        HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200) {
                    TalkAuthResponse authResponse = objectMapper.readValue(
                            response.body(), TalkAuthResponse.class);
                    if (authResponse.isSuccess()) {
                        this.sessionToken = authResponse.getSessionToken();
                        this.currentUser = authResponse.getUser();
                        this.activeBaseUrl = trimmedUrl;
                        log.info("Talk authentication successful via fallback: {}", trimmedUrl);
                        return true;
                    }
                }
            } catch (Exception e) {
                log.debug("Fallback {} failed: {}", fallbackUrl, e.getMessage());
            }
        }
        return false;
    }

    /**
     * Logout from Heronix-Talk server
     */
    public void logout() {
        if (sessionToken == null) return;

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(activeBaseUrl + "/api/auth/logout"))
                    .header("X-Session-Token", sessionToken)
                    .POST(HttpRequest.BodyPublishers.noBody())
                    .timeout(Duration.ofSeconds(5))
                    .build();

            httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            log.info("Talk logout successful");

        } catch (Exception e) {
            log.warn("Error during Talk logout: {}", e.getMessage());
        } finally {
            sessionToken = null;
            currentUser = null;
        }
    }

    /**
     * Validate current session
     */
    public boolean validateSession() {
        if (sessionToken == null) return false;

        try {
            HttpRequest request = buildGetRequest("/api/auth/validate");
            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString());
            return response.statusCode() == 200;
        } catch (Exception e) {
            log.debug("Session validation failed: {}", e.getMessage());
            return false;
        }
    }

    // ========================================================================
    // CHANNELS
    // ========================================================================

    /**
     * Get all channels the user is a member of
     */
    public List<TalkChannelDTO> getMyChannels() throws Exception {
        HttpRequest request = buildGetRequest("/api/channels/my");
        HttpResponse<String> response = httpClient.send(request,
                HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 200) {
            return objectMapper.readValue(response.body(),
                    new TypeReference<List<TalkChannelDTO>>() {});
        } else {
            throw new Exception("Failed to fetch channels: " + response.statusCode());
        }
    }

    /**
     * Get public channels
     */
    public List<TalkChannelDTO> getPublicChannels() throws Exception {
        HttpRequest request = buildGetRequest("/api/channels/public");
        HttpResponse<String> response = httpClient.send(request,
                HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 200) {
            return objectMapper.readValue(response.body(),
                    new TypeReference<List<TalkChannelDTO>>() {});
        } else {
            throw new Exception("Failed to fetch public channels: " + response.statusCode());
        }
    }

    /**
     * Get direct message channels
     */
    public List<TalkChannelDTO> getDirectMessages() throws Exception {
        HttpRequest request = buildGetRequest("/api/channels/dm");
        HttpResponse<String> response = httpClient.send(request,
                HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 200) {
            return objectMapper.readValue(response.body(),
                    new TypeReference<List<TalkChannelDTO>>() {});
        } else {
            throw new Exception("Failed to fetch DMs: " + response.statusCode());
        }
    }

    /**
     * Join a channel
     */
    public void joinChannel(Long channelId) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(activeBaseUrl + "/api/channels/" + channelId + "/join"))
                .header("X-Session-Token", sessionToken)
                .POST(HttpRequest.BodyPublishers.noBody())
                .timeout(Duration.ofSeconds(10))
                .build();

        HttpResponse<String> response = httpClient.send(request,
                HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new Exception("Failed to join channel: " + response.statusCode());
        }
    }

    /**
     * Leave a channel
     */
    public void leaveChannel(Long channelId) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(activeBaseUrl + "/api/channels/" + channelId + "/leave"))
                .header("X-Session-Token", sessionToken)
                .POST(HttpRequest.BodyPublishers.noBody())
                .timeout(Duration.ofSeconds(10))
                .build();

        HttpResponse<String> response = httpClient.send(request,
                HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new Exception("Failed to leave channel: " + response.statusCode());
        }
    }

    /**
     * Get or create direct message channel with another user
     */
    public TalkChannelDTO getOrCreateDirectMessage(Long targetUserId) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(activeBaseUrl + "/api/channels/dm/" + targetUserId))
                .header("X-Session-Token", sessionToken)
                .POST(HttpRequest.BodyPublishers.noBody())
                .timeout(Duration.ofSeconds(10))
                .build();

        HttpResponse<String> response = httpClient.send(request,
                HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 200 || response.statusCode() == 201) {
            return objectMapper.readValue(response.body(), TalkChannelDTO.class);
        } else {
            throw new Exception("Failed to get/create DM: " + response.statusCode());
        }
    }

    // ========================================================================
    // MESSAGES
    // ========================================================================

    /**
     * Get messages for a channel (paginated)
     */
    public List<TalkMessageDTO> getMessages(Long channelId, int page, int size) throws Exception {
        HttpRequest request = buildGetRequest(
                "/api/messages/channel/" + channelId + "?page=" + page + "&size=" + size);
        HttpResponse<String> response = httpClient.send(request,
                HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 200) {
            return objectMapper.readValue(response.body(),
                    new TypeReference<List<TalkMessageDTO>>() {});
        } else {
            throw new Exception("Failed to fetch messages: " + response.statusCode());
        }
    }

    /**
     * Send a message via REST API (WebSocket preferred for real-time)
     */
    public TalkMessageDTO sendMessage(Long channelId, String content, String clientId) throws Exception {
        var payload = new java.util.HashMap<String, Object>();
        payload.put("channelId", channelId);
        payload.put("content", content);
        payload.put("messageType", "TEXT");
        payload.put("clientId", clientId);

        String requestBody = objectMapper.writeValueAsString(payload);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(activeBaseUrl + "/api/messages"))
                .header("Content-Type", "application/json")
                .header("X-Session-Token", sessionToken)
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .timeout(Duration.ofSeconds(10))
                .build();

        HttpResponse<String> response = httpClient.send(request,
                HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 200 || response.statusCode() == 201) {
            return objectMapper.readValue(response.body(), TalkMessageDTO.class);
        } else {
            throw new Exception("Failed to send message: " + response.statusCode());
        }
    }

    /**
     * Mark messages as read up to a specific message
     */
    public void markRead(Long channelId, Long messageId) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(activeBaseUrl + "/api/messages/channel/" + channelId + "/read?messageId=" + messageId))
                .header("X-Session-Token", sessionToken)
                .POST(HttpRequest.BodyPublishers.noBody())
                .timeout(Duration.ofSeconds(10))
                .build();

        HttpResponse<String> response = httpClient.send(request,
                HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            log.warn("Failed to mark messages as read: {}", response.statusCode());
        }
    }

    // ========================================================================
    // USERS
    // ========================================================================

    /**
     * Get all active users
     */
    public List<TalkUserDTO> getUsers() throws Exception {
        HttpRequest request = buildGetRequest("/api/users");
        HttpResponse<String> response = httpClient.send(request,
                HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 200) {
            return objectMapper.readValue(response.body(),
                    new TypeReference<List<TalkUserDTO>>() {});
        } else {
            throw new Exception("Failed to fetch users: " + response.statusCode());
        }
    }

    /**
     * Get online users
     */
    public List<TalkUserDTO> getOnlineUsers() throws Exception {
        HttpRequest request = buildGetRequest("/api/users/online");
        HttpResponse<String> response = httpClient.send(request,
                HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 200) {
            return objectMapper.readValue(response.body(),
                    new TypeReference<List<TalkUserDTO>>() {});
        } else {
            throw new Exception("Failed to fetch online users: " + response.statusCode());
        }
    }

    /**
     * Search users by name/keyword
     */
    public List<TalkUserDTO> searchUsers(String query) throws Exception {
        HttpRequest request = buildGetRequest("/api/users/search?q=" +
                java.net.URLEncoder.encode(query, java.nio.charset.StandardCharsets.UTF_8));
        HttpResponse<String> response = httpClient.send(request,
                HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 200) {
            return objectMapper.readValue(response.body(),
                    new TypeReference<List<TalkUserDTO>>() {});
        } else {
            throw new Exception("Failed to search users: " + response.statusCode());
        }
    }

    // ========================================================================
    // PRESENCE
    // ========================================================================

    /**
     * Update user's presence status
     */
    public void updateStatus(String status, String statusMessage) throws Exception {
        var payload = new java.util.HashMap<String, Object>();
        payload.put("status", status);
        payload.put("statusMessage", statusMessage);

        String requestBody = objectMapper.writeValueAsString(payload);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(activeBaseUrl + "/api/presence/status"))
                .header("Content-Type", "application/json")
                .header("X-Session-Token", sessionToken)
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .timeout(Duration.ofSeconds(10))
                .build();

        HttpResponse<String> response = httpClient.send(request,
                HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new Exception("Failed to update status: " + response.statusCode());
        }
    }

    /**
     * Send heartbeat to maintain presence
     */
    public void sendHeartbeat() {
        if (sessionToken == null) return;

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(activeBaseUrl + "/api/presence/heartbeat"))
                    .header("X-Session-Token", sessionToken)
                    .POST(HttpRequest.BodyPublishers.noBody())
                    .timeout(Duration.ofSeconds(5))
                    .build();

            httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        } catch (Exception e) {
            log.debug("Heartbeat failed: {}", e.getMessage());
        }
    }

    // ========================================================================
    // HEALTH CHECK
    // ========================================================================

    /**
     * Check if Talk server is reachable
     */
    public boolean isServerReachable() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(activeBaseUrl + "/api/system/health"))
                    .GET()
                    .timeout(Duration.ofSeconds(5))
                    .build();

            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString());

            return response.statusCode() == 200;

        } catch (Exception e) {
            log.debug("Talk server not reachable: {}", e.getMessage());
            return false;
        }
    }

    // ========================================================================
    // HELPER METHODS
    // ========================================================================

    private HttpRequest buildGetRequest(String endpoint) {
        return HttpRequest.newBuilder()
                .uri(URI.create(activeBaseUrl + endpoint))
                .header("X-Session-Token", sessionToken)
                .GET()
                .timeout(Duration.ofSeconds(10))
                .build();
    }

    public String getSessionToken() {
        return sessionToken;
    }

    public TalkUserDTO getCurrentUser() {
        return currentUser;
    }

    public boolean isAuthenticated() {
        return sessionToken != null && !sessionToken.isEmpty();
    }

    public String getActiveBaseUrl() {
        return activeBaseUrl;
    }

    public String getWebSocketUrl() {
        return activeBaseUrl.replace("http://", "ws://").replace("https://", "wss://")
                + "/ws/chat?token=" + sessionToken;
    }
}
