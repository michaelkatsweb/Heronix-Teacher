package com.heronix.teacher.ui.controller;

import com.heronix.teacher.model.dto.talk.*;
import com.heronix.teacher.service.CommunicationService;
import com.heronix.teacher.service.SessionManager;
import com.heronix.teacher.ui.component.MessageBubble;
import com.heronix.teacher.ui.dialog.ChannelMembersDialog;
import com.heronix.teacher.ui.dialog.CreateChannelDialog;
import com.heronix.teacher.ui.dialog.EmojiPickerPopup;
import com.heronix.teacher.ui.dialog.ForwardMessageDialog;
import com.heronix.teacher.ui.dialog.InvitationDialog;
import com.heronix.teacher.ui.dialog.MessageSearchDialog;
import com.heronix.teacher.ui.dialog.ChatSettingsDialog;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

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

    // Root container for keyboard shortcuts
    @FXML private BorderPane rootPane;

    // News Ticker Components
    @FXML private ScrollPane newsScrollPane;
    @FXML private HBox newsTickerContainer;
    @FXML private Button pauseNewsBtn;

    // Channels and Users
    @FXML private ListView<ChannelItem> channelsList;
    @FXML private ListView<UserItem> usersList;
    @FXML private ComboBox<UserItem> usersComboBox;
    @FXML private TextField channelSearchField;
    @FXML private Label onlineCountLabel;

    // Chat Components
    @FXML private ScrollPane chatScrollPane;
    @FXML private VBox chatMessagesContainer;
    @FXML private TextArea messageInputArea;
    @FXML private Button sendMessageBtn;
    @FXML private Label chatTitleLabel;
    @FXML private Label chatMemberCountLabel;
    @FXML private Label typingIndicatorLabel;

    // Connection Status (optional - may not exist in FXML)
    @FXML private Label connectionStatusLabel;

    // Connection Status Banner (diagnostic UI)
    @FXML private HBox connectionStatusBanner;
    @FXML private Label connectionStatusIcon;
    @FXML private Label connectionStatusDetail;
    @FXML private Button retryConnectionBtn;

    // Channel placeholder labels for dynamic messages
    @FXML private Label channelPlaceholderTitle;
    @FXML private Label channelPlaceholderMessage;

    // Invitation badge button (optional - may not exist in FXML)
    @FXML private Button invitationBadgeBtn;

    // Sound toggle button
    @FXML private Button soundToggleBtn;

    // Reply preview bar (FXML-defined)
    @FXML private HBox replyPreviewBar;
    @FXML private Label replyPreviewSender;
    @FXML private Label replyPreviewContent;
    @FXML private Button cancelReplyBtn;

    // My Status components
    @FXML private Region myStatusIndicator;
    @FXML private MenuButton statusMenuButton;
    @FXML private TextField statusMessageField;

    // Data
    private ObservableList<ChannelItem> channels;
    private ObservableList<UserItem> users;
    private ChannelItem selectedChannel;
    private boolean newsTickerPaused = false;
    private Timer typingTimer;
    private boolean isTyping = false;
    private int pendingInvitationCount = 0;

    // Message tracking for features
    private final Map<Long, MessageBubble> messageBubbleMap = new ConcurrentHashMap<>();
    private final Map<String, Long> pendingClientIds = new ConcurrentHashMap<>(); // clientId -> tempId for deduplication
    private final Map<Long, String> channelDrafts = new ConcurrentHashMap<>(); // channelId -> draft message
    private TalkMessageDTO replyToMessage = null;
    private Long currentUserId = null;
    private long tempMessageIdCounter = -1; // Negative IDs for pending messages
    private String currentUserStatus = "ONLINE"; // Current user's status
    private ChatSettingsDialog.ChatSettings currentChatSettings = ChatSettingsDialog.ChatSettings.getDefault();

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
        setupMyStatusSelector();
        setupKeyboardShortcuts();

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
        communicationService.setOnNewsReceived(this::handleIncomingNews);
        communicationService.setOnNewsUpdated(this::updateNewsList);
        communicationService.setOnAlertReceived(this::handleIncomingAlert);
        communicationService.setOnInvitationReceived(this::handleIncomingInvitation);
    }

    private void initializeTalkConnection() {
        log.info("=== initializeTalkConnection called ===");

        // Show connecting status in banner
        showConnectionBanner("connecting", "Connecting to Heronix-Talk...",
                "Establishing connection to messaging server", false);

        if (sessionManager.getCurrentTeacher() == null) {
            log.warn("No teacher logged in, cannot initialize Talk connection");
            showConnectionBanner("error", "Not Logged In",
                    "Please log in to access messaging features", false);
            updateConnectionStatus(false);
            loadSampleData();
            return;
        }

        String storedPassword = sessionManager.getStoredPassword();
        log.info("Stored password available: {}", storedPassword != null);
        if (storedPassword == null) {
            log.warn("No stored password for Talk authentication");
            showConnectionBanner("error", "Authentication Required",
                    "Please log out and log back in to enable messaging", false);
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
        log.info("Initializing Talk connection for employee: {}", employeeId);

        // First check if server is reachable
        showConnectionBanner("connecting", "Checking Server...",
                "Verifying Heronix-Talk server is available (port 9680)", false);

        communicationService.initialize(employeeId, storedPassword)
                .thenAccept(success -> Platform.runLater(() -> {
                    log.info("CommunicationService.initialize() returned: {}", success);
                    if (success) {
                        log.info("Connected to Heronix-Talk server successfully");
                        hideConnectionBanner();
                        updateConnectionStatus(true);
                        // Load news after successful connection
                        loadNewsFromServer();
                        // Check for pending invitations
                        refreshInvitationBadge();
                        // Explicitly load channels after connection is confirmed
                        log.info("Explicitly loading channels after successful connection...");
                        communicationService.loadChannels()
                                .thenAccept(loadedChannels -> Platform.runLater(() -> {
                                    log.info("Channels loaded via explicit call: {} channels",
                                            loadedChannels != null ? loadedChannels.size() : 0);
                                    if (loadedChannels == null || loadedChannels.isEmpty()) {
                                        showConnectionBanner("warning", "No Channels Available",
                                                "Connected to server but no channels found. Default channels may not be created yet.", false);
                                        updateChannelPlaceholder("No channels available",
                                                "Server connected but no channels exist yet. Click + to create one or wait for server to initialize default channels.");
                                    }
                                }));
                    } else {
                        log.warn("Could not connect to Heronix-Talk, using offline mode");
                        showConnectionBanner("error", "Server Unavailable",
                                "Cannot connect to Heronix-Talk server (port 9680). Make sure the Talk server is running.", true);
                        updateChannelPlaceholder("Server Offline",
                                "Heronix-Talk server is not running. Start the server and click Retry.");
                        updateConnectionStatus(false);
                        loadSampleData();
                    }
                }))
                .exceptionally(ex -> {
                    Platform.runLater(() -> {
                        log.error("Exception during Talk initialization", ex);
                        String errorDetail = ex.getCause() != null ? ex.getCause().getMessage() : ex.getMessage();
                        showConnectionBanner("error", "Connection Failed",
                                "Error: " + errorDetail, true);
                        updateChannelPlaceholder("Connection Error",
                                "Failed to connect: " + errorDetail);
                        updateConnectionStatus(false);
                        loadSampleData();
                    });
                    return null;
                });
    }

    /**
     * Show the connection status banner with appropriate styling
     * @param type "connecting", "warning", "error", or "success"
     * @param title Main status message
     * @param detail Detailed explanation
     * @param showRetry Whether to show the retry button
     */
    private void showConnectionBanner(String type, String title, String detail, boolean showRetry) {
        Platform.runLater(() -> {
            if (connectionStatusBanner == null) return;

            connectionStatusBanner.setVisible(true);
            connectionStatusBanner.setManaged(true);

            if (connectionStatusLabel != null) {
                connectionStatusLabel.setText(title);
            }
            if (connectionStatusDetail != null) {
                connectionStatusDetail.setText(detail);
            }
            if (retryConnectionBtn != null) {
                retryConnectionBtn.setVisible(showRetry);
                retryConnectionBtn.setManaged(showRetry);
            }

            // Style based on type
            String bgColor, textColor, icon;
            switch (type) {
                case "connecting":
                    bgColor = "#E3F2FD"; // Light blue
                    textColor = "#1565C0";
                    icon = "🔄";
                    break;
                case "warning":
                    bgColor = "#FFF3CD"; // Light yellow
                    textColor = "#856404";
                    icon = "⚠";
                    break;
                case "error":
                    bgColor = "#F8D7DA"; // Light red
                    textColor = "#721C24";
                    icon = "❌";
                    break;
                case "success":
                    bgColor = "#D4EDDA"; // Light green
                    textColor = "#155724";
                    icon = "✓";
                    break;
                default:
                    bgColor = "#E9ECEF";
                    textColor = "#495057";
                    icon = "ℹ";
            }

            connectionStatusBanner.setStyle("-fx-padding: 8 12; -fx-background-color: " + bgColor + "; -fx-background-radius: 6;");
            if (connectionStatusIcon != null) {
                connectionStatusIcon.setText(icon);
            }
            if (connectionStatusLabel != null) {
                connectionStatusLabel.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: " + textColor + ";");
            }
            if (connectionStatusDetail != null) {
                connectionStatusDetail.setStyle("-fx-font-size: 10px; -fx-text-fill: " + textColor + ";");
            }
        });
    }

    /**
     * Hide the connection status banner
     */
    private void hideConnectionBanner() {
        Platform.runLater(() -> {
            if (connectionStatusBanner != null) {
                connectionStatusBanner.setVisible(false);
                connectionStatusBanner.setManaged(false);
            }
        });
    }

    /**
     * Update the channel list placeholder text
     */
    private void updateChannelPlaceholder(String title, String message) {
        Platform.runLater(() -> {
            if (channelPlaceholderTitle != null) {
                channelPlaceholderTitle.setText(title);
            }
            if (channelPlaceholderMessage != null) {
                channelPlaceholderMessage.setText(message);
            }
        });
    }

    /**
     * Handle retry connection button click
     */
    @FXML
    private void handleRetryConnection() {
        log.info("Retry connection requested");
        hideConnectionBanner();
        updateChannelPlaceholder("Connecting...", "Attempting to reconnect to server...");
        initializeTalkConnection();
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
     * Setup my status selector
     */
    private void setupMyStatusSelector() {
        if (statusMenuButton != null) {
            // Setup status message field action
            if (statusMessageField != null) {
                statusMessageField.setOnAction(e -> updateStatusMessage());
                statusMessageField.focusedProperty().addListener((obs, wasFocused, isNowFocused) -> {
                    if (!isNowFocused && wasFocused) {
                        updateStatusMessage();
                    }
                });
            }
            // Set initial status
            updateMyStatusDisplay("ONLINE", null);
        }
    }

    /**
     * Setup keyboard shortcuts for common actions.
     *
     * Shortcuts:
     * - Ctrl+N: Create new channel
     * - Ctrl+F: Search messages
     * - Ctrl+E: Open emoji picker (when message input is focused)
     * - Ctrl+Up: Navigate to previous channel
     * - Ctrl+Down: Navigate to next channel
     * - Ctrl+M: Focus message input
     * - Ctrl+Shift+S: Toggle sound notifications
     * - Ctrl+Enter: Send message (in message input)
     * - Escape: Cancel reply (in message input)
     */
    private void setupKeyboardShortcuts() {
        if (rootPane == null) {
            log.warn("Root pane not available for keyboard shortcuts");
            return;
        }

        rootPane.setOnKeyPressed(event -> {
            // Ctrl+N: Create new channel
            if (event.isControlDown() && event.getCode() == KeyCode.N) {
                event.consume();
                handleAddChannel();
                return;
            }

            // Ctrl+F: Search messages
            if (event.isControlDown() && event.getCode() == KeyCode.F) {
                event.consume();
                handleSearchMessages();
                return;
            }

            // Ctrl+E: Open emoji picker
            if (event.isControlDown() && event.getCode() == KeyCode.E) {
                event.consume();
                if (messageInputArea != null && messageInputArea.isFocused()) {
                    handleEmoji();
                }
                return;
            }

            // Ctrl+Up: Previous channel
            if (event.isControlDown() && event.getCode() == KeyCode.UP) {
                event.consume();
                navigateChannel(-1);
                return;
            }

            // Ctrl+Down: Next channel
            if (event.isControlDown() && event.getCode() == KeyCode.DOWN) {
                event.consume();
                navigateChannel(1);
                return;
            }

            // Ctrl+M: Focus message input
            if (event.isControlDown() && event.getCode() == KeyCode.M) {
                event.consume();
                if (messageInputArea != null) {
                    messageInputArea.requestFocus();
                }
                return;
            }

            // Ctrl+Shift+S: Toggle sound
            if (event.isControlDown() && event.isShiftDown() && event.getCode() == KeyCode.S) {
                event.consume();
                handleToggleSound();
                return;
            }
        });

        log.info("Keyboard shortcuts initialized");
    }

    /**
     * Navigate to previous or next channel
     */
    private void navigateChannel(int direction) {
        if (channelsList == null || channels.isEmpty()) return;

        int currentIndex = channelsList.getSelectionModel().getSelectedIndex();
        int newIndex = currentIndex + direction;

        if (newIndex >= 0 && newIndex < channels.size()) {
            channelsList.getSelectionModel().select(newIndex);
            channelsList.scrollTo(newIndex);
        }
    }

    /**
     * Update the status message on the server
     */
    private void updateStatusMessage() {
        if (statusMessageField != null && communicationService.isConnected()) {
            String statusMessage = statusMessageField.getText();
            communicationService.updatePresence(currentUserStatus, statusMessage);
            log.debug("Status message updated: {}", statusMessage);
        }
    }

    /**
     * Update my status display (indicator and button text)
     */
    private void updateMyStatusDisplay(String status, String statusMessage) {
        Platform.runLater(() -> {
            currentUserStatus = status;

            // Update status indicator color
            if (myStatusIndicator != null) {
                myStatusIndicator.getStyleClass().removeAll(
                    "status-online", "status-away", "status-busy",
                    "status-in-class", "status-silent", "status-offline"
                );
                myStatusIndicator.getStyleClass().add(getStatusStyleClass(status));
            }

            // Update menu button text
            if (statusMenuButton != null) {
                statusMenuButton.setText(getStatusDisplayText(status));
            }

            // Update status message field if provided
            if (statusMessageField != null && statusMessage != null && !statusMessage.isEmpty()) {
                statusMessageField.setText(statusMessage);
            }
        });
    }

    /**
     * Get CSS class for status
     */
    private String getStatusStyleClass(String status) {
        if (status == null) return "status-offline";
        return switch (status.toUpperCase()) {
            case "ONLINE" -> "status-online";
            case "AWAY" -> "status-away";
            case "BUSY", "IN_MEETING" -> "status-busy";
            case "IN_CLASS" -> "status-in-class";
            case "SILENT", "DND" -> "status-silent";
            default -> "status-offline";
        };
    }

    /**
     * Get display text for status
     */
    private String getStatusDisplayText(String status) {
        if (status == null) return "Offline";
        return switch (status.toUpperCase()) {
            case "ONLINE" -> "Online";
            case "AWAY" -> "Away";
            case "BUSY" -> "Busy";
            case "IN_CLASS" -> "In Class";
            case "IN_MEETING" -> "In Meeting";
            case "SILENT", "DND" -> "Silent Mode";
            default -> "Offline";
        };
    }

    // ==================== Status Action Handlers ====================

    @FXML
    private void handleSetStatusOnline() {
        setUserStatus("ONLINE");
    }

    @FXML
    private void handleSetStatusAway() {
        setUserStatus("AWAY");
    }

    @FXML
    private void handleSetStatusBusy() {
        setUserStatus("BUSY");
    }

    @FXML
    private void handleSetStatusInClass() {
        setUserStatus("IN_CLASS");
    }

    @FXML
    private void handleSetStatusSilent() {
        setUserStatus("SILENT");
    }

    /**
     * Set user status and broadcast to server
     */
    private void setUserStatus(String status) {
        log.info("Setting user status to: {}", status);
        currentUserStatus = status;
        updateMyStatusDisplay(status, null);

        // Send to server via WebSocket
        if (communicationService.isConnected()) {
            String statusMessage = statusMessageField != null ? statusMessageField.getText() : "";
            communicationService.updatePresence(status, statusMessage);
        }
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
                // Save draft from previous channel
                if (oldVal != null && oldVal.getId() != null) {
                    saveDraft(oldVal.getId());
                }

                selectedChannel = newVal;
                loadChannelChat(newVal);

                // Restore draft for new channel
                restoreDraft(newVal.getId());
            }
        });
    }

    /**
     * Save the current message input as a draft for the channel
     */
    private void saveDraft(Long channelId) {
        if (channelId == null) return;

        String text = messageInputArea.getText();
        if (text != null && !text.trim().isEmpty()) {
            channelDrafts.put(channelId, text);
            log.debug("Draft saved for channel {}: {} chars", channelId, text.length());
        } else {
            channelDrafts.remove(channelId);
        }
    }

    /**
     * Restore a saved draft for the channel
     */
    private void restoreDraft(Long channelId) {
        if (channelId == null) return;

        String draft = channelDrafts.get(channelId);
        if (draft != null && !draft.isEmpty()) {
            messageInputArea.setText(draft);
            messageInputArea.positionCaret(draft.length());
            log.debug("Draft restored for channel {}: {} chars", channelId, draft.length());
        } else {
            messageInputArea.clear();
        }
    }

    /**
     * Setup users ComboBox dropdown
     */
    private void setupUsersList() {
        // Keep the hidden ListView for data storage
        usersList.setItems(users);

        // Setup ComboBox for user selection
        if (usersComboBox != null) {
            usersComboBox.setItems(users);

            // Custom cell factory for dropdown display
            usersComboBox.setCellFactory(lv -> new ListCell<>() {
                @Override
                protected void updateItem(UserItem item, boolean empty) {
                    super.updateItem(item, empty);
                    if (empty || item == null) {
                        setText(null);
                        setGraphic(null);
                    } else {
                        HBox hbox = new HBox(8);
                        hbox.setAlignment(Pos.CENTER_LEFT);

                        // Status indicator
                        Region statusDot = new Region();
                        statusDot.getStyleClass().addAll("status-dot", getStatusStyleClass(item.getStatus()));
                        statusDot.setMinSize(8, 8);
                        statusDot.setMaxSize(8, 8);

                        // Name and role
                        Label nameLabel = new Label(item.getName());
                        nameLabel.getStyleClass().add("user-name-label");

                        Label roleLabel = new Label(" - " + item.getRole());
                        roleLabel.getStyleClass().add("user-role-label");
                        roleLabel.setStyle("-fx-font-size: 11px; -fx-opacity: 0.7;");

                        hbox.getChildren().addAll(statusDot, nameLabel, roleLabel);
                        setGraphic(hbox);
                    }
                }
            });

            // Button cell (what shows when dropdown is closed)
            usersComboBox.setButtonCell(new ListCell<>() {
                @Override
                protected void updateItem(UserItem item, boolean empty) {
                    super.updateItem(item, empty);
                    if (empty || item == null) {
                        setText("Select user to message...");
                        setGraphic(null);
                    } else {
                        HBox hbox = new HBox(6);
                        hbox.setAlignment(Pos.CENTER_LEFT);

                        Region statusDot = new Region();
                        statusDot.getStyleClass().addAll("status-dot", getStatusStyleClass(item.getStatus()));
                        statusDot.setMinSize(8, 8);
                        statusDot.setMaxSize(8, 8);

                        Label nameLabel = new Label(item.getName());
                        hbox.getChildren().addAll(statusDot, nameLabel);
                        setGraphic(hbox);
                    }
                }
            });

            // Handle user selection - start DM
            usersComboBox.setOnAction(event -> {
                UserItem selected = usersComboBox.getValue();
                if (selected != null && selected.getId() != null) {
                    startDirectMessage(selected);
                    // Clear selection after starting DM
                    Platform.runLater(() -> usersComboBox.setValue(null));
                }
            });
        }

        // Channel search functionality
        if (channelSearchField != null) {
            channelSearchField.textProperty().addListener((obs, oldVal, newVal) -> {
                filterChannels(newVal);
            });
        }
    }

    /**
     * Filter channels by search term
     */
    private void filterChannels(String searchTerm) {
        if (searchTerm == null || searchTerm.trim().isEmpty()) {
            channelsList.setItems(channels);
            return;
        }

        String lowerSearch = searchTerm.toLowerCase().trim();
        ObservableList<ChannelItem> filtered = channels.filtered(channel ->
                channel.getName().toLowerCase().contains(lowerSearch)
        );
        channelsList.setItems(filtered);
    }

    private String getStatusIcon(String status) {
        if (status == null) return "⚪";
        return switch (status.toUpperCase()) {
            case "ONLINE" -> "\uD83D\uDFE2"; // Green circle
            case "AWAY" -> "\uD83D\uDFE1";   // Yellow circle
            case "BUSY", "IN_CLASS", "IN_MEETING" -> "\uD83D\uDD34"; // Red circle
            case "SILENT", "DND" -> "\uD83D\uDFE3"; // Purple circle
            default -> "⚪"; // White circle for offline
        };
    }

    /**
     * Get CSS class for user cell based on status
     */
    private String getUserCellStyleClass(String status) {
        if (status == null) return "user-cell-offline";
        return switch (status.toUpperCase()) {
            case "ONLINE" -> "user-cell-online";
            case "AWAY" -> "user-cell-away";
            case "BUSY", "IN_CLASS", "IN_MEETING" -> "user-cell-busy";
            case "SILENT", "DND" -> "user-cell-silent";
            default -> "user-cell-offline";
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
            // Escape to cancel reply
            if (event.getCode().toString().equals("ESCAPE") && replyToMessage != null) {
                cancelReply();
            }
        });

        // Typing indicator
        messageInputArea.textProperty().addListener((obs, oldVal, newVal) -> {
            handleTypingInput();
        });

        // Get current user ID
        if (communicationService.getCurrentUserId() != null) {
            currentUserId = communicationService.getCurrentUserId();
        }
    }

    // ==================== Data Handling ====================

    private void updateChannelsList(List<TalkChannelDTO> talkChannels) {
        log.info("updateChannelsList called with {} channels", talkChannels != null ? talkChannels.size() : 0);
        if (talkChannels == null) {
            log.warn("updateChannelsList received null channel list");
            return;
        }
        Platform.runLater(() -> {
            log.info("Updating UI with {} channels on FX thread", talkChannels.size());
            channels.clear();
            for (TalkChannelDTO channel : talkChannels) {
                log.debug("Adding channel to UI: {} (id={}, type={})",
                        channel.getName(), channel.getId(), channel.getChannelType());
                channels.add(new ChannelItem(
                        channel.getId(),
                        channel.getDisplayIcon(),
                        channel.getName(),
                        channel.getChannelType(),
                        channel.getMemberCount(),
                        channel.getUnreadCount()
                ));
            }
            log.info("Channels list now has {} items", channels.size());

            // Select first channel if none selected
            if (selectedChannel == null && !channels.isEmpty()) {
                channelsList.getSelectionModel().select(0);
                log.info("Auto-selected first channel: {}", channels.get(0).getName());
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
        log.info("handleIncomingMessage called: id={}, channelId={}, senderId={}, clientId={}",
                message.getId(), message.getChannelId(), message.getSenderId(), message.getClientId());

        // Ensure we're on the FX thread
        if (!Platform.isFxApplicationThread()) {
            Platform.runLater(() -> handleIncomingMessage(message));
            return;
        }

        // Check if this is an echo of a message we sent (deduplication)
        String clientId = message.getClientId();
        if (clientId != null && pendingClientIds.containsKey(clientId)) {
            // This is the server echo of our own message - remove the pending one
            Long tempId = pendingClientIds.remove(clientId);
            if (tempId != null) {
                log.debug("Removing pending message with tempId={} for clientId={}", tempId, clientId);
                removePendingMessage(tempId);
            }
        }

        // Check channel match using Objects.equals for null-safe comparison
        Long selectedId = selectedChannel != null ? selectedChannel.getId() : null;
        Long messageChannelId = message.getChannelId();
        boolean isCurrentChannel = java.util.Objects.equals(selectedId, messageChannelId);

        log.info("Channel comparison: selectedId={}, messageChannelId={}, match={}",
                selectedId, messageChannelId, isCurrentChannel);

        if (isCurrentChannel && selectedId != null) {
            log.info("Adding message to current channel view: channelId={}", messageChannelId);
            addMessageBubble(message);
        } else {
            log.debug("Message is for different channel (selected={}, message={}), updating unread count",
                    selectedId, messageChannelId);
            // Update unread count for other channels
            for (ChannelItem channel : channels) {
                if (channel.getId() != null && channel.getId().equals(messageChannelId)) {
                    channel.setUnreadCount(channel.getUnreadCount() + 1);
                    channelsList.refresh();
                    break;
                }
            }
        }
    }

    private void handleTypingIndicator(ChatWsMessage wsMessage) {
        Platform.runLater(() -> {
            if (typingIndicatorLabel == null) return;

            // Only show typing for current channel
            if (selectedChannel == null || !selectedChannel.getId().equals(wsMessage.getChannelId())) {
                return;
            }

            String action = wsMessage.getAction();
            if (ChatWsMessage.ACTION_TYPING_START.equals(action)) {
                // Get user name from payload if available
                String userName = "Someone";
                if (wsMessage.getPayload() instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> payload = (Map<String, Object>) wsMessage.getPayload();
                    if (payload.containsKey("userName")) {
                        userName = String.valueOf(payload.get("userName"));
                    }
                }
                typingIndicatorLabel.setText(userName + " is typing...");
                log.debug("Typing indicator: {} is typing", userName);
            } else if (ChatWsMessage.ACTION_TYPING_STOP.equals(action)) {
                typingIndicatorLabel.setText("");
            }
        });
    }

    private void handlePresenceUpdate(ChatWsMessage wsMessage) {
        // Refresh users list to update status
        communicationService.loadUsers();
    }

    private void handleError(String error) {
        Platform.runLater(() -> {
            log.error("Communication error: {}", error);
            // Show error in status bar or as alert for important errors
            if (error != null && error.toLowerCase().contains("not saved")) {
                showAlert("Message Error", error);
            }
        });
    }

    // ========================================================================
    // NEWS HANDLERS
    // ========================================================================

    /**
     * Load news from server
     */
    private void loadNewsFromServer() {
        communicationService.loadNews(20)
                .thenAccept(newsItems -> Platform.runLater(() -> {
                    log.info("Loaded {} news items from server", newsItems.size());
                    newsTickerContainer.getChildren().clear();
                    for (TalkNewsItemDTO news : newsItems) {
                        addNewsItemFromDTO(news);
                    }
                    if (newsItems.isEmpty()) {
                        addNewsItem("\uD83D\uDCF0", "No news items available");
                    }
                }));
    }

    /**
     * Handle incoming news via WebSocket
     */
    private void handleIncomingNews(TalkNewsItemDTO newsItem) {
        Platform.runLater(() -> {
            log.info("New news received: {}", newsItem.getHeadline());
            // Add at the beginning of the list (most recent first)
            addNewsItemFromDTOAtTop(newsItem);
        });
    }

    /**
     * Update news list (full refresh)
     */
    private void updateNewsList(List<TalkNewsItemDTO> newsItems) {
        Platform.runLater(() -> {
            newsTickerContainer.getChildren().clear();
            for (TalkNewsItemDTO news : newsItems) {
                addNewsItemFromDTO(news);
            }
        });
    }

    /**
     * Add news item from DTO to the ticker banner
     */
    private void addNewsItemFromDTO(TalkNewsItemDTO news) {
        // Add separator if not first item
        if (!newsTickerContainer.getChildren().isEmpty()) {
            newsTickerContainer.getChildren().add(createTickerSeparator());
        }
        Label newsLabel = createNewsItemLabel(news);
        newsTickerContainer.getChildren().add(newsLabel);
    }

    /**
     * Add news item from DTO at the beginning (for real-time updates)
     */
    private void addNewsItemFromDTOAtTop(TalkNewsItemDTO news) {
        Label newsLabel = createNewsItemLabel(news);
        // Add at beginning with separator after it
        newsTickerContainer.getChildren().add(0, createTickerSeparator());
        newsTickerContainer.getChildren().add(0, newsLabel);
    }

    /**
     * Create news item label for horizontal banner ticker
     */
    private Label createNewsItemLabel(TalkNewsItemDTO news) {
        String icon = news.getDisplayIcon();
        String headline = news.getHeadline();

        Label newsLabel = new Label(icon + "  " + headline);
        newsLabel.getStyleClass().add("news-ticker-item");
        newsLabel.setOnMouseClicked(e -> handleNewsItemClick(headline));
        newsLabel.setCursor(javafx.scene.Cursor.HAND);

        // Style urgent items differently
        if (news.isUrgent()) {
            newsLabel.setStyle("-fx-text-fill: #ff6b6b; -fx-font-weight: bold;");
        }

        return newsLabel;
    }

    /**
     * Create separator label for ticker
     */
    private Label createTickerSeparator() {
        Label separator = new Label("\u2022"); // Bullet point
        separator.getStyleClass().add("news-ticker-separator");
        return separator;
    }

    // ========================================================================
    // ALERT HANDLERS
    // ========================================================================

    /**
     * Handle incoming emergency alert via WebSocket
     */
    private void handleIncomingAlert(TalkAlertDTO alert) {
        Platform.runLater(() -> {
            log.warn("ALERT received in UI: [{}] {} - {}",
                    alert.getAlertLevel(), alert.getAlertType(), alert.getTitle());

            // Determine alert style based on level
            String dialogStyle = getAlertDialogStyle(alert.getAlertLevel());
            Alert.AlertType alertType = alert.isCritical()
                    ? Alert.AlertType.ERROR
                    : Alert.AlertType.WARNING;

            // Create alert dialog
            Alert alertDialog = new Alert(alertType);
            alertDialog.setTitle(alert.getAlertLevel() + " ALERT");
            alertDialog.setHeaderText(alert.getIcon() + " " + alert.getTitle());

            // Build content text
            StringBuilder content = new StringBuilder();
            content.append(alert.getMessage());

            if (alert.getTargetDepartments() != null && !alert.getTargetDepartments().isEmpty()) {
                content.append("\n\nAffected Areas: ").append(alert.getTargetDepartments());
            }
            if (alert.getInstructions() != null && !alert.getInstructions().isEmpty()) {
                content.append("\n\nInstructions: ").append(alert.getInstructions());
            }
            if (alert.getIssuedByName() != null) {
                content.append("\n\nIssued by: ").append(alert.getIssuedByName());
            }

            alertDialog.setContentText(content.toString());

            // Style the dialog pane for critical alerts
            if (alert.isCritical()) {
                alertDialog.getDialogPane().setStyle(
                        "-fx-background-color: #ffebee; " +
                        "-fx-border-color: #f44336; " +
                        "-fx-border-width: 3px;"
                );
            }

            // Add acknowledge button for critical alerts
            if (alert.isRequiresAcknowledgment()) {
                ButtonType acknowledgeBtn = new ButtonType("Acknowledge", ButtonBar.ButtonData.OK_DONE);
                alertDialog.getButtonTypes().setAll(acknowledgeBtn);

                alertDialog.showAndWait().ifPresent(response -> {
                    if (response == acknowledgeBtn && alert.getId() != null) {
                        acknowledgeAlert(alert.getId());
                    }
                });
            } else {
                // Just show the alert
                alertDialog.show();
            }

            // Also add to news ticker for visibility
            addAlertToNewsTicker(alert);
        });
    }

    /**
     * Acknowledge an alert on the server
     */
    private void acknowledgeAlert(Long alertId) {
        if (!communicationService.isConnected()) return;

        communicationService.acknowledgeAlert(alertId)
                .thenAccept(success -> Platform.runLater(() -> {
                    if (success) {
                        log.info("Alert {} acknowledged successfully", alertId);
                    } else {
                        log.warn("Failed to acknowledge alert {}", alertId);
                    }
                }));
    }

    /**
     * Get dialog style based on alert level
     */
    private String getAlertDialogStyle(String alertLevel) {
        if (alertLevel == null) return "";
        return switch (alertLevel.toUpperCase()) {
            case "EMERGENCY" -> "-fx-background-color: #b71c1c;";
            case "URGENT" -> "-fx-background-color: #e65100;";
            case "HIGH" -> "-fx-background-color: #f57c00;";
            case "NORMAL" -> "-fx-background-color: #1976d2;";
            case "LOW" -> "-fx-background-color: #388e3c;";
            default -> "";
        };
    }

    /**
     * Add alert to news ticker for persistent visibility
     */
    private void addAlertToNewsTicker(TalkAlertDTO alert) {
        HBox alertItem = new HBox(12);
        // For horizontal banner, create a compact alert label
        String alertText = alert.getIcon() + " [" + alert.getAlertLevel() + "] " + alert.getTitle();
        Label alertLabel = new Label(alertText);
        alertLabel.getStyleClass().add("news-ticker-item");
        alertLabel.setCursor(javafx.scene.Cursor.HAND);

        // Critical alerts get special styling
        if (alert.isCritical()) {
            alertLabel.setStyle("-fx-text-fill: #ff6b6b; -fx-font-weight: bold;");
        } else {
            alertLabel.setStyle("-fx-text-fill: #ffa726; -fx-font-weight: bold;");
        }

        // Click to show full alert again
        alertLabel.setOnMouseClicked(e -> handleIncomingAlert(alert));

        // Add separator if not first item, then add at the beginning
        if (!newsTickerContainer.getChildren().isEmpty()) {
            newsTickerContainer.getChildren().add(0, createTickerSeparator());
        }
        newsTickerContainer.getChildren().add(0, alertLabel);
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
        // For horizontal banner, create a simple label
        if (!newsTickerContainer.getChildren().isEmpty()) {
            newsTickerContainer.getChildren().add(createTickerSeparator());
        }

        Label newsLabel = new Label(icon + "  " + text);
        newsLabel.getStyleClass().add("news-ticker-item");
        newsLabel.setCursor(javafx.scene.Cursor.HAND);
        newsLabel.setOnMouseClicked(e -> handleNewsItemClick(text));

        newsTickerContainer.getChildren().add(newsLabel);
    }

    /**
     * Load chat for selected channel
     */
    private void loadChannelChat(ChannelItem channel) {
        chatTitleLabel.setText("\uD83D\uDCAC " + channel.getName());
        chatMemberCountLabel.setText(channel.getMemberCount() + " members");

        // Clear existing messages and tracking
        chatMessagesContainer.getChildren().clear();
        messageBubbleMap.clear();
        cancelReply(); // Clear any pending reply

        // Reset unread count
        channel.setUnreadCount(0);
        channelsList.refresh();

        if (channel.getId() != null && communicationService.isConnected()) {
            // First, subscribe to channel via WebSocket to receive real-time updates
            // This is critical for DMs and private channels
            communicationService.subscribeToChannel(channel.getId());

            // Load messages from server
            communicationService.loadMessages(channel.getId(), 0, 50)
                    .thenAccept(messages -> Platform.runLater(() -> {
                        addSystemMessage("You joined " + channel.getName());
                        for (TalkMessageDTO msg : messages) {
                            addMessageBubble(msg);
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
            // Generate a client-side ID for deduplication
            String clientId = UUID.randomUUID().toString();

            // Create optimistic message to show immediately
            long tempId = tempMessageIdCounter--;
            TalkMessageDTO optimisticMsg = new TalkMessageDTO();
            optimisticMsg.setId(tempId);
            optimisticMsg.setClientId(clientId);
            optimisticMsg.setSenderId(currentUserId);
            optimisticMsg.setSenderName(currentUser);
            optimisticMsg.setContent(messageText);
            optimisticMsg.setChannelId(selectedChannel.getId());
            optimisticMsg.setTimestamp(LocalDateTime.now());
            optimisticMsg.setMessageType("TEXT");

            // Track this pending message for deduplication
            pendingClientIds.put(clientId, tempId);

            // Show message immediately (optimistic update)
            addMessageBubble(optimisticMsg);

            // Check if this is a reply
            if (replyToMessage != null) {
                optimisticMsg.setReplyToId(replyToMessage.getId());
                optimisticMsg.setReplyToSenderName(replyToMessage.getSenderName());
                optimisticMsg.setReplyToPreview(replyToMessage.getContent() != null && replyToMessage.getContent().length() > 50
                        ? replyToMessage.getContent().substring(0, 47) + "..."
                        : replyToMessage.getContent());

                // Send as reply
                communicationService.sendReply(selectedChannel.getId(), replyToMessage.getId(), messageText)
                        .thenAccept(message -> Platform.runLater(() -> {
                            if (message == null) {
                                // Mark message as failed
                                markMessageAsFailed(tempId);
                            }
                            // Remove from pending (server echo will have the real ID)
                            pendingClientIds.remove(clientId);
                        }));
                cancelReply(); // Clear reply mode
            } else {
                // Send regular message via WebSocket
                communicationService.sendMessageWithClientId(selectedChannel.getId(), messageText, clientId)
                        .thenAccept(success -> Platform.runLater(() -> {
                            if (!success) {
                                // Mark message as failed
                                markMessageAsFailed(tempId);
                            }
                            // Remove from pending after a delay to allow for echo
                            // The echo handler will handle deduplication
                        }));
            }
        } else {
            // Cannot send messages when offline
            showAlert("Not Connected", "Cannot send messages while offline. Please ensure the Heronix-Talk server is running.");
            return;
        }

        // Clear input and draft
        messageInputArea.clear();
        if (selectedChannel != null && selectedChannel.getId() != null) {
            channelDrafts.remove(selectedChannel.getId());
        }

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
                            // Refresh channels and select the DM channel
                            communicationService.loadChannels()
                                    .thenAccept(allChannels -> Platform.runLater(() -> {
                                        // Find and select the newly created/retrieved DM channel
                                        selectChannelById(channel.getId());
                                    }));
                        });
                    }
                });
    }

    /**
     * Select a channel by its ID
     */
    private void selectChannelById(Long channelId) {
        if (channelId == null) return;

        for (int i = 0; i < channels.size(); i++) {
            if (channelId.equals(channels.get(i).getId())) {
                channelsList.getSelectionModel().select(i);
                channelsList.scrollTo(i);
                log.info("Auto-selected channel with ID: {}", channelId);
                break;
            }
        }
    }

    /**
     * Add a message bubble to the chat display.
     * Messages are inserted in chronological order based on timestamp.
     */
    private void addMessageBubble(TalkMessageDTO message) {
        if (message == null || message.getId() == null) return;

        // Check if message already exists
        if (messageBubbleMap.containsKey(message.getId())) return;

        boolean isOwnMessage = currentUserId != null && currentUserId.equals(message.getSenderId());
        MessageBubble bubble = new MessageBubble(message, isOwnMessage, currentUserId);

        // Wire up callbacks
        bubble.setOnReply(this::handleReplyToMessage);
        bubble.setOnReaction(this::handleReactionToggle);
        bubble.setOnEdit(this::handleEditMessage);
        bubble.setOnDelete(this::handleDeleteMessage);
        bubble.setOnPin(this::handlePinMessage);
        bubble.setOnViewReplies(this::handleViewReplies);
        bubble.setOnForward(this::handleForwardMessage);

        // Wrap in alignment container
        HBox wrapper = new HBox();
        wrapper.setPadding(new Insets(4, 10, 4, 10));
        // Store the message ID and timestamp on the wrapper for ordering
        wrapper.setUserData(message);
        if (isOwnMessage) {
            wrapper.setAlignment(Pos.CENTER_RIGHT);
        } else {
            wrapper.setAlignment(Pos.CENTER_LEFT);
        }
        wrapper.getChildren().add(bubble);

        messageBubbleMap.put(message.getId(), bubble);

        // Insert message in correct position based on timestamp (chronological order)
        int insertIndex = findInsertPosition(message.getTimestamp());
        if (insertIndex >= chatMessagesContainer.getChildren().size()) {
            chatMessagesContainer.getChildren().add(wrapper);
        } else {
            chatMessagesContainer.getChildren().add(insertIndex, wrapper);
        }

        // Force layout update and scroll to bottom to ensure new message is visible
        chatMessagesContainer.applyCss();
        chatMessagesContainer.layout();
        Platform.runLater(() -> chatScrollPane.setVvalue(1.0));
    }

    /**
     * Find the correct insertion position for a message based on its timestamp.
     * Returns the index where the message should be inserted to maintain chronological order.
     */
    private int findInsertPosition(LocalDateTime timestamp) {
        if (timestamp == null) {
            // No timestamp, add at the end
            return chatMessagesContainer.getChildren().size();
        }

        var children = chatMessagesContainer.getChildren();
        for (int i = children.size() - 1; i >= 0; i--) {
            var node = children.get(i);
            if (node.getUserData() instanceof TalkMessageDTO existingMsg) {
                LocalDateTime existingTime = existingMsg.getTimestamp();
                if (existingTime != null && !timestamp.isBefore(existingTime)) {
                    // Insert after this message
                    return i + 1;
                }
            }
        }

        // This message is the oldest, but we may have system messages at the start
        // Find the first message bubble (skip system labels)
        for (int i = 0; i < children.size(); i++) {
            if (children.get(i) instanceof HBox) {
                return i;
            }
        }

        return children.size();
    }

    /**
     * Legacy method for backward compatibility - creates a minimal TalkMessageDTO
     */
    private void addChatMessage(String sender, String message, LocalDateTime time) {
        TalkMessageDTO dto = new TalkMessageDTO();
        dto.setId(System.currentTimeMillis()); // Temporary ID
        dto.setSenderName(sender);
        dto.setContent(message);
        dto.setTimestamp(time);
        addMessageBubble(dto);
    }

    /**
     * Mark a pending message as failed to send
     */
    private void markMessageAsFailed(long tempId) {
        MessageBubble bubble = messageBubbleMap.get(tempId);
        if (bubble != null) {
            bubble.setStyle(bubble.getStyle() + "-fx-opacity: 0.6;");
            // Add a failed indicator to the bubble
            Label failedLabel = new Label(" (failed to send)");
            failedLabel.setStyle("-fx-text-fill: #e53935; -fx-font-size: 10px;");
            bubble.getChildren().add(failedLabel);
        }
    }

    /**
     * Remove a pending message bubble (used when server echo arrives with real ID)
     */
    private void removePendingMessage(long tempId) {
        MessageBubble bubble = messageBubbleMap.remove(tempId);
        if (bubble != null) {
            // Find and remove the wrapper HBox containing this bubble
            chatMessagesContainer.getChildren().removeIf(node -> {
                if (node instanceof HBox wrapper) {
                    return wrapper.getChildren().contains(bubble);
                }
                return false;
            });
        }
    }

    // ==================== Message Feature Handlers ====================

    /**
     * Handle reply to message
     */
    private void handleReplyToMessage(TalkMessageDTO message) {
        replyToMessage = message;
        showReplyPreview();
        messageInputArea.requestFocus();
        log.debug("Replying to message {} from {}", message.getId(), message.getSenderName());
    }

    /**
     * Show reply preview bar above input
     */
    private void showReplyPreview() {
        if (replyPreviewBar == null || replyToMessage == null) return;

        // Update the FXML-defined reply preview bar
        replyPreviewSender.setText("Replying to " + replyToMessage.getSenderName());

        String preview = replyToMessage.getContent();
        if (preview != null && preview.length() > 60) {
            preview = preview.substring(0, 57) + "...";
        }
        replyPreviewContent.setText(preview);

        // Show the reply preview bar
        replyPreviewBar.setVisible(true);
        replyPreviewBar.setManaged(true);
    }

    /**
     * Cancel reply mode (called from FXML)
     */
    @FXML
    private void handleCancelReply() {
        cancelReply();
    }

    /**
     * Cancel reply mode
     */
    private void cancelReply() {
        replyToMessage = null;
        if (replyPreviewBar != null) {
            replyPreviewBar.setVisible(false);
            replyPreviewBar.setManaged(false);
        }
    }

    /**
     * Handle reaction toggle
     */
    private void handleReactionToggle(TalkMessageDTO message, String emoji) {
        if (message.getId() == null || !communicationService.isConnected()) return;

        log.debug("Toggling reaction {} on message {}", emoji, message.getId());
        communicationService.toggleReaction(message.getId(), emoji)
                .thenAccept(reactions -> Platform.runLater(() -> {
                    if (reactions != null) {
                        // Update the message bubble
                        MessageBubble bubble = messageBubbleMap.get(message.getId());
                        if (bubble != null) {
                            bubble.updateReactions(reactions);
                        }
                        log.debug("Reactions updated for message {}", message.getId());
                    }
                }));
    }

    /**
     * Handle edit message
     */
    private void handleEditMessage(TalkMessageDTO message) {
        TextInputDialog dialog = new TextInputDialog(message.getContent());
        dialog.setTitle("Edit Message");
        dialog.setHeaderText("Edit your message");
        dialog.setContentText("Message:");

        dialog.showAndWait().ifPresent(newContent -> {
            if (!newContent.trim().isEmpty() && !newContent.equals(message.getContent())) {
                communicationService.editMessage(message.getId(), newContent.trim())
                        .thenAccept(updatedMessage -> Platform.runLater(() -> {
                            if (updatedMessage != null) {
                                // Reload the channel to show edited message
                                if (selectedChannel != null) {
                                    loadChannelChat(selectedChannel);
                                }
                                log.info("Message {} edited successfully", message.getId());
                            } else {
                                showAlert("Error", "Failed to edit message. Please try again.");
                            }
                        }));
            }
        });
    }

    /**
     * Handle delete message
     */
    private void handleDeleteMessage(TalkMessageDTO message) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Delete Message");
        confirm.setHeaderText("Are you sure you want to delete this message?");
        confirm.setContentText("This action cannot be undone.");

        confirm.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                communicationService.deleteMessage(message.getId())
                        .thenAccept(success -> Platform.runLater(() -> {
                            if (success) {
                                // Reload the channel to show deleted state
                                if (selectedChannel != null) {
                                    loadChannelChat(selectedChannel);
                                }
                                log.info("Message {} deleted successfully", message.getId());
                            } else {
                                showAlert("Error", "Failed to delete message. Please try again.");
                            }
                        }));
            }
        });
    }

    /**
     * Handle pin/unpin message
     */
    private void handlePinMessage(TalkMessageDTO message, boolean pin) {
        communicationService.pinMessage(message.getId(), pin)
                .thenAccept(result -> Platform.runLater(() -> {
                    // Reload to show pin status
                    if (selectedChannel != null) {
                        loadChannelChat(selectedChannel);
                    }
                    log.info("Message {} {} successfully", message.getId(), pin ? "pinned" : "unpinned");
                }));
    }

    /**
     * Handle view replies to a message
     */
    private void handleViewReplies(TalkMessageDTO message) {
        if (message.getId() == null || !communicationService.isConnected()) return;

        communicationService.getReplies(message.getId())
                .thenAccept(replies -> Platform.runLater(() -> {
                    if (replies == null || replies.isEmpty()) {
                        showAlert("No Replies", "This message has no replies.");
                        return;
                    }

                    // Show replies in a dialog
                    Dialog<Void> dialog = new Dialog<>();
                    dialog.setTitle("Replies to " + message.getSenderName());
                    dialog.setHeaderText("Thread with " + replies.size() + " replies");

                    VBox content = new VBox(10);
                    content.setPadding(new Insets(15));
                    content.setPrefWidth(450);
                    content.setMaxHeight(400);

                    ScrollPane scrollPane = new ScrollPane();
                    VBox repliesContainer = new VBox(8);
                    repliesContainer.setPadding(new Insets(5));

                    for (TalkMessageDTO reply : replies) {
                        boolean isOwn = currentUserId != null && currentUserId.equals(reply.getSenderId());
                        MessageBubble replyBubble = new MessageBubble(reply, isOwn, currentUserId);
                        repliesContainer.getChildren().add(replyBubble);
                    }

                    scrollPane.setContent(repliesContainer);
                    scrollPane.setFitToWidth(true);
                    content.getChildren().add(scrollPane);

                    dialog.getDialogPane().setContent(content);
                    dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
                    dialog.showAndWait();
                }));
    }

    /**
     * Handle forwarding a message to another channel
     */
    private void handleForwardMessage(TalkMessageDTO message) {
        if (message == null || message.getContent() == null) return;

        // Get available channels (excluding current channel) and convert to TalkChannelDTO
        java.util.List<TalkChannelDTO> availableChannels = channels.stream()
                .filter(ch -> selectedChannel == null || !ch.getId().equals(selectedChannel.getId()))
                .map(ch -> TalkChannelDTO.builder()
                        .id(ch.getId())
                        .name(ch.getName())
                        .channelType(ch.getType())
                        .memberCount(ch.getMemberCount())
                        .build())
                .collect(java.util.stream.Collectors.toList());

        if (availableChannels.isEmpty()) {
            showAlert("Forward Message", "No other channels available to forward to.");
            return;
        }

        ForwardMessageDialog dialog = new ForwardMessageDialog(message, availableChannels);
        dialog.showAndGetResult().ifPresent(targetChannel -> {
            // Create forwarded message content
            String forwardedContent = "\u27A1 Forwarded from " + message.getSenderName() + ":\n\n" +
                    message.getContent();

            // Send to the target channel
            communicationService.sendMessage(targetChannel.getId(), forwardedContent)
                    .thenAccept(success -> Platform.runLater(() -> {
                        if (success) {
                            log.info("Message forwarded to channel: {}", targetChannel.getName());
                            showAlert("Message Forwarded",
                                    "Message has been forwarded to " + targetChannel.getName());
                        } else {
                            showAlert("Forward Failed",
                                    "Failed to forward message. Please try again.");
                        }
                    }));
        });

        log.debug("Forward message dialog shown for message: {}", message.getId());
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
        if (!communicationService.isConnected()) {
            showAlert("Not Connected", "Please connect to Heronix-Talk server to create channels.");
            return;
        }

        log.info("Opening create channel dialog");

        // Get available users for member selection
        List<TalkUserDTO> availableUsers = communicationService.getUsers();

        CreateChannelDialog dialog = new CreateChannelDialog(availableUsers);
        Optional<CreateChannelDialog.CreateChannelResult> result = dialog.showAndWait();

        result.ifPresent(channelResult -> {
            log.info("Creating channel: name='{}', type='{}', members={}",
                    channelResult.getName(), channelResult.getChannelType(),
                    channelResult.getMemberIds().size());

            communicationService.createChannel(
                    channelResult.getName(),
                    channelResult.getDescription(),
                    channelResult.getChannelType(),
                    null, // icon
                    channelResult.getMemberIds(),
                    channelResult.isSendInvites()
            ).thenAccept(channel -> Platform.runLater(() -> {
                if (channel != null) {
                    log.info("Channel '{}' created successfully with ID {}", channel.getName(), channel.getId());
                    // Auto-select the newly created channel
                    selectChannelById(channel.getId());
                } else {
                    showAlert("Error", "Failed to create channel. Please try again.");
                }
            }));
        });
    }

    @FXML
    private void handleAttachment() {
        if (selectedChannel == null || selectedChannel.getId() == null) {
            showAlert("No Channel", "Please select a channel first.");
            return;
        }

        if (!communicationService.isConnected()) {
            showAlert("Not Connected", "Please connect to Heronix-Talk server to send files.");
            return;
        }

        // Open file chooser
        javafx.stage.FileChooser fileChooser = new javafx.stage.FileChooser();
        fileChooser.setTitle("Select File to Attach");
        fileChooser.getExtensionFilters().addAll(
                new javafx.stage.FileChooser.ExtensionFilter("All Files", "*.*"),
                new javafx.stage.FileChooser.ExtensionFilter("Images", "*.png", "*.jpg", "*.jpeg", "*.gif", "*.bmp"),
                new javafx.stage.FileChooser.ExtensionFilter("Documents", "*.pdf", "*.doc", "*.docx", "*.xls", "*.xlsx", "*.ppt", "*.pptx"),
                new javafx.stage.FileChooser.ExtensionFilter("Text Files", "*.txt", "*.csv", "*.json", "*.xml")
        );

        java.io.File selectedFile = fileChooser.showOpenDialog(chatScrollPane.getScene().getWindow());

        if (selectedFile != null) {
            // Check file size (10MB limit)
            if (selectedFile.length() > 10 * 1024 * 1024) {
                showAlert("File Too Large", "Maximum file size is 10MB. Selected file is " +
                        String.format("%.1f MB", selectedFile.length() / (1024.0 * 1024)));
                return;
            }

            // Ask for optional caption
            String caption = messageInputArea.getText().trim();
            if (!caption.isEmpty()) {
                Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
                confirm.setTitle("Include Message");
                confirm.setHeaderText("Include the message as a caption?");
                confirm.setContentText("Message: " + (caption.length() > 50 ? caption.substring(0, 47) + "..." : caption));

                ButtonType yesButton = new ButtonType("Yes, include as caption");
                ButtonType noButton = new ButtonType("No, send file only");
                ButtonType cancelButton = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
                confirm.getButtonTypes().setAll(yesButton, noButton, cancelButton);

                Optional<ButtonType> result = confirm.showAndWait();
                if (result.isEmpty() || result.get() == cancelButton) {
                    return;
                }
                if (result.get() == noButton) {
                    caption = null;
                }
            } else {
                caption = null;
            }

            // Upload file
            String finalCaption = caption;
            addSystemMessage("Uploading " + selectedFile.getName() + "...");

            communicationService.uploadFile(selectedChannel.getId(), selectedFile, finalCaption)
                    .thenAccept(message -> Platform.runLater(() -> {
                        if (message != null) {
                            messageInputArea.clear();
                            log.info("File {} uploaded successfully", selectedFile.getName());
                        } else {
                            showAlert("Upload Failed", "Failed to upload file. Please try again.");
                        }
                    }));
        }
    }

    @FXML
    private void handleEmoji() {
        // Get the emoji button position for popup placement
        javafx.scene.Node emojiBtn = messageInputArea.getScene().lookup(".chat-attachment-button");
        if (emojiBtn == null) {
            emojiBtn = messageInputArea;
        }

        // Calculate popup position (above the input area)
        javafx.geometry.Bounds bounds = emojiBtn.localToScreen(emojiBtn.getBoundsInLocal());
        double x = bounds.getMinX();
        double y = bounds.getMinY() - 360; // Position above the button

        // Create and show emoji picker
        EmojiPickerPopup emojiPicker = new EmojiPickerPopup();
        emojiPicker.setOnEmojiSelected(emoji -> {
            // Insert emoji at cursor position in message input
            int caretPos = messageInputArea.getCaretPosition();
            String currentText = messageInputArea.getText();
            String newText = currentText.substring(0, caretPos) + emoji + currentText.substring(caretPos);
            messageInputArea.setText(newText);
            messageInputArea.positionCaret(caretPos + emoji.length());
            messageInputArea.requestFocus();
        });

        emojiPicker.showNear(messageInputArea.getScene().getWindow(), x, y);
        log.debug("Emoji picker opened");
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
        log.debug("Opening chat settings dialog");

        ChatSettingsDialog dialog = new ChatSettingsDialog(currentChatSettings);
        dialog.initOwner(rootPane.getScene().getWindow());

        Optional<ChatSettingsDialog.ChatSettings> result = dialog.showAndWait();
        result.ifPresent(settings -> {
            currentChatSettings = settings;
            applyChatSettings(settings);
            log.info("Chat settings updated");
        });
    }

    /**
     * Apply chat settings to the UI and services
     */
    private void applyChatSettings(ChatSettingsDialog.ChatSettings settings) {
        // Apply sound settings
        if (settings.isSoundEnabled() != communicationService.isSoundEnabled()) {
            communicationService.toggleSound();
            updateSoundToggleButton(settings.isSoundEnabled());
        }

        // Apply other settings as needed
        log.debug("Applied settings - Sound: {}, Density: {}, Enter to send: {}",
                settings.isSoundEnabled(),
                settings.getMessageDensity(),
                settings.isEnterToSend());
    }

    // ==================== Invitation Handling ====================

    /**
     * Handle incoming invitation notification from WebSocket
     */
    private void handleIncomingInvitation(TalkChannelInvitationDTO invitation) {
        Platform.runLater(() -> {
            log.info("New invitation received for channel: {}", invitation.getChannelName());
            pendingInvitationCount++;
            updateInvitationBadge();

            // Show a notification
            Alert notification = new Alert(Alert.AlertType.INFORMATION);
            notification.setTitle("New Channel Invitation");
            notification.setHeaderText("You've been invited to join a channel");
            notification.setContentText(invitation.getInviterName() + " invited you to join \"" +
                    invitation.getChannelName() + "\"");
            notification.show();
        });
    }

    /**
     * Refresh the invitation badge by fetching count from server
     */
    private void refreshInvitationBadge() {
        if (!communicationService.isConnected()) return;

        communicationService.getPendingInvitationCount()
                .thenAccept(count -> Platform.runLater(() -> {
                    pendingInvitationCount = count.intValue();
                    updateInvitationBadge();
                }));
    }

    /**
     * Update the invitation badge UI
     */
    private void updateInvitationBadge() {
        if (invitationBadgeBtn != null) {
            if (pendingInvitationCount > 0) {
                invitationBadgeBtn.setText("\u2709 " + pendingInvitationCount);
                invitationBadgeBtn.setStyle("-fx-background-color: #FF5722; -fx-text-fill: white; " +
                        "-fx-font-size: 11px; -fx-background-radius: 4;");
                invitationBadgeBtn.setVisible(true);
                invitationBadgeBtn.setManaged(true);
            } else {
                invitationBadgeBtn.setVisible(false);
                invitationBadgeBtn.setManaged(false);
            }
        }
    }

    /**
     * Show the invitations dialog
     */
    @FXML
    private void handleShowInvitations() {
        if (!communicationService.isConnected()) {
            showAlert("Not Connected", "Please connect to Heronix-Talk server to view invitations.");
            return;
        }

        log.info("Opening invitations dialog");

        // Fetch pending invitations
        communicationService.getPendingInvitations()
                .thenAccept(invitations -> Platform.runLater(() -> {
                    InvitationDialog dialog = new InvitationDialog(invitations);

                    // Set up accept callback
                    dialog.setOnAccept(invitation -> {
                        log.info("Accepting invitation {} for channel {}", invitation.getId(), invitation.getChannelName());
                        communicationService.acceptInvitation(invitation.getId())
                                .thenAccept(result -> Platform.runLater(() -> {
                                    if (result != null) {
                                        dialog.removeInvitation(invitation.getId());
                                        pendingInvitationCount = Math.max(0, pendingInvitationCount - 1);
                                        updateInvitationBadge();
                                        // Auto-select the joined channel
                                        selectChannelById(invitation.getChannelId());
                                    } else {
                                        showAlert("Error", "Failed to accept invitation. Please try again.");
                                    }
                                }));
                    });

                    // Set up decline callback
                    dialog.setOnDecline(invitation -> {
                        log.info("Declining invitation {} for channel {}", invitation.getId(), invitation.getChannelName());
                        communicationService.declineInvitation(invitation.getId())
                                .thenAccept(result -> Platform.runLater(() -> {
                                    if (result != null) {
                                        dialog.removeInvitation(invitation.getId());
                                        pendingInvitationCount = Math.max(0, pendingInvitationCount - 1);
                                        updateInvitationBadge();
                                    } else {
                                        showAlert("Error", "Failed to decline invitation. Please try again.");
                                    }
                                }));
                    });

                    dialog.showAndWait();

                    // Refresh badge after dialog closes
                    refreshInvitationBadge();
                }));
    }

    // ==================== Sound Control ====================

    /**
     * Toggle notification sounds on/off
     */
    @FXML
    private void handleToggleSound() {
        boolean soundEnabled = communicationService.toggleSound();
        updateSoundToggleButton(soundEnabled);
        log.info("Notification sounds {}", soundEnabled ? "enabled" : "disabled");
    }

    /**
     * Update the sound toggle button appearance
     */
    private void updateSoundToggleButton(boolean soundEnabled) {
        if (soundToggleBtn != null) {
            if (soundEnabled) {
                soundToggleBtn.setText("\uD83D\uDD0A"); // Speaker with sound
                soundToggleBtn.setStyle("");
            } else {
                soundToggleBtn.setText("\uD83D\uDD07"); // Speaker muted
                soundToggleBtn.setStyle("-fx-text-fill: gray;");
            }
        }
    }

    // ==================== Search and Pin Handlers ====================

    /**
     * Handle search messages button
     */
    @FXML
    private void handleSearchMessages() {
        if (!communicationService.isConnected()) {
            showAlert("Not Connected", "Please connect to Heronix-Talk server to search messages.");
            return;
        }

        MessageSearchDialog dialog = new MessageSearchDialog();

        dialog.setOnSearch(query -> {
            Long channelId = selectedChannel != null ? selectedChannel.getId() : null;
            communicationService.searchMessages(query, channelId)
                    .thenAccept(results -> Platform.runLater(() -> {
                        dialog.updateResults(results);
                    }));
        });

        Optional<TalkMessageDTO> result = dialog.showAndWait();
        result.ifPresent(message -> {
            // Navigate to the message's channel and scroll to it
            if (message.getChannelId() != null) {
                selectChannelById(message.getChannelId());
                // Scroll to the specific message after a brief delay to allow channel messages to load
                Platform.runLater(() -> {
                    // Use another runLater to ensure messages are rendered
                    Platform.runLater(() -> scrollToMessage(message.getId()));
                });
                log.info("Navigating to message {} in channel {}", message.getId(), message.getChannelId());
            }
        });
    }

    /**
     * Scroll the chat view to a specific message and highlight it
     */
    private void scrollToMessage(Long messageId) {
        if (messageId == null) return;

        MessageBubble bubble = messageBubbleMap.get(messageId);
        if (bubble == null) {
            log.debug("Message {} not found in current view, cannot scroll to it", messageId);
            return;
        }

        // Find the wrapper HBox containing the bubble
        javafx.scene.Node targetNode = bubble.getParent();
        if (targetNode == null) {
            targetNode = bubble;
        }

        // Calculate scroll position
        double contentHeight = chatMessagesContainer.getHeight();
        double viewportHeight = chatScrollPane.getViewportBounds().getHeight();

        if (contentHeight > viewportHeight) {
            // Get the Y position of the message relative to the container
            double messageY = bubble.getBoundsInParent().getMinY();
            // Center the message in the viewport
            double scrollPosition = (messageY - viewportHeight / 2) / (contentHeight - viewportHeight);
            // Clamp between 0 and 1
            scrollPosition = Math.max(0, Math.min(1, scrollPosition));
            chatScrollPane.setVvalue(scrollPosition);
        }

        // Highlight the message temporarily
        highlightMessage(bubble);
        log.debug("Scrolled to message {}", messageId);
    }

    /**
     * Temporarily highlight a message bubble to draw attention to it
     */
    private void highlightMessage(MessageBubble bubble) {
        // Add highlight style
        bubble.getStyleClass().add("message-highlight");

        // Remove highlight after 2 seconds
        new Timer().schedule(new TimerTask() {
            @Override
            public void run() {
                Platform.runLater(() -> bubble.getStyleClass().remove("message-highlight"));
            }
        }, 2000);
    }

    /**
     * Handle show pinned messages button
     */
    @FXML
    private void handleShowPinnedMessages() {
        if (selectedChannel == null || selectedChannel.getId() == null) {
            showAlert("No Channel", "Please select a channel first.");
            return;
        }

        if (!communicationService.isConnected()) {
            showAlert("Not Connected", "Please connect to Heronix-Talk server to view pinned messages.");
            return;
        }

        communicationService.getPinnedMessages(selectedChannel.getId())
                .thenAccept(pinnedMessages -> Platform.runLater(() -> {
                    if (pinnedMessages == null || pinnedMessages.isEmpty()) {
                        showAlert("No Pinned Messages", "This channel has no pinned messages.");
                        return;
                    }

                    // Show pinned messages in a dialog
                    Dialog<Void> dialog = new Dialog<>();
                    dialog.setTitle("Pinned Messages");
                    dialog.setHeaderText(pinnedMessages.size() + " pinned messages in " + selectedChannel.getName());

                    VBox content = new VBox(10);
                    content.setPadding(new Insets(15));
                    content.setPrefWidth(500);

                    ScrollPane scrollPane = new ScrollPane();
                    scrollPane.setFitToWidth(true);
                    scrollPane.setPrefHeight(400);

                    VBox messagesContainer = new VBox(8);
                    messagesContainer.setPadding(new Insets(5));

                    for (TalkMessageDTO msg : pinnedMessages) {
                        boolean isOwn = currentUserId != null && currentUserId.equals(msg.getSenderId());
                        MessageBubble bubble = new MessageBubble(msg, isOwn, currentUserId);
                        bubble.setOnPin((m, pin) -> {
                            handlePinMessage(m, pin);
                            dialog.close();
                        });
                        messagesContainer.getChildren().add(bubble);
                    }

                    scrollPane.setContent(messagesContainer);
                    content.getChildren().add(scrollPane);

                    dialog.getDialogPane().setContent(content);
                    dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
                    dialog.showAndWait();
                }));
    }

    /**
     * Handle show channel members button
     */
    @FXML
    private void handleShowMembers() {
        if (selectedChannel == null || selectedChannel.getId() == null) {
            showAlert("No Channel", "Please select a channel first.");
            return;
        }

        if (!communicationService.isConnected()) {
            showAlert("Not Connected", "Please connect to Heronix-Talk server to view members.");
            return;
        }

        // Get channel members
        communicationService.getChannelMembers(selectedChannel.getId())
                .thenAccept(members -> Platform.runLater(() -> {
                    TalkChannelDTO channelDTO = new TalkChannelDTO();
                    channelDTO.setId(selectedChannel.getId());
                    channelDTO.setName(selectedChannel.getName());
                    channelDTO.setChannelType(selectedChannel.getType());

                    ChannelMembersDialog dialog = new ChannelMembersDialog(channelDTO, members, currentUserId);

                    // Provide available users for invite functionality
                    dialog.setAvailableUsersSupplier(() -> communicationService.getUsers());

                    dialog.setOnRemoveUser(userId -> {
                        communicationService.removeChannelMember(selectedChannel.getId(), userId)
                                .thenAccept(success -> Platform.runLater(() -> {
                                    if (success) {
                                        log.info("Removed user {} from channel", userId);
                                        // Refresh members
                                        handleShowMembers();
                                    } else {
                                        showAlert("Error", "Failed to remove user from channel.");
                                    }
                                }));
                    });

                    dialog.setOnInviteUsers(userIds -> {
                        // Send invitations to all selected users
                        for (Long userId : userIds) {
                            communicationService.inviteUserToChannel(selectedChannel.getId(), userId, null)
                                    .thenAccept(success -> Platform.runLater(() -> {
                                        if (success) {
                                            log.info("Invited user {} to channel", userId);
                                        } else {
                                            log.warn("Failed to invite user {} to channel", userId);
                                        }
                                    }));
                        }
                        showAlert("Invitations Sent", userIds.size() + " invitation(s) have been sent.");
                    });

                    dialog.showAndWait();
                }));
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
        private final String type;
        private final int memberCount;
        private int unreadCount;

        public ChannelItem(Long id, String icon, String name, int memberCount, int unreadCount) {
            this(id, icon, name, null, memberCount, unreadCount);
        }

        public ChannelItem(Long id, String icon, String name, String type, int memberCount, int unreadCount) {
            this.id = id;
            this.icon = icon;
            this.name = name;
            this.type = type;
            this.memberCount = memberCount;
            this.unreadCount = unreadCount;
        }

        public Long getId() { return id; }
        public String getIcon() { return icon; }
        public String getName() { return name; }
        public String getType() { return type; }
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
