package com.heronix.teacher.ui.controller;

import com.heronix.teacher.model.dto.talk.*;
import com.heronix.teacher.service.CommunicationService;
import com.heronix.teacher.service.SessionManager;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

/**
 * Communication Hub Controller
 *
 * Integrated with Heronix-Talk messaging server for real-time communication:
 * - News ticker with scrolling headlines
 * - Channel management (public, private, DMs)
 * - User list with online status
 * - Real-time chat with WebSocket
 *
 * @author Heronix Team
 * @version 2.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CommunicationHubController {

    private final SessionManager sessionManager;
    private final CommunicationService communicationService;

    // News Ticker Components
    @FXML private ScrollPane newsScrollPane;
    @FXML private VBox newsTickerContainer;
    @FXML private Button pauseNewsBtn;

    // Channels and Users
    @FXML private ListView<ChannelItem> channelsList;
    @FXML private ListView<UserItem> usersList;
    @FXML private TextField userSearchField;
    @FXML private Label onlineCountLabel;

    // Chat Components
    @FXML private ScrollPane chatScrollPane;
    @FXML private VBox chatMessagesContainer;
    @FXML private TextArea messageInputArea;
    @FXML private Button sendMessageBtn;
    @FXML private Label chatTitleLabel;
    @FXML private Label chatMemberCountLabel;

    // Connection Status (optional - may not exist in FXML)
    @FXML private Label connectionStatusLabel;

    // Data
    private ObservableList<ChannelItem> channels;
    private ObservableList<UserItem> users;
    private ChannelItem selectedChannel;
    private boolean newsTickerPaused = false;
    private Timer typingTimer;
    private boolean isTyping = false;

    /**
     * Initialize the controller
     */
    @FXML
    public void initialize() {
        log.info("Initializing Communication Hub...");

        // Initialize data structures
        channels = FXCollections.observableArrayList();
        users = FXCollections.observableArrayList();

        // Setup UI components
        setupChannelsList();
        setupUsersList();
        setupChatArea();
        setupConnectionStatus();

        // Setup communication service callbacks
        setupCommunicationCallbacks();

        // Initialize connection to Heronix-Talk
        initializeTalkConnection();

        log.info("Communication Hub initialized");
    }

    // ==================== Setup Methods ====================

    private void setupCommunicationCallbacks() {
        communicationService.setOnMessageReceived(this::handleIncomingMessage);
        communicationService.setOnChannelsUpdated(this::updateChannelsList);
        communicationService.setOnUsersUpdated(this::updateUsersList);
        communicationService.setOnTypingIndicator(this::handleTypingIndicator);
        communicationService.setOnPresenceUpdate(this::handlePresenceUpdate);
        communicationService.setOnConnectionStateChange(this::updateConnectionStatus);
        communicationService.setOnError(this::handleError);
    }

    private void initializeTalkConnection() {
        if (sessionManager.getCurrentTeacher() == null) {
            log.warn("No teacher logged in, cannot initialize Talk connection");
            updateConnectionStatus(false);
            loadSampleData(); // Fall back to offline mode
            return;
        }

        String storedPassword = sessionManager.getStoredPassword();
        if (storedPassword == null) {
            log.warn("No stored password for Talk authentication");
            updateConnectionStatus(false);
            loadSampleData();
            return;
        }

        // Show connecting status
        if (connectionStatusLabel != null) {
            connectionStatusLabel.setText("Connecting...");
            connectionStatusLabel.setStyle("-fx-text-fill: orange;");
        }

        String employeeId = sessionManager.getCurrentTeacher().getEmployeeId();

        communicationService.initialize(employeeId, storedPassword)
                .thenAccept(success -> Platform.runLater(() -> {
                    if (success) {
                        log.info("Connected to Heronix-Talk server");
                        updateConnectionStatus(true);
                    } else {
                        log.warn("Could not connect to Heronix-Talk, using offline mode");
                        updateConnectionStatus(false);
                        loadSampleData();
                    }
                }));
    }

    private void setupConnectionStatus() {
        if (connectionStatusLabel != null) {
            connectionStatusLabel.setText("Offline");
            connectionStatusLabel.setStyle("-fx-text-fill: gray;");
        }
    }

    private void updateConnectionStatus(boolean connected) {
        Platform.runLater(() -> {
            if (connectionStatusLabel != null) {
                if (connected) {
                    connectionStatusLabel.setText("Connected");
                    connectionStatusLabel.setStyle("-fx-text-fill: #4CAF50;"); // Green
                } else {
                    connectionStatusLabel.setText("Offline");
                    connectionStatusLabel.setStyle("-fx-text-fill: gray;");
                }
            }
            if (sendMessageBtn != null) {
                // Allow sending even offline (will queue or show locally)
            }
        });
    }

    /**
     * Setup channels list
     */
    private void setupChannelsList() {
        channelsList.setItems(channels);
        channelsList.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(ChannelItem item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    HBox hbox = new HBox(10);
                    hbox.setAlignment(Pos.CENTER_LEFT);
                    hbox.setPadding(new Insets(5));

                    Label iconLabel = new Label(item.getIcon());
                    iconLabel.setStyle("-fx-font-size: 14px;");

                    Label nameLabel = new Label(item.getName());
                    nameLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 12px;");

                    Label countLabel = new Label(String.valueOf(item.getMemberCount()));
                    countLabel.setStyle("-fx-text-fill: gray; -fx-font-size: 10px;");

                    // Unread badge
                    if (item.getUnreadCount() > 0) {
                        Label unreadBadge = new Label(String.valueOf(item.getUnreadCount()));
                        unreadBadge.setStyle("-fx-background-color: #e53935; -fx-text-fill: white; " +
                                "-fx-padding: 2 6; -fx-background-radius: 10; -fx-font-size: 10px;");
                        hbox.getChildren().addAll(iconLabel, nameLabel, countLabel, unreadBadge);
                    } else {
                        hbox.getChildren().addAll(iconLabel, nameLabel, countLabel);
                    }

                    setGraphic(hbox);
                }
            }
        });

        // Handle channel selection
        channelsList.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                selectedChannel = newVal;
                loadChannelChat(newVal);
            }
        });
    }

    /**
     * Setup users list
     */
    private void setupUsersList() {
        usersList.setItems(users);
        usersList.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(UserItem item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    HBox hbox = new HBox(10);
                    hbox.setAlignment(Pos.CENTER_LEFT);
                    hbox.setPadding(new Insets(5));

                    String statusIcon = getStatusIcon(item.getStatus());

                    Label statusLabel = new Label(statusIcon);
                    statusLabel.setStyle("-fx-font-size: 10px;");

                    Label nameLabel = new Label(item.getName());
                    nameLabel.setStyle("-fx-font-size: 12px;");

                    Label roleLabel = new Label(item.getRole());
                    roleLabel.setStyle("-fx-text-fill: gray; -fx-font-size: 9px;");

                    hbox.getChildren().addAll(statusLabel, nameLabel, roleLabel);
                    setGraphic(hbox);

                    // Double-click to start DM
                    setOnMouseClicked(event -> {
                        if (event.getClickCount() == 2 && item.getId() != null) {
                            startDirectMessage(item);
                        }
                    });
                }
            }
        });

        // Search functionality
        userSearchField.textProperty().addListener((obs, oldVal, newVal) -> {
            filterUsers(newVal);
        });
    }

    private String getStatusIcon(String status) {
        if (status == null) return "⚪";
        return switch (status) {
            case "ONLINE" -> "\uD83D\uDFE2"; // Green circle
            case "AWAY" -> "\uD83D\uDFE1";   // Yellow circle
            case "BUSY", "IN_CLASS", "IN_MEETING" -> "\uD83D\uDD34"; // Red circle
            default -> "⚪"; // White circle for offline
        };
    }

    /**
     * Setup chat area
     */
    private void setupChatArea() {
        // Auto-scroll to bottom when new messages added
        chatMessagesContainer.heightProperty().addListener((obs, oldVal, newVal) -> {
            chatScrollPane.setVvalue(1.0);
        });

        // Send on Ctrl+Enter
        messageInputArea.setOnKeyPressed(event -> {
            if (event.isControlDown() && event.getCode().toString().equals("ENTER")) {
                handleSendMessage();
            }
        });

        // Typing indicator
        messageInputArea.textProperty().addListener((obs, oldVal, newVal) -> {
            handleTypingInput();
        });
    }

    // ==================== Data Handling ====================

    private void updateChannelsList(List<TalkChannelDTO> talkChannels) {
        Platform.runLater(() -> {
            channels.clear();
            for (TalkChannelDTO channel : talkChannels) {
                channels.add(new ChannelItem(
                        channel.getId(),
                        channel.getDisplayIcon(),
                        channel.getName(),
                        channel.getMemberCount(),
                        channel.getUnreadCount()
                ));
            }

            // Select first channel if none selected
            if (selectedChannel == null && !channels.isEmpty()) {
                channelsList.getSelectionModel().select(0);
            }
        });
    }

    private void updateUsersList(List<TalkUserDTO> talkUsers) {
        Platform.runLater(() -> {
            users.clear();
            long onlineCount = 0;

            for (TalkUserDTO user : talkUsers) {
                users.add(new UserItem(
                        user.getId(),
                        user.getFullName(),
                        user.getRole() != null ? user.getRole() : "Staff",
                        user.getStatus() != null ? user.getStatus() : "OFFLINE"
                ));
                if (user.isOnline()) {
                    onlineCount++;
                }
            }

            onlineCountLabel.setText(String.valueOf(onlineCount));
        });
    }

    private void handleIncomingMessage(TalkMessageDTO message) {
        Platform.runLater(() -> {
            // Only add to chat if it's for the selected channel
            if (selectedChannel != null && selectedChannel.getId() != null
                    && selectedChannel.getId().equals(message.getChannelId())) {
                addChatMessage(
                        message.getSenderName(),
                        message.getContent(),
                        message.getTimestamp() != null ? message.getTimestamp() : LocalDateTime.now()
                );
            } else {
                // Update unread count for other channels
                for (ChannelItem channel : channels) {
                    if (channel.getId() != null && channel.getId().equals(message.getChannelId())) {
                        channel.setUnreadCount(channel.getUnreadCount() + 1);
                        channelsList.refresh();
                        break;
                    }
                }
            }
        });
    }

    private void handleTypingIndicator(ChatWsMessage wsMessage) {
        // Show typing indicator in chat area
        Platform.runLater(() -> {
            log.debug("Typing indicator: {}", wsMessage.getAction());
        });
    }

    private void handlePresenceUpdate(ChatWsMessage wsMessage) {
        // Refresh users list to update status
        communicationService.loadUsers();
    }

    private void handleError(String error) {
        Platform.runLater(() -> {
            log.error("Communication error: {}", error);
        });
    }

    /**
     * Show offline mode - no sample data, just status messages
     */
    private void loadSampleData() {
        Platform.runLater(() -> {
            // Clear any existing data
            channels.clear();
            users.clear();

            // Update online count to 0
            onlineCountLabel.setText("0");

            // Show offline message in news ticker
            addNewsItem("\u26A0\uFE0F", "Not connected to messaging server. Channels will appear when connected.");

            // Update chat area with offline message
            chatTitleLabel.setText("\uD83D\uDCAC Communication Hub");
            chatMemberCountLabel.setText("Offline");
            chatMessagesContainer.getChildren().clear();
            addSystemMessage("Unable to connect to Heronix-Talk server.");
            addSystemMessage("Please ensure the messaging server is running and try again.");
            addSystemMessage("Contact your administrator if the issue persists.");
        });
    }

    private void addNewsItem(String icon, String text) {
        HBox newsItem = new HBox(10);
        newsItem.setAlignment(Pos.CENTER_LEFT);
        newsItem.getStyleClass().add("news-item");

        Label iconLabel = new Label(icon);
        iconLabel.setStyle("-fx-font-size: 12px;");

        Label textLabel = new Label(text);
        textLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 12px;");
        textLabel.setWrapText(true);

        Label timeLabel = new Label(LocalDateTime.now().format(DateTimeFormatter.ofPattern("h:mm a")));
        timeLabel.getStyleClass().add("text-secondary");
        timeLabel.setStyle("-fx-font-size: 10px;");

        newsItem.getChildren().addAll(iconLabel, textLabel, timeLabel);
        newsItem.setOnMouseClicked(e -> handleNewsItemClick(text));

        newsTickerContainer.getChildren().add(newsItem);
    }

    /**
     * Load chat for selected channel
     */
    private void loadChannelChat(ChannelItem channel) {
        chatTitleLabel.setText("\uD83D\uDCAC " + channel.getName());
        chatMemberCountLabel.setText(channel.getMemberCount() + " members");

        // Clear existing messages
        chatMessagesContainer.getChildren().clear();

        // Reset unread count
        channel.setUnreadCount(0);
        channelsList.refresh();

        if (channel.getId() != null && communicationService.isConnected()) {
            // Load messages from server
            communicationService.loadMessages(channel.getId(), 0, 50)
                    .thenAccept(messages -> Platform.runLater(() -> {
                        addSystemMessage("You joined " + channel.getName());
                        for (TalkMessageDTO msg : messages) {
                            addChatMessage(
                                    msg.getSenderName(),
                                    msg.getContent(),
                                    msg.getTimestamp() != null ? msg.getTimestamp() : LocalDateTime.now()
                            );
                        }
                        // Mark as read
                        if (!messages.isEmpty()) {
                            TalkMessageDTO lastMsg = messages.get(messages.size() - 1);
                            communicationService.markRead(channel.getId(), lastMsg.getId());
                        }
                    }));
        } else {
            // Channel without server ID means we're offline
            addSystemMessage("Not connected to messaging server.");
            addSystemMessage("Please connect to Heronix-Talk to view messages.");
        }
    }

    // ==================== User Actions ====================

    @FXML
    private void handleSendMessage() {
        String messageText = messageInputArea.getText().trim();

        if (messageText.isEmpty()) {
            return;
        }

        if (selectedChannel == null) {
            showAlert("No Channel Selected", "Please select a channel first.");
            return;
        }

        // Stop typing indicator
        if (isTyping && selectedChannel.getId() != null) {
            communicationService.sendTypingStop(selectedChannel.getId());
            isTyping = false;
        }

        String currentUser = sessionManager.getCurrentTeacher() != null
                ? sessionManager.getCurrentTeacher().getFullName()
                : "You";

        if (selectedChannel.getId() != null && communicationService.isConnected()) {
            // Send via WebSocket
            communicationService.sendMessage(selectedChannel.getId(), messageText)
                    .thenAccept(success -> {
                        if (!success) {
                            Platform.runLater(() -> {
                                // Show message locally with pending indicator
                                addChatMessage(currentUser + " (pending)", messageText, LocalDateTime.now());
                            });
                        }
                    });
        } else {
            // Cannot send messages when offline
            showAlert("Not Connected", "Cannot send messages while offline. Please ensure the Heronix-Talk server is running.");
            return;
        }

        // Clear input
        messageInputArea.clear();

        log.debug("Message sent to {}: {}", selectedChannel.getName(), messageText);
    }

    private void handleTypingInput() {
        if (selectedChannel == null || selectedChannel.getId() == null || !communicationService.isConnected()) {
            return;
        }

        // Cancel existing timer
        if (typingTimer != null) {
            typingTimer.cancel();
        }

        // Send typing start if not already typing
        if (!isTyping) {
            isTyping = true;
            communicationService.sendTypingStart(selectedChannel.getId());
        }

        // Set timer to stop typing after 2 seconds of no input
        typingTimer = new Timer();
        typingTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                if (isTyping && selectedChannel != null && selectedChannel.getId() != null) {
                    communicationService.sendTypingStop(selectedChannel.getId());
                    isTyping = false;
                }
            }
        }, 2000);
    }

    private void startDirectMessage(UserItem user) {
        if (user.getId() == null || !communicationService.isConnected()) {
            showAlert("Offline", "Direct messaging requires connection to Heronix-Talk server.");
            return;
        }

        communicationService.startDirectMessage(user.getId())
                .thenAccept(channel -> {
                    if (channel != null) {
                        Platform.runLater(() -> {
                            // Refresh channels and select the DM
                            communicationService.loadChannels();
                        });
                    }
                });
    }

    /**
     * Add chat message to display
     */
    private void addChatMessage(String sender, String message, LocalDateTime time) {
        VBox messageBox = new VBox(5);
        messageBox.getStyleClass().add("chat-message");

        HBox header = new HBox(10);
        header.setAlignment(Pos.CENTER_LEFT);

        Label senderLabel = new Label(sender);
        senderLabel.getStyleClass().add("text-bold");
        senderLabel.setStyle("-fx-font-size: 12px;");

        Label timeLabel = new Label(time.format(DateTimeFormatter.ofPattern("h:mm a")));
        timeLabel.getStyleClass().add("text-secondary");
        timeLabel.setStyle("-fx-font-size: 10px;");

        header.getChildren().addAll(senderLabel, timeLabel);

        Label messageLabel = new Label(message);
        messageLabel.setWrapText(true);
        messageLabel.setStyle("-fx-font-size: 12px;");

        messageBox.getChildren().addAll(header, messageLabel);
        chatMessagesContainer.getChildren().add(messageBox);
    }

    private void addSystemMessage(String message) {
        Label systemLabel = new Label("\u2139\uFE0F " + message);
        systemLabel.getStyleClass().add("system-message");
        systemLabel.setAlignment(Pos.CENTER);
        chatMessagesContainer.getChildren().add(systemLabel);
    }

    @FXML
    private void handlePauseNews() {
        newsTickerPaused = !newsTickerPaused;
        pauseNewsBtn.setText(newsTickerPaused ? "▶" : "⏸");
        log.debug("News ticker {}", newsTickerPaused ? "paused" : "resumed");
    }

    private void handleNewsItemClick(String newsText) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("News Story");
        alert.setHeaderText("Full Story");
        alert.setContentText(newsText);
        alert.showAndWait();
    }

    @FXML
    private void handleAddChannel() {
        showAlert("Create Channel", "Channel creation will be available in a future update.");
        log.debug("Add channel requested");
    }

    @FXML
    private void handleAttachment() {
        showAlert("Attach File", "File attachment will be available in a future update.");
        log.debug("Attachment requested");
    }

    @FXML
    private void handleEmoji() {
        showAlert("Insert Emoji", "Emoji picker will be available in a future update.");
        log.debug("Emoji picker requested");
    }

    @FXML
    private void handleChatInfo() {
        if (selectedChannel != null) {
            showAlert("Chat Info",
                    "Channel: " + selectedChannel.getName() + "\n" +
                            "Members: " + selectedChannel.getMemberCount() + "\n" +
                            "Connected: " + (communicationService.isConnected() ? "Yes" : "No"));
        }
    }

    @FXML
    private void handleChatSettings() {
        showAlert("Chat Settings", "Chat settings will be available in a future update.");
        log.debug("Chat settings requested");
    }

    // ==================== Helper Methods ====================

    private void filterUsers(String searchTerm) {
        if (searchTerm == null || searchTerm.isEmpty()) {
            if (communicationService.isConnected()) {
                communicationService.loadUsers();
            } else {
                usersList.setItems(users);
            }
        } else if (communicationService.isConnected()) {
            communicationService.searchUsers(searchTerm)
                    .thenAccept(this::updateUsersList);
        } else {
            // Local filter for offline mode
            ObservableList<UserItem> filtered = users.filtered(user ->
                    user.getName().toLowerCase().contains(searchTerm.toLowerCase()));
            usersList.setItems(filtered);
        }
    }

    private void updateOnlineCount() {
        long onlineCount = users.stream()
                .filter(u -> "ONLINE".equals(u.getStatus()) || "AWAY".equals(u.getStatus()) || "BUSY".equals(u.getStatus()))
                .count();
        onlineCountLabel.setText(String.valueOf(onlineCount));
    }

    private void showAlert(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }

    /**
     * Called when leaving the Communication Hub view
     */
    public void onViewExit() {
        // Cancel typing timer
        if (typingTimer != null) {
            typingTimer.cancel();
        }

        // Stop typing indicator
        if (isTyping && selectedChannel != null && selectedChannel.getId() != null) {
            communicationService.sendTypingStop(selectedChannel.getId());
            isTyping = false;
        }
    }

    // ==================== Inner Classes ====================

    /**
     * Channel item for list view
     */
    public static class ChannelItem {
        private final Long id;
        private final String icon;
        private final String name;
        private final int memberCount;
        private int unreadCount;

        public ChannelItem(Long id, String icon, String name, int memberCount, int unreadCount) {
            this.id = id;
            this.icon = icon;
            this.name = name;
            this.memberCount = memberCount;
            this.unreadCount = unreadCount;
        }

        public Long getId() { return id; }
        public String getIcon() { return icon; }
        public String getName() { return name; }
        public int getMemberCount() { return memberCount; }
        public int getUnreadCount() { return unreadCount; }
        public void setUnreadCount(int count) { this.unreadCount = count; }

        @Override
        public String toString() {
            return name;
        }
    }

    /**
     * User item for list view
     */
    public static class UserItem {
        private final Long id;
        private final String name;
        private final String role;
        private final String status;

        public UserItem(Long id, String name, String role, String status) {
            this.id = id;
            this.name = name;
            this.role = role;
            this.status = status;
        }

        public Long getId() { return id; }
        public String getName() { return name; }
        public String getRole() { return role; }
        public String getStatus() { return status; }

        @Override
        public String toString() {
            return name;
        }
    }
}
