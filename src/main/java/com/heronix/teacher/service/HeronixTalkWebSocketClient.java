package com.heronix.teacher.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.heronix.teacher.model.dto.talk.ChatWsMessage;
import com.heronix.teacher.model.dto.talk.TalkChannelInvitationDTO;
import com.heronix.teacher.model.dto.talk.TalkAlertDTO;
import com.heronix.teacher.model.dto.talk.TalkMessageDTO;
import com.heronix.teacher.model.dto.talk.TalkNewsItemDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * WebSocket client for Heronix-Talk real-time messaging
 * Handles connection, reconnection, and message routing
 */
@Slf4j
@Service
public class HeronixTalkWebSocketClient implements WebSocket.Listener {

    private final ObjectMapper objectMapper;
    private final ScheduledExecutorService scheduler;

    private WebSocket webSocket;
    private String wsUrl;
    private volatile boolean connected = false;
    private volatile boolean shouldReconnect = true;
    private volatile boolean connecting = false;
    private int reconnectAttempts = 0;
    private static final int MAX_RECONNECT_ATTEMPTS = 10;
    private static final int BASE_RECONNECT_DELAY_MS = 1000;
    private static final int MAX_RECONNECT_DELAY_MS = 60000;
    private static final int HEARTBEAT_INTERVAL_SECONDS = 30;
    private static final int CONNECTION_TIMEOUT_SECONDS = 15;

    // Pending message queue for when connection is temporarily lost
    private final BlockingQueue<ChatWsMessage> pendingMessages = new LinkedBlockingQueue<>(100);
    private ScheduledFuture<?> heartbeatTask;
    private volatile long lastPongReceived = System.currentTimeMillis();

    // Message handlers
    private volatile Consumer<TalkMessageDTO> onMessageReceived;
    private volatile Consumer<java.util.List<TalkMessageDTO>> onHistoryReceived;
    private volatile Consumer<ChatWsMessage> onTypingIndicator;
    private volatile Consumer<ChatWsMessage> onPresenceUpdate;
    private volatile Consumer<Boolean> onConnectionStateChange;
    private volatile Consumer<String> onError;
    private volatile Consumer<TalkNewsItemDTO> onNewsReceived;
    private volatile Consumer<TalkAlertDTO> onAlertReceived;
    private volatile Consumer<TalkChannelInvitationDTO> onInvitationReceived;

    // Message buffer for building multi-part messages
    private final StringBuilder messageBuffer = new StringBuilder();

