package com.heronix.teacher.service;

import com.heronix.teacher.model.dto.talk.*;
import javafx.application.Platform;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.function.Consumer;

/**
 * High-level Communication Service
 * Coordinates REST API and WebSocket for seamless messaging experience
 */
@Slf4j
@Service
public class CommunicationService {

    private final HeronixTalkApiClient apiClient;
    private final HeronixTalkWebSocketClient wsClient;
    private final ScheduledExecutorService heartbeatScheduler;

    @Getter
    private boolean initialized = false;

    @Getter
    private boolean connected = false;

    // UI callbacks (run on JavaFX thread)
    private Consumer<TalkMessageDTO> onMessageReceived;
    private Consumer<List<TalkChannelDTO>> onChannelsUpdated;
    private Consumer<List<TalkUserDTO>> onUsersUpdated;
    private Consumer<ChatWsMessage> onTypingIndicator;
    private Consumer<ChatWsMessage> onPresenceUpdate;
    private Consumer<Boolean> onConnectionStateChange;
    private Consumer<String> onError;

    // Cached data
    @Getter
    private List<TalkChannelDTO> channels = new ArrayList<>();

    @Getter
    private List<TalkUserDTO> users = new ArrayList<>();

    private ScheduledFuture<?> heartbeatTask;

    public CommunicationService(HeronixTalkApiClient apiClient, HeronixTalkWebSocketClient wsClient) {
        this.apiClient = apiClient;
        this.wsClient = wsClient;
        this.heartbeatScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "talk-heartbeat");
            t.setDaemon(true);
            return t;
        });

        setupWebSocketHandlers();
    }

    private void setupWebSocketHandlers() {
        wsClient.setOnMessageReceived(message -> {
            log.debug("Message received: {}", message.getContent());
            runOnFxThread(() -> {
                if (onMessageReceived != null) {
                    onMessageReceived.accept(message);
                }
            });
        });

        wsClient.setOnTypingIndicator(wsMessage -> {
            runOnFxThread(() -> {
                if (onTypingIndicator != null) {
                    onTypingIndicator.accept(wsMessage);
                }
            });
        });

        wsClient.setOnPresenceUpdate(wsMessage -> {
            runOnFxThread(() -> {
                if (onPresenceUpdate != null) {
                    onPresenceUpdate.accept(wsMessage);
                }
            });
        });

        wsClient.setOnConnectionStateChange(isConnected -> {
            this.connected = isConnected;
            runOnFxThread(() -> {
                if (onConnectionStateChange != null) {
                    onConnectionStateChange.accept(isConnected);
                }
            });
        });

        wsClient.setOnError(error -> {
            runOnFxThread(() -> {
                if (onError != null) {
                    onError.accept(error);
                }
            });
        });
    }

    // ========================================================================
    // INITIALIZATION
    // ========================================================================

    /**
     * Initialize communication service with teacher credentials
     * Called after successful teacher login
     */
    public CompletableFuture<Boolean> initialize(String employeeId, String password) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Check if server is reachable
                if (!apiClient.isServerReachable()) {
                    log.warn("Heronix-Talk server not reachable");
                    return false;
                }

                // Authenticate with Talk server
                boolean authenticated = apiClient.authenticate(employeeId, password);
                if (!authenticated) {
                    log.warn("Failed to authenticate with Heronix-Talk");
                    return false;
                }

                // Connect WebSocket
                String wsUrl = apiClient.getWebSocketUrl();
                Boolean wsConnected = wsClient.connect(wsUrl).get(10, TimeUnit.SECONDS);

                if (wsConnected) {
                    // Set status to online
                    apiClient.updateStatus("ONLINE", "Available");

                    // Start heartbeat
                    startHeartbeat();

                    // Load initial data
                    loadChannels();
                    loadUsers();

                    initialized = true;
                    connected = true;
                    log.info("Communication service initialized successfully");
                    return true;
                } else {
                    log.warn("WebSocket connection failed");
                    return false;
                }

            } catch (Exception e) {
                log.error("Error initializing communication service", e);
                return false;
            }
        });
    }

    /**
     * Shutdown communication service
     */
    public void shutdown() {
        try {
            // Set status to offline
            if (apiClient.isAuthenticated()) {
                try {
                    apiClient.updateStatus("OFFLINE", null);
                } catch (Exception e) {
                    log.debug("Could not update status on shutdown");
                }
            }

            // Stop heartbeat
            if (heartbeatTask != null) {
                heartbeatTask.cancel(true);
            }

            // Disconnect WebSocket
            wsClient.disconnect();

            // Logout from API
            apiClient.logout();

            initialized = false;
            connected = false;
            log.info("Communication service shutdown complete");

        } catch (Exception e) {
            log.error("Error during communication service shutdown", e);
        }
    }

    private void startHeartbeat() {
        heartbeatTask = heartbeatScheduler.scheduleAtFixedRate(() -> {
            if (initialized) {
                apiClient.sendHeartbeat();
            }
        }, 5, 5, TimeUnit.MINUTES);
    }

    // ========================================================================
    // DATA LOADING
    // ========================================================================

    /**
     * Load/refresh channels list (includes user's channels and public channels)
     */
    public CompletableFuture<List<TalkChannelDTO>> loadChannels() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Get user's channels (channels they're a member of)
                List<TalkChannelDTO> myChannels = apiClient.getMyChannels();

                // Also get public channels (visible to everyone)
                List<TalkChannelDTO> publicChannels = apiClient.getPublicChannels();

                // Merge lists, avoiding duplicates
                java.util.Set<Long> seenIds = new java.util.HashSet<>();
                List<TalkChannelDTO> allChannels = new ArrayList<>();

                for (TalkChannelDTO channel : myChannels) {
                    if (channel.getId() != null && seenIds.add(channel.getId())) {
                        allChannels.add(channel);
                    }
                }

                for (TalkChannelDTO channel : publicChannels) {
                    if (channel.getId() != null && seenIds.add(channel.getId())) {
                        allChannels.add(channel);
                    }
                }

                this.channels = allChannels;

                runOnFxThread(() -> {
                    if (onChannelsUpdated != null) {
                        onChannelsUpdated.accept(allChannels);
                    }
                });

                log.info("Loaded {} channels ({} personal, {} public)",
                        allChannels.size(), myChannels.size(), publicChannels.size());

                return allChannels;

            } catch (Exception e) {
                log.error("Error loading channels", e);
                return new ArrayList<>();
            }
        });
    }

    /**
     * Load/refresh users list
     */
    public CompletableFuture<List<TalkUserDTO>> loadUsers() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                List<TalkUserDTO> allUsers = apiClient.getUsers();
                this.users = allUsers;

                runOnFxThread(() -> {
                    if (onUsersUpdated != null) {
                        onUsersUpdated.accept(allUsers);
                    }
                });

                return allUsers;

            } catch (Exception e) {
                log.error("Error loading users", e);
                return new ArrayList<>();
            }
        });
    }

    /**
     * Load messages for a channel
     */
    public CompletableFuture<List<TalkMessageDTO>> loadMessages(Long channelId, int page, int size) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return apiClient.getMessages(channelId, page, size);
            } catch (Exception e) {
                log.error("Error loading messages for channel {}", channelId, e);
                return new ArrayList<>();
            }
        });
    }

    // ========================================================================
    // MESSAGING
    // ========================================================================

    /**
     * Send a message to a channel
     */
    public CompletableFuture<Boolean> sendMessage(Long channelId, String content) {
        if (!connected) {
            log.warn("Cannot send message: not connected");
            return CompletableFuture.completedFuture(false);
        }

        return wsClient.sendMessage(channelId, content);
    }

    /**
     * Send typing indicator
     */
    public void sendTypingStart(Long channelId) {
        if (connected) {
            wsClient.sendTypingStart(channelId);
        }
    }

    /**
     * Stop typing indicator
     */
    public void sendTypingStop(Long channelId) {
        if (connected) {
            wsClient.sendTypingStop(channelId);
        }
    }

    /**
     * Mark messages as read
     */
    public void markRead(Long channelId, Long messageId) {
        if (connected) {
            wsClient.markRead(channelId, messageId);
        }
    }

    // ========================================================================
    // CHANNELS
    // ========================================================================

    /**
     * Join a channel
     */
    public CompletableFuture<Void> joinChannel(Long channelId) {
        return CompletableFuture.runAsync(() -> {
            try {
                apiClient.joinChannel(channelId);
                if (connected) {
                    wsClient.joinChannel(channelId);
                }
                loadChannels(); // Refresh channel list
            } catch (Exception e) {
                log.error("Error joining channel {}", channelId, e);
            }
        });
    }

    /**
     * Leave a channel
     */
    public CompletableFuture<Void> leaveChannel(Long channelId) {
        return CompletableFuture.runAsync(() -> {
            try {
                apiClient.leaveChannel(channelId);
                loadChannels(); // Refresh channel list
            } catch (Exception e) {
                log.error("Error leaving channel {}", channelId, e);
            }
        });
    }

    /**
     * Start or get existing direct message with user
     */
    public CompletableFuture<TalkChannelDTO> startDirectMessage(Long userId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                TalkChannelDTO dm = apiClient.getOrCreateDirectMessage(userId);
                loadChannels(); // Refresh to include new DM
                return dm;
            } catch (Exception e) {
                log.error("Error starting DM with user {}", userId, e);
                return null;
            }
        });
    }

    // ========================================================================
    // PRESENCE
    // ========================================================================

    /**
     * Update user's status
     */
    public CompletableFuture<Void> updateStatus(String status, String statusMessage) {
        return CompletableFuture.runAsync(() -> {
            try {
                apiClient.updateStatus(status, statusMessage);
                if (connected) {
                    wsClient.updatePresence(status, statusMessage);
                }
            } catch (Exception e) {
                log.error("Error updating status", e);
            }
        });
    }

    // ========================================================================
    // SEARCH
    // ========================================================================

    /**
     * Search users by name
     */
    public CompletableFuture<List<TalkUserDTO>> searchUsers(String query) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return apiClient.searchUsers(query);
            } catch (Exception e) {
                log.error("Error searching users", e);
                return new ArrayList<>();
            }
        });
    }

    // ========================================================================
    // CALLBACKS
    // ========================================================================

    public void setOnMessageReceived(Consumer<TalkMessageDTO> handler) {
        this.onMessageReceived = handler;
    }

    public void setOnChannelsUpdated(Consumer<List<TalkChannelDTO>> handler) {
        this.onChannelsUpdated = handler;
    }

    public void setOnUsersUpdated(Consumer<List<TalkUserDTO>> handler) {
        this.onUsersUpdated = handler;
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

    // ========================================================================
    // HELPERS
    // ========================================================================

    private void runOnFxThread(Runnable action) {
        if (Platform.isFxApplicationThread()) {
            action.run();
        } else {
            Platform.runLater(action);
        }
    }

    public TalkUserDTO getCurrentUser() {
        return apiClient.getCurrentUser();
    }

    public boolean isServerAvailable() {
        return apiClient.isServerReachable();
    }
}
