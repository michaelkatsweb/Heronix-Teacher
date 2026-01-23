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
import java.util.concurrent.*;
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
    private boolean connected = false;
    private boolean shouldReconnect = true;
    private int reconnectAttempts = 0;
    private static final int MAX_RECONNECT_ATTEMPTS = 5;
    private static final int RECONNECT_DELAY_SECONDS = 5;

    // Message handlers
    private Consumer<TalkMessageDTO> onMessageReceived;
    private Consumer<java.util.List<TalkMessageDTO>> onHistoryReceived;
    private Consumer<ChatWsMessage> onTypingIndicator;
    private Consumer<ChatWsMessage> onPresenceUpdate;
    private Consumer<Boolean> onConnectionStateChange;
    private Consumer<String> onError;
    private Consumer<TalkNewsItemDTO> onNewsReceived;
    private Consumer<TalkAlertDTO> onAlertReceived;
    private Consumer<TalkChannelInvitationDTO> onInvitationReceived;

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

        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .build();

            client.newWebSocketBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .buildAsync(URI.create(wsUrl), this)
                    .thenAccept(ws -> {
                        this.webSocket = ws;
                        this.connected = true;
                        this.reconnectAttempts = 0;
                        log.info("WebSocket connected to Heronix-Talk");
                        notifyConnectionState(true);
                        future.complete(true);
                    })
                    .exceptionally(e -> {
                        log.error("WebSocket connection failed: {}", e.getMessage());
                        this.connected = false;
                        notifyConnectionState(false);
                        future.complete(false);
                        scheduleReconnect();
                        return null;
                    });

        } catch (Exception e) {
            log.error("Error creating WebSocket connection", e);
            future.complete(false);
        }

        return future;
    }

    /**
     * Disconnect from WebSocket server
     */
    public void disconnect() {
        shouldReconnect = false;
        if (webSocket != null) {
            try {
                webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "Client disconnect");
            } catch (Exception e) {
                log.debug("Error during WebSocket close: {}", e.getMessage());
            }
        }
        connected = false;
        notifyConnectionState(false);
    }

    private void scheduleReconnect() {
        if (!shouldReconnect || reconnectAttempts >= MAX_RECONNECT_ATTEMPTS) {
            log.warn("Max reconnect attempts reached or reconnect disabled");
            return;
        }

        reconnectAttempts++;
        int delay = RECONNECT_DELAY_SECONDS * reconnectAttempts;
        log.info("Scheduling reconnect attempt {} in {} seconds", reconnectAttempts, delay);

        scheduler.schedule(() -> {
            if (shouldReconnect && !connected) {
                doConnect();
            }
        }, delay, TimeUnit.SECONDS);
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
        shouldReconnect = false;
        disconnect();
        scheduler.shutdown();
    }
}
