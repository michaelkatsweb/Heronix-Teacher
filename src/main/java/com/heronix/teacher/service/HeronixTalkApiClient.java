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
        log.info("Fetching my channels from {}/api/channels/my with token: {}",
                activeBaseUrl, sessionToken != null ? sessionToken.substring(0, Math.min(10, sessionToken.length())) + "..." : "null");

        HttpRequest request = buildGetRequest("/api/channels/my");
        HttpResponse<String> response = httpClient.send(request,
                HttpResponse.BodyHandlers.ofString());

        log.info("getMyChannels response status: {}", response.statusCode());
        log.debug("getMyChannels response body: {}", response.body());

        if (response.statusCode() == 200) {
            List<TalkChannelDTO> channels = objectMapper.readValue(response.body(),
                    new TypeReference<List<TalkChannelDTO>>() {});
            log.info("Parsed {} channels from response", channels.size());
            return channels;
        } else {
            log.error("Failed to fetch my channels - Status: {}, Body: {}", response.statusCode(), response.body());
            throw new Exception("Failed to fetch channels: " + response.statusCode());
        }
    }

    /**
     * Get public channels
     */
    public List<TalkChannelDTO> getPublicChannels() throws Exception {
        log.info("Fetching public channels from {}/api/channels/public", activeBaseUrl);

        HttpRequest request = buildGetRequest("/api/channels/public");
        HttpResponse<String> response = httpClient.send(request,
                HttpResponse.BodyHandlers.ofString());

        log.info("getPublicChannels response status: {}", response.statusCode());
        log.debug("getPublicChannels response body: {}", response.body());

        if (response.statusCode() == 200) {
            List<TalkChannelDTO> channels = objectMapper.readValue(response.body(),
                    new TypeReference<List<TalkChannelDTO>>() {});
            log.info("Parsed {} public channels from response", channels.size());
            return channels;
        } else {
            log.error("Failed to fetch public channels - Status: {}, Body: {}", response.statusCode(), response.body());
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

    /**
     * Create a new channel with optional members
     * @param name Channel name
     * @param description Channel description
     * @param channelType Channel type (PUBLIC, PRIVATE, GROUP, DEPARTMENT, ANNOUNCEMENT)
     * @param icon Channel icon (optional)
     * @param memberIds List of user IDs to add as members (optional)
     * @param notifyMembers Whether to send invites to members
     * @return Created channel
     */
    public TalkChannelDTO createChannel(String name, String description, String channelType,
                                        String icon, List<Long> memberIds, boolean notifyMembers) throws Exception {
        var payload = new java.util.HashMap<String, Object>();
        payload.put("name", name);
        payload.put("description", description);
        payload.put("channelType", channelType);
        if (icon != null && !icon.isEmpty()) {
            payload.put("icon", icon);
        }
        if (memberIds != null && !memberIds.isEmpty()) {
            payload.put("memberIds", memberIds);
        }
        payload.put("notifyMembers", notifyMembers);

        String requestBody = objectMapper.writeValueAsString(payload);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(activeBaseUrl + "/api/channels"))
                .header("Content-Type", "application/json")
                .header("X-Session-Token", sessionToken)
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .timeout(Duration.ofSeconds(10))
                .build();

        HttpResponse<String> response = httpClient.send(request,
                HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 200 || response.statusCode() == 201) {
            log.info("Channel '{}' created successfully", name);
            return objectMapper.readValue(response.body(), TalkChannelDTO.class);
        } else {
            log.error("Failed to create channel '{}': status={}, body={}", name, response.statusCode(), response.body());
            throw new Exception("Failed to create channel: " + response.statusCode());
        }
    }

    // ========================================================================
    // MESSAGES
    // ========================================================================

    /**
     * Get messages for a channel (paginated)
     */
    public List<TalkMessageDTO> getMessages(Long channelId, int page, int size) throws Exception {
        if (channelId == null) {
            log.warn("Cannot fetch messages: channelId is null");
            return List.of();
        }

        log.debug("Fetching messages for channel {} (page={}, size={})", channelId, page, size);

        HttpRequest request = buildGetRequest(
                "/api/messages/channel/" + channelId + "?page=" + page + "&size=" + size);
        HttpResponse<String> response = httpClient.send(request,
                HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 200) {
            List<TalkMessageDTO> messages = objectMapper.readValue(response.body(),
                    new TypeReference<List<TalkMessageDTO>>() {});
            log.debug("Fetched {} messages for channel {}", messages.size(), channelId);
            return messages;
        } else if (response.statusCode() == 401) {
            log.warn("Unauthorized to fetch messages for channel {} - session may be invalid", channelId);
            throw new Exception("Unauthorized - please re-authenticate");
        } else if (response.statusCode() == 403) {
            log.warn("Forbidden to fetch messages for channel {} - not a member", channelId);
            throw new Exception("You are not a member of this channel");
        } else if (response.statusCode() == 404) {
            log.warn("Channel {} not found", channelId);
            throw new Exception("Channel not found");
        } else {
            log.error("Failed to fetch messages for channel {}: status={}, body={}",
                    channelId, response.statusCode(), response.body());
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

    /**
     * Toggle a reaction on a message (add if not present, remove if present)
     * @return Updated reactions map
     */
    public java.util.Map<String, List<Long>> toggleReaction(Long messageId, String emoji) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(activeBaseUrl + "/api/messages/" + messageId + "/reaction/toggle?emoji=" +
                        java.net.URLEncoder.encode(emoji, java.nio.charset.StandardCharsets.UTF_8)))
                .header("X-Session-Token", sessionToken)
                .POST(HttpRequest.BodyPublishers.noBody())
                .timeout(Duration.ofSeconds(10))
                .build();

        HttpResponse<String> response = httpClient.send(request,
                HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 200) {
            return objectMapper.readValue(response.body(),
                    new TypeReference<java.util.Map<String, List<Long>>>() {});
        } else {
            throw new Exception("Failed to toggle reaction: " + response.statusCode());
        }
    }

    /**
     * Add a reaction to a message
     */
    public java.util.Map<String, List<Long>> addReaction(Long messageId, String emoji) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(activeBaseUrl + "/api/messages/" + messageId + "/reaction?emoji=" +
                        java.net.URLEncoder.encode(emoji, java.nio.charset.StandardCharsets.UTF_8)))
                .header("X-Session-Token", sessionToken)
                .POST(HttpRequest.BodyPublishers.noBody())
                .timeout(Duration.ofSeconds(10))
                .build();

        HttpResponse<String> response = httpClient.send(request,
                HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 200) {
            return objectMapper.readValue(response.body(),
                    new TypeReference<java.util.Map<String, List<Long>>>() {});
        } else {
            throw new Exception("Failed to add reaction: " + response.statusCode());
        }
    }

    /**
     * Remove a reaction from a message
     */
    public java.util.Map<String, List<Long>> removeReaction(Long messageId, String emoji) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(activeBaseUrl + "/api/messages/" + messageId + "/reaction?emoji=" +
                        java.net.URLEncoder.encode(emoji, java.nio.charset.StandardCharsets.UTF_8)))
                .header("X-Session-Token", sessionToken)
                .DELETE()
                .timeout(Duration.ofSeconds(10))
                .build();

        HttpResponse<String> response = httpClient.send(request,
                HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 200) {
            return objectMapper.readValue(response.body(),
                    new TypeReference<java.util.Map<String, List<Long>>>() {});
        } else {
            throw new Exception("Failed to remove reaction: " + response.statusCode());
        }
    }

    /**
     * Get reactions for a message
     */
    public java.util.Map<String, List<Long>> getReactions(Long messageId) throws Exception {
        HttpRequest request = buildGetRequest("/api/messages/" + messageId + "/reactions");
        HttpResponse<String> response = httpClient.send(request,
                HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 200) {
            return objectMapper.readValue(response.body(),
                    new TypeReference<java.util.Map<String, List<Long>>>() {});
        } else {
            throw new Exception("Failed to get reactions: " + response.statusCode());
        }
    }

    /**
     * Edit a message
     */
    public TalkMessageDTO editMessage(Long messageId, String newContent) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(activeBaseUrl + "/api/messages/" + messageId))
                .header("Content-Type", "text/plain")
                .header("X-Session-Token", sessionToken)
                .PUT(HttpRequest.BodyPublishers.ofString(newContent))
                .timeout(Duration.ofSeconds(10))
                .build();

        HttpResponse<String> response = httpClient.send(request,
                HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 200) {
            return objectMapper.readValue(response.body(), TalkMessageDTO.class);
        } else {
            throw new Exception("Failed to edit message: " + response.statusCode());
        }
    }

    /**
     * Delete a message
     */
    public boolean deleteMessage(Long messageId) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(activeBaseUrl + "/api/messages/" + messageId))
                .header("X-Session-Token", sessionToken)
                .DELETE()
                .timeout(Duration.ofSeconds(10))
                .build();

        HttpResponse<String> response = httpClient.send(request,
                HttpResponse.BodyHandlers.ofString());

        return response.statusCode() == 200;
    }

    /**
     * Pin/unpin a message
     */
    public void pinMessage(Long messageId, boolean pinned) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(activeBaseUrl + "/api/messages/" + messageId + "/pin?pinned=" + pinned))
                .header("X-Session-Token", sessionToken)
                .POST(HttpRequest.BodyPublishers.noBody())
                .timeout(Duration.ofSeconds(10))
                .build();

        HttpResponse<String> response = httpClient.send(request,
                HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new Exception("Failed to pin message: " + response.statusCode());
        }
    }

    /**
     * Get pinned messages for a channel
     */
    public List<TalkMessageDTO> getPinnedMessages(Long channelId) throws Exception {
        HttpRequest request = buildGetRequest("/api/messages/channel/" + channelId + "/pinned");
        HttpResponse<String> response = httpClient.send(request,
                HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 200) {
            return objectMapper.readValue(response.body(),
                    new TypeReference<List<TalkMessageDTO>>() {});
        } else {
            throw new Exception("Failed to get pinned messages: " + response.statusCode());
        }
    }

    /**
     * Get replies to a message
     */
    public List<TalkMessageDTO> getReplies(Long messageId) throws Exception {
        HttpRequest request = buildGetRequest("/api/messages/" + messageId + "/replies");
        HttpResponse<String> response = httpClient.send(request,
                HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 200) {
            return objectMapper.readValue(response.body(),
                    new TypeReference<List<TalkMessageDTO>>() {});
        } else {
            throw new Exception("Failed to get replies: " + response.statusCode());
        }
    }

    /**
     * Send a reply to a message
     */
    public TalkMessageDTO sendReply(Long channelId, Long replyToId, String content, String clientId) throws Exception {
        var payload = new java.util.HashMap<String, Object>();
        payload.put("channelId", channelId);
        payload.put("content", content);
        payload.put("messageType", "REPLY");
        payload.put("replyToId", replyToId);
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
            throw new Exception("Failed to send reply: " + response.statusCode());
        }
    }

    /**
     * Search messages in a channel
     */
    public List<TalkMessageDTO> searchMessages(String query, Long channelId, int page, int size) throws Exception {
        String url = "/api/messages/search?q=" + java.net.URLEncoder.encode(query, java.nio.charset.StandardCharsets.UTF_8);
        if (channelId != null) {
            url += "&channelId=" + channelId;
        }
        url += "&page=" + page + "&size=" + size;

        HttpRequest request = buildGetRequest(url);
        HttpResponse<String> response = httpClient.send(request,
                HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 200) {
            return objectMapper.readValue(response.body(),
                    new TypeReference<List<TalkMessageDTO>>() {});
        } else {
            throw new Exception("Failed to search messages: " + response.statusCode());
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
    // NEWS
    // ========================================================================

    /**
     * Get visible news items
     */
    public List<TalkNewsItemDTO> getNews(int limit) throws Exception {
        String endpoint = limit > 0 ? "/api/news?limit=" + limit : "/api/news";
        HttpRequest request = buildGetRequest(endpoint);
        HttpResponse<String> response = httpClient.send(request,
                HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 200) {
            return objectMapper.readValue(response.body(),
                    new TypeReference<List<TalkNewsItemDTO>>() {});
        } else {
            throw new Exception("Failed to fetch news: " + response.statusCode());
        }
    }

    /**
     * Get urgent news items
     */
    public List<TalkNewsItemDTO> getUrgentNews() throws Exception {
        HttpRequest request = buildGetRequest("/api/news/urgent");
        HttpResponse<String> response = httpClient.send(request,
                HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 200) {
            return objectMapper.readValue(response.body(),
                    new TypeReference<List<TalkNewsItemDTO>>() {});
        } else {
            throw new Exception("Failed to fetch urgent news: " + response.statusCode());
        }
    }

    /**
     * Get pinned news items
     */
    public List<TalkNewsItemDTO> getPinnedNews() throws Exception {
        HttpRequest request = buildGetRequest("/api/news/pinned");
        HttpResponse<String> response = httpClient.send(request,
                HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 200) {
            return objectMapper.readValue(response.body(),
                    new TypeReference<List<TalkNewsItemDTO>>() {});
        } else {
            throw new Exception("Failed to fetch pinned news: " + response.statusCode());
        }
    }

    // ========================================================================
    // INVITATIONS
    // ========================================================================

    /**
     * Get pending invitations for the current user
     */
    public List<TalkChannelInvitationDTO> getPendingInvitations() throws Exception {
        HttpRequest request = buildGetRequest("/api/invitations/pending");
        HttpResponse<String> response = httpClient.send(request,
                HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 200) {
            return objectMapper.readValue(response.body(),
                    new TypeReference<List<TalkChannelInvitationDTO>>() {});
        } else {
            throw new Exception("Failed to fetch invitations: " + response.statusCode());
        }
    }

    /**
     * Get count of pending invitations
     */
    public long getPendingInvitationCount() throws Exception {
        HttpRequest request = buildGetRequest("/api/invitations/pending/count");
        HttpResponse<String> response = httpClient.send(request,
                HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 200) {
            var map = objectMapper.readValue(response.body(),
                    new TypeReference<java.util.Map<String, Long>>() {});
            return map.getOrDefault("count", 0L);
        } else {
            return 0;
        }
    }

    /**
     * Accept an invitation
     */
    public TalkChannelInvitationDTO acceptInvitation(Long invitationId) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(activeBaseUrl + "/api/invitations/" + invitationId + "/accept"))
                .header("X-Session-Token", sessionToken)
                .POST(HttpRequest.BodyPublishers.noBody())
                .timeout(Duration.ofSeconds(10))
                .build();

        HttpResponse<String> response = httpClient.send(request,
                HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 200) {
            return objectMapper.readValue(response.body(), TalkChannelInvitationDTO.class);
        } else {
            throw new Exception("Failed to accept invitation: " + response.statusCode());
        }
    }

    /**
     * Decline an invitation
     */
    public TalkChannelInvitationDTO declineInvitation(Long invitationId) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(activeBaseUrl + "/api/invitations/" + invitationId + "/decline"))
                .header("X-Session-Token", sessionToken)
                .POST(HttpRequest.BodyPublishers.noBody())
                .timeout(Duration.ofSeconds(10))
                .build();

        HttpResponse<String> response = httpClient.send(request,
                HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 200) {
            return objectMapper.readValue(response.body(), TalkChannelInvitationDTO.class);
        } else {
            throw new Exception("Failed to decline invitation: " + response.statusCode());
        }
    }

    /**
     * Invite a user to a channel
     */
    public TalkChannelInvitationDTO inviteUser(Long channelId, Long userId, String message) throws Exception {
        var payload = new java.util.HashMap<String, Object>();
        if (message != null && !message.isEmpty()) {
            payload.put("message", message);
        }

        String requestBody = objectMapper.writeValueAsString(payload);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(activeBaseUrl + "/api/invitations/channel/" + channelId + "/user/" + userId))
                .header("Content-Type", "application/json")
                .header("X-Session-Token", sessionToken)
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .timeout(Duration.ofSeconds(10))
                .build();

        HttpResponse<String> response = httpClient.send(request,
                HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 200) {
            return objectMapper.readValue(response.body(), TalkChannelInvitationDTO.class);
        } else {
            throw new Exception("Failed to send invitation: " + response.statusCode());
        }
    }

    // ========================================================================
    // CHANNEL MEMBER MANAGEMENT
    // ========================================================================

    /**
     * Get members of a channel
     */
    public List<TalkUserDTO> getChannelMembers(Long channelId) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(activeBaseUrl + "/api/channels/" + channelId + "/members"))
                .header("X-Session-Token", sessionToken)
                .GET()
                .timeout(Duration.ofSeconds(10))
                .build();

        HttpResponse<String> response = httpClient.send(request,
                HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 200) {
            return objectMapper.readValue(response.body(),
                    objectMapper.getTypeFactory().constructCollectionType(List.class, TalkUserDTO.class));
        } else {
            log.warn("Failed to get channel members: {}", response.statusCode());
            return new java.util.ArrayList<>();
        }
    }

    /**
     * Remove a user from a channel
     */
    public boolean removeChannelMember(Long channelId, Long userId) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(activeBaseUrl + "/api/channels/" + channelId + "/members/" + userId))
                .header("X-Session-Token", sessionToken)
                .DELETE()
                .timeout(Duration.ofSeconds(10))
                .build();

        HttpResponse<String> response = httpClient.send(request,
                HttpResponse.BodyHandlers.ofString());

        return response.statusCode() == 200 || response.statusCode() == 204;
    }

    // ========================================================================
    // FILE ATTACHMENTS
    // ========================================================================

    /**
     * Upload a file attachment to a channel (creates a message with the attachment)
     */
    public TalkMessageDTO uploadFile(Long channelId, java.io.File file, String caption) throws Exception {
        String boundary = "----HeronixBoundary" + System.currentTimeMillis();

        // Build multipart body
        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        java.io.PrintWriter writer = new java.io.PrintWriter(new java.io.OutputStreamWriter(baos, java.nio.charset.StandardCharsets.UTF_8), true);

        // File part
        writer.append("--").append(boundary).append("\r\n");
        writer.append("Content-Disposition: form-data; name=\"file\"; filename=\"")
                .append(file.getName()).append("\"\r\n");
        String contentType = java.nio.file.Files.probeContentType(file.toPath());
        if (contentType == null) contentType = "application/octet-stream";
        writer.append("Content-Type: ").append(contentType).append("\r\n\r\n");
        writer.flush();
        baos.write(java.nio.file.Files.readAllBytes(file.toPath()));
        baos.flush();
        writer.append("\r\n");

        // Caption part (if provided)
        if (caption != null && !caption.isEmpty()) {
            writer.append("--").append(boundary).append("\r\n");
            writer.append("Content-Disposition: form-data; name=\"caption\"\r\n\r\n");
            writer.append(caption).append("\r\n");
        }

        writer.append("--").append(boundary).append("--\r\n");
        writer.flush();

        byte[] body = baos.toByteArray();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(activeBaseUrl + "/api/attachments/upload/channel/" + channelId))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .header("X-Session-Token", sessionToken)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                .timeout(Duration.ofSeconds(60))
                .build();

        HttpResponse<String> response = httpClient.send(request,
                HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 200) {
            return objectMapper.readValue(response.body(), TalkMessageDTO.class);
        } else {
            log.error("Failed to upload file: {} - {}", response.statusCode(), response.body());
            throw new Exception("Failed to upload file: " + response.statusCode());
        }
    }

    /**
     * Download an attachment by UUID
     */
    public byte[] downloadAttachment(String uuid) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(activeBaseUrl + "/api/attachments/download/" + uuid))
                .header("X-Session-Token", sessionToken)
                .GET()
                .timeout(Duration.ofSeconds(60))
                .build();

        HttpResponse<byte[]> response = httpClient.send(request,
                HttpResponse.BodyHandlers.ofByteArray());

        if (response.statusCode() == 200) {
            return response.body();
        } else {
            throw new Exception("Failed to download attachment: " + response.statusCode());
        }
    }

    /**
     * Get the download URL for an attachment
     */
    public String getAttachmentDownloadUrl(String uuid) {
        return activeBaseUrl + "/api/attachments/download/" + uuid;
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
    // USER PREFERENCES
    // ========================================================================

    /**
     * Update user preferences/settings
     */
    public TalkUserDTO updateUserPreferences(Boolean notificationsEnabled, Boolean soundEnabled, String statusMessage) throws Exception {
        var payload = new java.util.HashMap<String, Object>();
        if (notificationsEnabled != null) payload.put("notificationsEnabled", notificationsEnabled);
        if (soundEnabled != null) payload.put("soundEnabled", soundEnabled);
        if (statusMessage != null) payload.put("statusMessage", statusMessage);

        String requestBody = objectMapper.writeValueAsString(payload);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(activeBaseUrl + "/api/users/" + currentUser.getId() + "/preferences"))
                .header("Content-Type", "application/json")
                .header("X-Session-Token", sessionToken)
                .PUT(HttpRequest.BodyPublishers.ofString(requestBody))
                .timeout(Duration.ofSeconds(10))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 200) {
            currentUser = objectMapper.readValue(response.body(), TalkUserDTO.class);
            return currentUser;
        } else {
            throw new Exception("Failed to update preferences: " + response.statusCode());
        }
    }

    /**
     * Toggle mute for a channel
     */
    public void toggleChannelMute(Long channelId) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(activeBaseUrl + "/api/channels/" + channelId + "/mute"))
                .header("X-Session-Token", sessionToken)
                .POST(HttpRequest.BodyPublishers.noBody())
                .timeout(Duration.ofSeconds(10))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new Exception("Failed to toggle mute: " + response.statusCode());
        }
    }

    /**
     * Toggle favorite for a channel
     */
    public void toggleChannelFavorite(Long channelId) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(activeBaseUrl + "/api/channels/" + channelId + "/favorite"))
                .header("X-Session-Token", sessionToken)
                .POST(HttpRequest.BodyPublishers.noBody())
                .timeout(Duration.ofSeconds(10))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new Exception("Failed to toggle favorite: " + response.statusCode());
        }
    }

    /**
     * Toggle pin for a channel
     */
    public void toggleChannelPin(Long channelId) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(activeBaseUrl + "/api/channels/" + channelId + "/pin"))
                .header("X-Session-Token", sessionToken)
                .POST(HttpRequest.BodyPublishers.noBody())
                .timeout(Duration.ofSeconds(10))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new Exception("Failed to toggle pin: " + response.statusCode());
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

    // ========================================================================
    // STUDENT MESSAGES
    // ========================================================================

    /**
     * Get users filtered by role (for finding students)
     * @param role Role to filter by (e.g., "Student")
     * @return List of users with the specified role
     */
    public List<TalkUserDTO> getUsersByRole(String role) throws Exception {
        HttpRequest request = buildGetRequest("/api/users?role=" +
                java.net.URLEncoder.encode(role, java.nio.charset.StandardCharsets.UTF_8));
        HttpResponse<String> response = httpClient.send(request,
                HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 200) {
            List<TalkUserDTO> allUsers = objectMapper.readValue(response.body(),
                    new TypeReference<List<TalkUserDTO>>() {});
            // Filter by role on client side if server doesn't support role filter
            return allUsers.stream()
                    .filter(u -> role.equalsIgnoreCase(u.getRole()))
                    .collect(java.util.stream.Collectors.toList());
        } else {
            throw new Exception("Failed to fetch users by role: " + response.statusCode());
        }
    }

    /**
     * Get all student users for easy messaging
     */
    public List<TalkUserDTO> getStudentUsers() throws Exception {
        return getUsersByRole("Student");
    }

    /**
     * Check if a user is a student based on their role
     */
    public boolean isStudentUser(TalkUserDTO user) {
        return user != null && "Student".equalsIgnoreCase(user.getRole());
    }

    // ========================================================================
    // ALERTS
    // ========================================================================

    /**
     * Acknowledge an alert
     */
    public boolean acknowledgeAlert(Long alertId) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(activeBaseUrl + "/api/alerts/" + alertId + "/acknowledge"))
                .header("X-Session-Token", sessionToken)
                .POST(HttpRequest.BodyPublishers.noBody())
                .timeout(Duration.ofSeconds(10))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 200) {
            log.info("Alert {} acknowledged successfully", alertId);
            return true;
        } else {
            log.error("Failed to acknowledge alert {}: {}", alertId, response.statusCode());
            return false;
        }
    }
}