    public HeronixTalkWebSocketClient(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "talk-ws-scheduler");
            t.setDaemon(true);
            return t;
        });
    }

    // ========================================================================
    // CONNECTION MANAGEMENT
    // ========================================================================

    /**
     * Connect to WebSocket server
     */
    public CompletableFuture<Boolean> connect(String wsUrl) {
        this.wsUrl = wsUrl;
        this.shouldReconnect = true;
        this.reconnectAttempts = 0;

        return doConnect();
    }

    private CompletableFuture<Boolean> doConnect() {
        CompletableFuture<Boolean> future = new CompletableFuture<>();

        if (connecting) {
            log.debug("Already connecting, returning pending future");
            future.complete(false);
            return future;
        }

        connecting = true;

        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(CONNECTION_TIMEOUT_SECONDS))
                    .build();

            client.newWebSocketBuilder()
                    .connectTimeout(Duration.ofSeconds(CONNECTION_TIMEOUT_SECONDS))
                    .buildAsync(URI.create(wsUrl), this)
                    .thenAccept(ws -> {
                        this.webSocket = ws;
                        this.connected = true;
                        this.connecting = false;
                        this.reconnectAttempts = 0;
                        log.info("WebSocket connected to Heronix-Talk");
                        notifyConnectionState(true);
                        startHeartbeat();
                        flushPendingMessages();
                        future.complete(true);
                    })
                    .exceptionally(e -> {
                        log.error("WebSocket connection failed: {}", e.getMessage());
                        this.connected = false;
                        this.connecting = false;
                        notifyConnectionState(false);
                        future.complete(false);
                        scheduleReconnect();
                        return null;
                    });

        } catch (Exception e) {
            log.error("Error creating WebSocket connection", e);
            connecting = false;
            future.complete(false);
        }

        return future;
    }

    /**
     * Disconnect from WebSocket server
     */
    public void disconnect() {
        shouldReconnect = false;
        stopHeartbeat();
        if (webSocket != null) {
            try {
                webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "Client disconnect");
            } catch (Exception e) {
                log.debug("Error during WebSocket close: {}", e.getMessage());
            }
        }
        connected = false;
        connecting = false;
        notifyConnectionState(false);
    }

    private void scheduleReconnect() {
        if (!shouldReconnect || reconnectAttempts >= MAX_RECONNECT_ATTEMPTS) {
            log.warn("Max reconnect attempts reached ({}) or reconnect disabled", reconnectAttempts);
            return;
        }

        if (connecting) {
            log.debug("Already connecting, skipping reconnect schedule");
            return;
        }

        reconnectAttempts++;
        // Exponential backoff with jitter
        int delayMs = Math.min(BASE_RECONNECT_DELAY_MS * (1 << reconnectAttempts), MAX_RECONNECT_DELAY_MS);
        delayMs += (int) (Math.random() * 1000); // Add jitter

        log.info("Scheduling reconnect attempt {} in {}ms", reconnectAttempts, delayMs);

        scheduler.schedule(() -> {
            if (shouldReconnect && !connected && !connecting) {
                doConnect();
            }
        }, delayMs, TimeUnit.MILLISECONDS);
    }

    /**
     * Start heartbeat to keep connection alive and detect stale connections
     */
    private void startHeartbeat() {
        stopHeartbeat();
        lastPongReceived = System.currentTimeMillis();

        heartbeatTask = scheduler.scheduleAtFixedRate(() -> {
            if (connected && webSocket != null) {
                // Check if we've received a pong recently
                long timeSinceLastPong = System.currentTimeMillis() - lastPongReceived;
                if (timeSinceLastPong > HEARTBEAT_INTERVAL_SECONDS * 2 * 1000L) {
                    log.warn("No pong received in {}ms, connection may be stale", timeSinceLastPong);
                    handleStaleConnection();
                    return;
                }

                // Send ping
                try {
                    webSocket.sendPing(ByteBuffer.wrap("ping".getBytes()));
                } catch (Exception e) {
                    log.debug("Error sending ping: {}", e.getMessage());
                }
            }
        }, HEARTBEAT_INTERVAL_SECONDS, HEARTBEAT_INTERVAL_SECONDS, TimeUnit.SECONDS);
    }

    private void stopHeartbeat() {
        if (heartbeatTask != null && !heartbeatTask.isCancelled()) {
            heartbeatTask.cancel(false);
            heartbeatTask = null;
        }
    }

    private void handleStaleConnection() {
        log.warn("Handling stale connection - forcing reconnect");
        connected = false;
        notifyConnectionState(false);

        if (webSocket != null) {
            try {
                webSocket.abort();
            } catch (Exception e) {
                log.debug("Error aborting stale connection: {}", e.getMessage());
            }
        }

        if (shouldReconnect) {
            scheduleReconnect();
        }
    }

    /**
     * Flush pending messages after reconnection
     */
    private void flushPendingMessages() {
        if (pendingMessages.isEmpty()) return;

        log.info("Flushing {} pending messages", pendingMessages.size());
        scheduler.execute(() -> {
            ChatWsMessage msg;
            while ((msg = pendingMessages.poll()) != null && connected) {
                send(msg);
            }
        });
    }

    // ========================================================================
    // WEBSOCKET LISTENER IMPLEMENTATION
    // ========================================================================

    @Override
    public void onOpen(WebSocket webSocket) {
        log.debug("WebSocket onOpen");
        webSocket.request(1);
    }

    @Override
    public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
        messageBuffer.append(data);

        if (last) {
            String fullMessage = messageBuffer.toString();
            messageBuffer.setLength(0);
            processMessage(fullMessage);
        }

        webSocket.request(1);
        return null;
    }

    @Override
    public CompletionStage<?> onPing(WebSocket webSocket, ByteBuffer message) {
        webSocket.sendPong(message);
        webSocket.request(1);
        return null;
    }

    @Override
    public CompletionStage<?> onPong(WebSocket webSocket, ByteBuffer message) {
        lastPongReceived = System.currentTimeMillis();
        webSocket.request(1);
        return null;
    }

    @Override
    public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
        log.info("WebSocket closed: {} - {}", statusCode, reason);
        this.connected = false;
        notifyConnectionState(false);

        if (shouldReconnect && statusCode != WebSocket.NORMAL_CLOSURE) {
            scheduleReconnect();
        }
        return null;
    }

    @Override
    public void onError(WebSocket webSocket, Throwable error) {
        log.error("WebSocket error: {}", error.getMessage());
        this.connected = false;
        notifyConnectionState(false);
        notifyError(error.getMessage());

        if (shouldReconnect) {
            scheduleReconnect();
        }
    }

    // ========================================================================
    // MESSAGE PROCESSING
    // ========================================================================

    private void processMessage(String jsonMessage) {
        try {
            ChatWsMessage wsMessage = objectMapper.readValue(jsonMessage, ChatWsMessage.class);

            log.debug("Received WS message: type={}, action={}", wsMessage.getType(), wsMessage.getAction());

            switch (wsMessage.getType()) {
                case ChatWsMessage.TYPE_MESSAGE -> handleMessageEvent(wsMessage);
                case ChatWsMessage.TYPE_TYPING -> handleTypingEvent(wsMessage);
                case ChatWsMessage.TYPE_PRESENCE -> handlePresenceEvent(wsMessage);
                case ChatWsMessage.TYPE_CHANNEL -> handleChannelEvent(wsMessage);
                case ChatWsMessage.TYPE_NEWS -> handleNewsEvent(wsMessage);
                case ChatWsMessage.TYPE_ALERT -> handleAlertEvent(wsMessage);
                case ChatWsMessage.TYPE_NOTIFICATION -> handleNotificationEvent(wsMessage);
                case ChatWsMessage.TYPE_ERROR -> handleErrorEvent(wsMessage);
                default -> log.debug("Unhandled message type: {}", wsMessage.getType());
            }

        } catch (Exception e) {
            log.error("Error processing WebSocket message: {}", e.getMessage());
        }
    }

    private void handleMessageEvent(ChatWsMessage wsMessage) {
        log.info("Received MESSAGE event: action={}, channelId={}", wsMessage.getAction(), wsMessage.getChannelId());
        if (wsMessage.getPayload() == null) {
            log.warn("Message event received but no payload");
            return;
        }

        String action = wsMessage.getAction();

        // Handle HISTORY action - payload is a List of messages
        if (ChatWsMessage.ACTION_HISTORY.equals(action)) {
            if (onHistoryReceived != null) {
                try {
                    String payloadJson = objectMapper.writeValueAsString(wsMessage.getPayload());
                    java.util.List<TalkMessageDTO> messages = objectMapper.readValue(payloadJson,
                            objectMapper.getTypeFactory().constructCollectionType(java.util.List.class, TalkMessageDTO.class));
                    log.info("Parsed {} history messages for channel {}", messages.size(), wsMessage.getChannelId());
                    onHistoryReceived.accept(messages);
                } catch (Exception e) {
                    log.error("Error parsing history payload: {}", e.getMessage(), e);
                }
            } else {
                log.debug("History received but no handler registered");
            }
            return;
        }

        // Handle CREATE/UPDATE/DELETE actions - payload is a single message
        if (onMessageReceived != null) {
            try {
                // Convert payload to TalkMessageDTO
                String payloadJson = objectMapper.writeValueAsString(wsMessage.getPayload());
                log.debug("Message payload JSON: {}", payloadJson);
                TalkMessageDTO message = objectMapper.readValue(payloadJson, TalkMessageDTO.class);
                log.info("Parsed message: id={}, channelId={}, senderId={}, content={}",
                        message.getId(), message.getChannelId(), message.getSenderId(),
                        message.getContent() != null ? message.getContent().substring(0, Math.min(50, message.getContent().length())) : "null");
                onMessageReceived.accept(message);
            } catch (Exception e) {
                log.error("Error parsing message payload: {}", e.getMessage(), e);
            }
        } else {
            log.warn("Message event received but no handler registered");
        }
    }

    private void handleTypingEvent(ChatWsMessage wsMessage) {
        if (onTypingIndicator != null) {
            onTypingIndicator.accept(wsMessage);
        }
    }

    private void handlePresenceEvent(ChatWsMessage wsMessage) {
        if (onPresenceUpdate != null) {
            onPresenceUpdate.accept(wsMessage);
        }
    }

    private void handleChannelEvent(ChatWsMessage wsMessage) {
        // Handle channel join/leave/update events
        log.debug("Channel event: {}", wsMessage.getAction());
    }

    private void handleNewsEvent(ChatWsMessage wsMessage) {
        if (onNewsReceived != null && wsMessage.getPayload() != null) {
            try {
                // Convert payload to TalkNewsItemDTO
                String payloadJson = objectMapper.writeValueAsString(wsMessage.getPayload());
                TalkNewsItemDTO newsItem = objectMapper.readValue(payloadJson, TalkNewsItemDTO.class);
                log.info("News received: {}", newsItem.getHeadline());
                onNewsReceived.accept(newsItem);
            } catch (Exception e) {
                log.error("Error parsing news payload: {}", e.getMessage());
            }
        }
    }

    private void handleAlertEvent(ChatWsMessage wsMessage) {
        if (onAlertReceived != null && wsMessage.getPayload() != null) {
            try {
                // Convert payload to TalkAlertDTO
                String payloadJson = objectMapper.writeValueAsString(wsMessage.getPayload());
                TalkAlertDTO alert = objectMapper.readValue(payloadJson, TalkAlertDTO.class);
                log.warn("ALERT received: [{}] {} - {}", alert.getAlertLevel(), alert.getAlertType(), alert.getTitle());
                onAlertReceived.accept(alert);
            } catch (Exception e) {
                log.error("Error parsing alert payload: {}", e.getMessage());
            }
        } else {
            log.warn("Alert received but no handler registered or no payload");
        }
    }

    private void handleNotificationEvent(ChatWsMessage wsMessage) {
        String action = wsMessage.getAction();
        if (action == null) return;

        // Handle invitation-related notifications
        if (action.startsWith("INVITE_") && onInvitationReceived != null && wsMessage.getPayload() != null) {
            try {
                String payloadJson = objectMapper.writeValueAsString(wsMessage.getPayload());
                TalkChannelInvitationDTO invitation = objectMapper.readValue(payloadJson, TalkChannelInvitationDTO.class);
                log.info("Invitation notification received: action={}, channel={}", action, invitation.getChannelName());
                onInvitationReceived.accept(invitation);
            } catch (Exception e) {
                log.error("Error parsing invitation payload: {}", e.getMessage());
            }
        } else {
            log.debug("Notification received: action={}", action);
        }
    }

    private void handleErrorEvent(ChatWsMessage wsMessage) {
        log.error("Server error received: type={}, action={}, errorMessage={}",
                wsMessage.getType(), wsMessage.getAction(), wsMessage.getErrorMessage());
        notifyError(wsMessage.getErrorMessage());
    }

    // ========================================================================
    // SEND METHODS
    // ========================================================================

    /**
     * Send a chat message
     */
    public CompletableFuture<Boolean> sendMessage(Long channelId, String content) {
        String clientId = UUID.randomUUID().toString();
        log.info("Sending message to channel {}: clientId={}, contentLength={}",
                channelId, clientId, content != null ? content.length() : 0);
        ChatWsMessage message = ChatWsMessage.sendMessage(channelId, content, clientId);
        return send(message);
    }

    /**
     * Send a chat message with a specific client ID for deduplication
     */
    public CompletableFuture<Boolean> sendMessageWithClientId(Long channelId, String content, String clientId) {
        ChatWsMessage message = ChatWsMessage.sendMessage(channelId, content, clientId);
        return send(message);
    }

    /**
     * Send typing indicator start
     */
    public CompletableFuture<Boolean> sendTypingStart(Long channelId) {
        ChatWsMessage message = ChatWsMessage.typingStart(channelId);
        return send(message);
    }

    /**
     * Send typing indicator stop
     */
    public CompletableFuture<Boolean> sendTypingStop(Long channelId) {
        ChatWsMessage message = ChatWsMessage.typingStop(channelId);
        return send(message);
    }

    /**
     * Join a channel (subscribes to updates)
     */
    public CompletableFuture<Boolean> joinChannel(Long channelId) {
        ChatWsMessage message = ChatWsMessage.joinChannel(channelId);
        return send(message);
    }

    /**
     * Mark messages as read
     */
    public CompletableFuture<Boolean> markRead(Long channelId, Long messageId) {
        ChatWsMessage message = ChatWsMessage.markRead(channelId, messageId);
        return send(message);
    }

    /**
     * Update presence status
     */
    public CompletableFuture<Boolean> updatePresence(String status, String statusMessage) {
        ChatWsMessage message = ChatWsMessage.updatePresence(status, statusMessage);
        return send(message);
    }

    /**
     * Send a raw ChatWsMessage
     */
    public CompletableFuture<Boolean> send(ChatWsMessage message) {
        CompletableFuture<Boolean> future = new CompletableFuture<>();

        if (!connected || webSocket == null) {
            // Queue message for later delivery if it's a chat message
            if (ChatWsMessage.TYPE_MESSAGE.equals(message.getType())) {
                if (pendingMessages.offer(message)) {
                    log.debug("Message queued for later delivery (queue size: {})", pendingMessages.size());
                } else {
                    log.warn("Pending message queue full, message dropped");
                }
            }
            log.warn("Cannot send message: WebSocket not connected (connected={}, webSocket={})",
                    connected, webSocket != null ? "present" : "null");
            future.complete(false);
            return future;
        }

        try {
            String json = objectMapper.writeValueAsString(message);
            log.debug("Sending WebSocket message: type={}, action={}, json length={}",
                    message.getType(), message.getAction(), json.length());
            webSocket.sendText(json, true)
                    .thenAccept(ws -> {
                        log.debug("WebSocket message sent successfully: type={}", message.getType());
                        future.complete(true);
                    })
                    .exceptionally(e -> {
                        log.error("Error sending WebSocket message: {}", e.getMessage());
                        // Queue for retry if it's a chat message
                        if (ChatWsMessage.TYPE_MESSAGE.equals(message.getType())) {
                            pendingMessages.offer(message);
                        }
                        future.complete(false);
                        return null;
                    });

        } catch (Exception e) {
            log.error("Error serializing message", e);
            future.complete(false);
        }

        return future;
    }

    // ========================================================================
    // EVENT HANDLERS
    // ========================================================================

    public void setOnMessageReceived(Consumer<TalkMessageDTO> handler) {
        this.onMessageReceived = handler;
    }

    public void setOnHistoryReceived(Consumer<java.util.List<TalkMessageDTO>> handler) {
        this.onHistoryReceived = handler;
    }

    public void setOnTypingIndicator(Consumer<ChatWsMessage> handler) {
        this.onTypingIndicator = handler;
    }

    public void setOnPresenceUpdate(Consumer<ChatWsMessage> handler) {
        this.onPresenceUpdate = handler;
    }

    public void setOnConnectionStateChange(Consumer<Boolean> handler) {
        this.onConnectionStateChange = handler;
    }

    public void setOnError(Consumer<String> handler) {
        this.onError = handler;
    }

    public void setOnNewsReceived(Consumer<TalkNewsItemDTO> handler) {
        this.onNewsReceived = handler;
    }

    public void setOnAlertReceived(Consumer<TalkAlertDTO> handler) {
        this.onAlertReceived = handler;
    }

    public void setOnInvitationReceived(Consumer<TalkChannelInvitationDTO> handler) {
        this.onInvitationReceived = handler;
    }

    private void notifyConnectionState(boolean connected) {
        if (onConnectionStateChange != null) {
            onConnectionStateChange.accept(connected);
        }
    }

    private void notifyError(String error) {
        if (onError != null) {
            onError.accept(error);
        }
    }

    // ========================================================================
    // STATUS
    // ========================================================================

    public boolean isConnected() {
        return connected;
    }

    public void shutdown() {
        log.info("Shutting down WebSocket client...");
        shouldReconnect = false;
        stopHeartbeat();
        disconnect();
        pendingMessages.clear();
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
        log.info("WebSocket client shutdown complete");
    }

    /**
     * Get connection statistics for monitoring
     */
    public ConnectionStats getConnectionStats() {
        return new ConnectionStats(connected, reconnectAttempts, pendingMessages.size(), lastPongReceived);
    }

    public record ConnectionStats(boolean connected, int reconnectAttempts, int pendingMessageCount, long lastPongTime) {}
}
