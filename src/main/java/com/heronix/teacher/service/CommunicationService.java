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
    private final NotificationSoundService soundService;
    private final DesktopNotificationService desktopNotificationService;
    private final ScheduledExecutorService heartbeatScheduler;

    @Getter
    private boolean initialized = false;

    @Getter
    private boolean connected = false;

    // UI callbacks (run on JavaFX thread)
    private Consumer<TalkMessageDTO> onMessageReceived;
    private Consumer<List<TalkMessageDTO>> onHistoryReceived;
    private Consumer<List<TalkChannelDTO>> onChannelsUpdated;
    private Consumer<List<TalkUserDTO>> onUsersUpdated;
    private Consumer<ChatWsMessage> onTypingIndicator;
    private Consumer<ChatWsMessage> onPresenceUpdate;
    private Consumer<Boolean> onConnectionStateChange;
    private Consumer<String> onError;
    private Consumer<TalkNewsItemDTO> onNewsReceived;
    private Consumer<List<TalkNewsItemDTO>> onNewsUpdated;
    private Consumer<TalkAlertDTO> onAlertReceived;
    private Consumer<TalkChannelInvitationDTO> onInvitationReceived;

    // Cached data
    @Getter
    private List<TalkChannelDTO> channels = new ArrayList<>();

    @Getter
    private List<TalkUserDTO> users = new ArrayList<>();

    @Getter
    private List<TalkNewsItemDTO> newsItems = new ArrayList<>();

    private ScheduledFuture<?> heartbeatTask;

    public CommunicationService(HeronixTalkApiClient apiClient, HeronixTalkWebSocketClient wsClient,
                                NotificationSoundService soundService,
                                DesktopNotificationService desktopNotificationService) {
        this.apiClient = apiClient;
        this.wsClient = wsClient;
        this.soundService = soundService;
        this.desktopNotificationService = desktopNotificationService;
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

            // Play sound and show notification for incoming messages (except own messages)
            TalkUserDTO currentUser = apiClient.getCurrentUser();
            if (currentUser == null || !currentUser.getId().equals(message.getSenderId())) {
                soundService.playMessageReceived();

                // Show desktop notification
                String preview = message.getContent();
                if (preview != null && preview.length() > 50) {
                    preview = preview.substring(0, 47) + "...";
                }
                desktopNotificationService.showMessageNotification(
                        message.getSenderName(),
                        preview,
                        message.getChannelName()
                );
            }

            runOnFxThread(() -> {
                if (onMessageReceived != null) {
                    onMessageReceived.accept(message);
                }
            });
        });

        wsClient.setOnHistoryReceived(messages -> {
            log.info("History received: {} messages", messages.size());
            runOnFxThread(() -> {
                if (onHistoryReceived != null) {
                    onHistoryReceived.accept(messages);
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
            soundService.playError();
            runOnFxThread(() -> {
                if (onError != null) {
                    onError.accept(error);
                }
            });
        });

        // Handle news broadcasts from WebSocket
        wsClient.setOnNewsReceived(newsItem -> {
            log.info("News received via WebSocket: {}", newsItem.getHeadline());
            // Add to cached list
            newsItems.add(0, newsItem); // Add at beginning (newest first)

            // Play notification sound for urgent news
            if (newsItem.isUrgent()) {
                soundService.playNotification();
            }

            // Show desktop notification
            desktopNotificationService.showNewsNotification(
                    newsItem.getHeadline(),
                    newsItem.isUrgent()
            );

            runOnFxThread(() -> {
                if (onNewsReceived != null) {
                    onNewsReceived.accept(newsItem);
                }
            });
        });

        // Handle invitation notifications from WebSocket
        wsClient.setOnInvitationReceived(invitation -> {
            log.info("Invitation received via WebSocket: channel={}", invitation.getChannelName());

            // Play invite sound
            soundService.playInviteReceived();

            // Show desktop notification
            desktopNotificationService.showInvitationNotification(
                    invitation.getInviterName(),
                    invitation.getChannelName()
            );

            runOnFxThread(() -> {
                if (onInvitationReceived != null) {
                    onInvitationReceived.accept(invitation);
                }
            });
        });

        // Handle emergency alert broadcasts from WebSocket
        wsClient.setOnAlertReceived(alert -> {
            log.warn("ALERT received via WebSocket: [{}] {} - {}",
                    alert.getAlertLevel(), alert.getAlertType(), alert.getTitle());

            // Play alert sound based on level
            if (alert.isPlaySound()) {
                if (alert.isCritical()) {
                    soundService.playEmergency();
                } else {
                    soundService.playNotification();
                }
            }

            // Show desktop notification for alerts
            desktopNotificationService.showAlertNotification(
                    alert.getAlertLevel(),
                    alert.getTitle(),
                    alert.getMessage()
            );

            runOnFxThread(() -> {
                if (onAlertReceived != null) {
                    onAlertReceived.accept(alert);
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
                log.info("=== Initializing CommunicationService for {} ===", employeeId);

                // Check if server is reachable
                boolean serverReachable = apiClient.isServerReachable();
                log.info("Server reachable check: {}", serverReachable);
                if (!serverReachable) {
                    log.warn("Heronix-Talk server not reachable");
                    return false;
                }

                // Authenticate with Talk server
                log.info("Authenticating with Talk server...");
                boolean authenticated = apiClient.authenticate(employeeId, password);
                log.info("Authentication result: {}", authenticated);
                if (!authenticated) {
                    log.warn("Failed to authenticate with Heronix-Talk");
                    return false;
                }

                // Connect WebSocket
                String wsUrl = apiClient.getWebSocketUrl();
                log.info("Connecting WebSocket to: {}", wsUrl);
                Boolean wsConnected = wsClient.connect(wsUrl).get(10, TimeUnit.SECONDS);
                log.info("WebSocket connection result: {}", wsConnected);

                if (wsConnected) {
                    // Set status to online
                    log.info("Setting status to ONLINE...");
                    apiClient.updateStatus("ONLINE", "Available");

                    // Start heartbeat
                    startHeartbeat();

                    // Load initial data
                    log.info("Loading channels...");
                    loadChannels();
                    log.info("Loading users...");
                    loadUsers();

                    initialized = true;
                    connected = true;
                    log.info("=== Communication service initialized successfully ===");
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
                log.info("Loading channels... apiClient authenticated: {}", apiClient.isAuthenticated());

                // Get user's channels (channels they're a member of)
                List<TalkChannelDTO> myChannels = apiClient.getMyChannels();
                log.info("Got {} personal channels from API", myChannels.size());
                for (TalkChannelDTO ch : myChannels) {
                    log.debug("  - My channel: {} (id={})", ch.getName(), ch.getId());
                }

                // Also get public channels (visible to everyone)
                List<TalkChannelDTO> publicChannels = apiClient.getPublicChannels();
                log.info("Got {} public channels from API", publicChannels.size());
                for (TalkChannelDTO ch : publicChannels) {
                    log.debug("  - Public channel: {} (id={})", ch.getName(), ch.getId());
                }

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
                log.info("Total merged channels: {}", allChannels.size());

                runOnFxThread(() -> {
                    log.info("Running channel update on FX thread, callback set: {}", onChannelsUpdated != null);
                    if (onChannelsUpdated != null) {
                        onChannelsUpdated.accept(allChannels);
                        log.info("Channel callback invoked with {} channels", allChannels.size());
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
     * Send a message to a channel with a specific client ID for deduplication
     */
    public CompletableFuture<Boolean> sendMessageWithClientId(Long channelId, String content, String clientId) {
        if (!connected) {
            log.warn("Cannot send message: not connected");
            return CompletableFuture.completedFuture(false);
        }

        return wsClient.sendMessageWithClientId(channelId, content, clientId);
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

    /**
     * Update user presence/status
     */
    public void updatePresence(String status, String statusMessage) {
        if (connected) {
            wsClient.updatePresence(status, statusMessage);
            log.info("Presence updated: status={}, message={}", status, statusMessage);
        } else {
            log.warn("Cannot update presence: not connected");
        }
    }

    // ========================================================================
    // MESSAGE ACTIONS
    // ========================================================================

    /**
     * Toggle a reaction on a message
     */
    public CompletableFuture<java.util.Map<String, List<Long>>> toggleReaction(Long messageId, String emoji) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return apiClient.toggleReaction(messageId, emoji);
            } catch (Exception e) {
                log.error("Error toggling reaction on message {}", messageId, e);
                return null;
            }
        });
    }

    /**
     * Edit a message
     */
    public CompletableFuture<TalkMessageDTO> editMessage(Long messageId, String newContent) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return apiClient.editMessage(messageId, newContent);
            } catch (Exception e) {
                log.error("Error editing message {}", messageId, e);
                runOnFxThread(() -> {
                    if (onError != null) {
                        onError.accept("Failed to edit message: " + e.getMessage());
                    }
                });
                return null;
            }
        });
    }

    /**
     * Delete a message
     */
    public CompletableFuture<Boolean> deleteMessage(Long messageId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return apiClient.deleteMessage(messageId);
            } catch (Exception e) {
                log.error("Error deleting message {}", messageId, e);
                runOnFxThread(() -> {
                    if (onError != null) {
                        onError.accept("Failed to delete message: " + e.getMessage());
                    }
                });
                return false;
            }
        });
    }

    /**
     * Pin or unpin a message
     */
    public CompletableFuture<Void> pinMessage(Long messageId, boolean pinned) {
        return CompletableFuture.runAsync(() -> {
            try {
                apiClient.pinMessage(messageId, pinned);
            } catch (Exception e) {
                log.error("Error pinning message {}", messageId, e);
                runOnFxThread(() -> {
                    if (onError != null) {
                        onError.accept("Failed to " + (pinned ? "pin" : "unpin") + " message: " + e.getMessage());
                    }
                });
            }
        });
    }

    /**
     * Get pinned messages for a channel
     */
    public CompletableFuture<List<TalkMessageDTO>> getPinnedMessages(Long channelId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return apiClient.getPinnedMessages(channelId);
            } catch (Exception e) {
                log.error("Error fetching pinned messages for channel {}", channelId, e);
                return new ArrayList<>();
            }
        });
    }

    /**
     * Send a reply to a message
     */
    public CompletableFuture<TalkMessageDTO> sendReply(Long channelId, Long replyToId, String content) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String clientId = java.util.UUID.randomUUID().toString();
                return apiClient.sendReply(channelId, replyToId, content, clientId);
            } catch (Exception e) {
                log.error("Error sending reply", e);
                runOnFxThread(() -> {
                    if (onError != null) {
                        onError.accept("Failed to send reply: " + e.getMessage());
                    }
                });
                return null;
            }
        });
    }

    /**
     * Get replies to a message
     */
    public CompletableFuture<List<TalkMessageDTO>> getReplies(Long messageId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return apiClient.getReplies(messageId);
            } catch (Exception e) {
                log.error("Error fetching replies for message {}", messageId, e);
                return new ArrayList<>();
            }
        });
    }

    /**
     * Search messages
     */
    public CompletableFuture<List<TalkMessageDTO>> searchMessages(String query, Long channelId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return apiClient.searchMessages(query, channelId, 0, 50);
            } catch (Exception e) {
                log.error("Error searching messages", e);
                return new ArrayList<>();
            }
        });
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
     * Subscribe to a channel via WebSocket for real-time updates.
     * This is used when switching to a channel to ensure messages are received.
     * Unlike joinChannel(), this does NOT add the user as a member - it just subscribes for updates.
     */
    public void subscribeToChannel(Long channelId) {
        if (connected && wsClient != null) {
            wsClient.joinChannel(channelId);
            log.debug("Subscribed to channel {} for real-time updates", channelId);
        }
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

    /**
     * Create a new channel
     * @param name Channel name
     * @param description Channel description
     * @param channelType Channel type (PUBLIC, PRIVATE, GROUP, DEPARTMENT, ANNOUNCEMENT)
     * @param icon Channel icon (optional)
     * @param memberIds List of user IDs to add as members (optional)
     * @param sendInvites Whether to send invites to members
     * @return Created channel
     */
    public CompletableFuture<TalkChannelDTO> createChannel(String name, String description,
                                                           String channelType, String icon,
                                                           List<Long> memberIds, boolean sendInvites) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                TalkChannelDTO channel = apiClient.createChannel(name, description, channelType,
                        icon, memberIds, sendInvites);
                loadChannels(); // Refresh channel list to include new channel
                return channel;
            } catch (Exception e) {
                log.error("Error creating channel '{}'", name, e);
                runOnFxThread(() -> {
                    if (onError != null) {
                        onError.accept("Failed to create channel: " + e.getMessage());
                    }
                });
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
    // NEWS
    // ========================================================================

    /**
     * Load/refresh news items
     */
    public CompletableFuture<List<TalkNewsItemDTO>> loadNews() {
        return loadNews(20); // Default limit
    }

    /**
     * Load/refresh news items with limit
     */
    public CompletableFuture<List<TalkNewsItemDTO>> loadNews(int limit) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                List<TalkNewsItemDTO> news = apiClient.getNews(limit);
                this.newsItems = new ArrayList<>(news);

                runOnFxThread(() -> {
                    if (onNewsUpdated != null) {
                        onNewsUpdated.accept(news);
                    }
                });

                log.info("Loaded {} news items", news.size());
                return news;

            } catch (Exception e) {
                log.error("Error loading news", e);
                return new ArrayList<>();
            }
        });
    }

    /**
     * Load urgent news items
     */
    public CompletableFuture<List<TalkNewsItemDTO>> loadUrgentNews() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return apiClient.getUrgentNews();
            } catch (Exception e) {
                log.error("Error loading urgent news", e);
                return new ArrayList<>();
            }
        });
    }

    // ========================================================================
    // INVITATIONS
    // ========================================================================

    /**
     * Get pending invitations for the current user
     */
    public CompletableFuture<List<TalkChannelInvitationDTO>> getPendingInvitations() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return apiClient.getPendingInvitations();
            } catch (Exception e) {
                log.error("Error loading pending invitations", e);
                return new ArrayList<>();
            }
        });
    }

    /**
     * Get count of pending invitations
     */
    public CompletableFuture<Long> getPendingInvitationCount() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return apiClient.getPendingInvitationCount();
            } catch (Exception e) {
                log.error("Error getting invitation count", e);
                return 0L;
            }
        });
    }

    /**
     * Accept a channel invitation
     */
    public CompletableFuture<TalkChannelInvitationDTO> acceptInvitation(Long invitationId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                TalkChannelInvitationDTO result = apiClient.acceptInvitation(invitationId);
                loadChannels(); // Refresh channels to include the newly joined one
                return result;
            } catch (Exception e) {
                log.error("Error accepting invitation {}", invitationId, e);
                runOnFxThread(() -> {
                    if (onError != null) {
                        onError.accept("Failed to accept invitation: " + e.getMessage());
                    }
                });
                return null;
            }
        });
    }

    /**
     * Decline a channel invitation
     */
    public CompletableFuture<TalkChannelInvitationDTO> declineInvitation(Long invitationId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return apiClient.declineInvitation(invitationId);
            } catch (Exception e) {
                log.error("Error declining invitation {}", invitationId, e);
                runOnFxThread(() -> {
                    if (onError != null) {
                        onError.accept("Failed to decline invitation: " + e.getMessage());
                    }
                });
                return null;
            }
        });
    }

    /**
     * Invite a user to a channel
     */
    public CompletableFuture<TalkChannelInvitationDTO> inviteUser(Long channelId, Long userId, String message) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return apiClient.inviteUser(channelId, userId, message);
            } catch (Exception e) {
                log.error("Error inviting user {} to channel {}", userId, channelId, e);
                runOnFxThread(() -> {
                    if (onError != null) {
                        onError.accept("Failed to send invitation: " + e.getMessage());
                    }
                });
                return null;
            }
        });
    }

    // ========================================================================
    // CHANNEL MEMBER MANAGEMENT
    // ========================================================================

    /**
     * Get current user ID
     */
    public Long getCurrentUserId() {
        TalkUserDTO currentUser = apiClient.getCurrentUser();
        return currentUser != null ? currentUser.getId() : null;
    }

    /**
     * Get members of a channel
     */
    public CompletableFuture<List<TalkUserDTO>> getChannelMembers(Long channelId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                List<TalkUserDTO> members = apiClient.getChannelMembers(channelId);
                return members != null ? members : new ArrayList<TalkUserDTO>();
            } catch (Exception e) {
                log.error("Error getting channel members for channel {}", channelId, e);
                runOnFxThread(() -> {
                    if (onError != null) {
                        onError.accept("Failed to get channel members: " + e.getMessage());
                    }
                });
                return new ArrayList<TalkUserDTO>();
            }
        });
    }

    /**
     * Remove a user from a channel
     */
    public CompletableFuture<Boolean> removeChannelMember(Long channelId, Long userId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                boolean result = apiClient.removeChannelMember(channelId, userId);
                return Boolean.valueOf(result);
            } catch (Exception e) {
                log.error("Error removing user {} from channel {}", userId, channelId, e);
                runOnFxThread(() -> {
                    if (onError != null) {
                        onError.accept("Failed to remove user from channel: " + e.getMessage());
                    }
                });
                return Boolean.FALSE;
            }
        });
    }

    /**
     * Invite a user to a channel (convenience wrapper)
     */
    public CompletableFuture<Boolean> inviteUserToChannel(Long channelId, Long userId, String message) {
        return inviteUser(channelId, userId, message)
                .thenApply(invitation -> invitation != null);
    }

    // ========================================================================
    // FILE ATTACHMENTS
    // ========================================================================

    /**
     * Upload a file to a channel (creates a message with the attachment)
     */
    public CompletableFuture<TalkMessageDTO> uploadFile(Long channelId, java.io.File file, String caption) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                TalkMessageDTO msg = apiClient.uploadFile(channelId, file, caption);
                if (msg != null) {
                    log.info("Uploaded file {} to channel {}", file.getName(), channelId);
                }
                return msg;
            } catch (Exception e) {
                log.error("Error uploading file to channel {}", channelId, e);
                runOnFxThread(() -> {
                    if (onError != null) {
                        onError.accept("Failed to upload file: " + e.getMessage());
                    }
                });
                return null;
            }
        });
    }

    /**
     * Download an attachment by UUID
     */
    public CompletableFuture<byte[]> downloadAttachment(String uuid) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return apiClient.downloadAttachment(uuid);
            } catch (Exception e) {
                log.error("Error downloading attachment {}", uuid, e);
                return null;
            }
        });
    }

    /**
     * Get the download URL for an attachment
     */
    public String getAttachmentDownloadUrl(String uuid) {
        return apiClient.getAttachmentDownloadUrl(uuid);
    }

    // ========================================================================
    // ALERTS
    // ========================================================================

    /**
     * Acknowledge an alert on the server
     */
    public CompletableFuture<Boolean> acknowledgeAlert(Long alertId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return apiClient.acknowledgeAlert(alertId);
            } catch (Exception e) {
                log.error("Error acknowledging alert {}", alertId, e);
                return false;
            }
        });
    }

    // ========================================================================
    // CALLBACKS
    // ========================================================================

    public void setOnMessageReceived(Consumer<TalkMessageDTO> handler) {
        this.onMessageReceived = handler;
    }

    public void setOnHistoryReceived(Consumer<List<TalkMessageDTO>> handler) {
        this.onHistoryReceived = handler;
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

    public void setOnNewsReceived(Consumer<TalkNewsItemDTO> handler) {
        this.onNewsReceived = handler;
    }

    public void setOnNewsUpdated(Consumer<List<TalkNewsItemDTO>> handler) {
        this.onNewsUpdated = handler;
    }

    public void setOnAlertReceived(Consumer<TalkAlertDTO> handler) {
        this.onAlertReceived = handler;
    }

    public void setOnInvitationReceived(Consumer<TalkChannelInvitationDTO> handler) {
        this.onInvitationReceived = handler;
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

    // ========================================================================
    // SOUND SETTINGS
    // ========================================================================

    /**
     * Check if notification sounds are enabled
     */
    public boolean isSoundEnabled() {
        return soundService.isSoundEnabled();
    }

    /**
     * Enable or disable notification sounds
     */
    public void setSoundEnabled(boolean enabled) {
        soundService.setSoundEnabled(enabled);
    }

    /**
     * Toggle notification sounds on/off
     * @return new sound enabled state
     */
    public boolean toggleSound() {
        return soundService.toggleSound();
    }

    /**
     * Get current volume level
     */
    public double getSoundVolume() {
        return soundService.getVolume();
    }

    /**
     * Set sound volume
     * @param volume 0.0 to 1.0
     */
    public void setSoundVolume(double volume) {
        soundService.setVolume(volume);
    }

    /**
     * Get the sound service for direct access
     */
    public NotificationSoundService getSoundService() {
        return soundService;
    }
}
