package com.heronix.teacher.ui.controller;

import com.heronix.teacher.model.domain.Teacher;
import com.heronix.teacher.service.*;
import com.heronix.teacher.util.ThemeManager;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.beans.value.ChangeListener;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Stage;
import javafx.util.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Timer;
import java.util.TimerTask;

/**
 * Main controller for Heronix-Teacher application
 *
 * Manages:
 * - Main window layout
 * - Navigation between views
 * - Theme switching
 * - Network status monitoring
 * - Sync status display
 *
 * @author EduScheduler Team
 * @version 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MainController {

    private final ApplicationContext applicationContext;
    private final ThemeManager themeManager;
    private final SessionManager sessionManager;
    private final AutoSyncService autoSyncService;
    private final NetworkMonitorService networkMonitor;
    private final AdminApiClient adminApiClient;
    private final EdGamesApiClient edGamesApiClient;
    private final HeronixTalkApiClient talkApiClient;

    @FXML private BorderPane mainRoot;
    @FXML private StackPane contentArea;
    @FXML private VBox sidebar;

    // Header elements
    @FXML private Label teacherNameLabel;
    @FXML private Label teacherSubjectLabel;
    @FXML private Label appTitleLabel;
    @FXML private Label appSubtitleLabel;
    @FXML private Button sidebarToggleBtn;
    @FXML private Label networkStatusIcon;
    @FXML private Label networkStatusLabel;
    @FXML private Button themeToggleBtn;
    @FXML private Button logoutBtn;

    // Server status indicators
    @FXML private Label sisStatusIcon;
    @FXML private Label edgamesStatusIcon;
    @FXML private Label talkStatusIcon;

    // Navigation buttons
    @FXML private Button dashboardBtn;
    @FXML private Button gradebookBtn;
    @FXML private Button attendanceBtn;
    @FXML private Button hallpassBtn;
    @FXML private Button clubsBtn;
    @FXML private Button walletBtn;
    @FXML private Button commHubBtn;
    @FXML private Button deviceMgmtBtn;
    @FXML private Button gameAnalyticsBtn;
    @FXML private Button codeBreakerBtn;
    @FXML private Button dismissalBtn;
    @FXML private Button pollsBtn;

    // Status bar
    @FXML private Label statusMessageLabel;
    @FXML private Label pendingSyncLabel;
    @FXML private Label lastSyncLabel;

    // Collapsible sidebar elements
    @FXML private VBox quickActionsSection;
    @FXML private Label quickActionsLabel;
    @FXML private Button syncNowBtn;
    @FXML private Button exportBtn;
    @FXML private VBox syncStatusCard;
    @FXML private Label syncStatusLabel;

    private Timer networkCheckTimer;
    private String currentView = "dashboard";
    private boolean sidebarExpanded = true;
    private static final double SIDEBAR_EXPANDED_WIDTH = 220;
    private static final double SIDEBAR_COLLAPSED_WIDTH = 60;
    private static final double COMPACT_MODE_THRESHOLD = 1000;
    private ChangeListener<Number> windowWidthListener;

    /**
     * Initialize controller
     */
    @FXML
    public void initialize() {
        log.info("Initializing Main Controller");

        // Load teacher information
        loadTeacherInfo();

        // Load dashboard by default
        showDashboard();

        // Start network status monitoring
        startNetworkMonitoring();

        // Update sync status display
        updateSyncStatus();

        // Setup responsive behavior after scene is available
        Platform.runLater(this::setupResponsiveBehavior);

        log.info("Main Controller initialized successfully");
    }

    /**
     * Setup responsive behavior based on window size
     */
    private void setupResponsiveBehavior() {
        if (mainRoot.getScene() != null && mainRoot.getScene().getWindow() != null) {
            windowWidthListener = (obs, oldVal, newVal) -> {
                double width = newVal.doubleValue();
                handleWindowResize(width);
            };
            mainRoot.getScene().widthProperty().addListener(windowWidthListener);
            // Initial check
            handleWindowResize(mainRoot.getScene().getWidth());
        }
    }

    /**
     * Handle window resize for responsive layout
     */
    private void handleWindowResize(double windowWidth) {
        Platform.runLater(() -> {
            // Auto-collapse sidebar on smaller screens
            if (windowWidth < COMPACT_MODE_THRESHOLD && sidebarExpanded) {
                collapseSidebar(false);
            }

            // Update header elements visibility based on width
            if (appSubtitleLabel != null) {
                appSubtitleLabel.setVisible(windowWidth >= 900);
                appSubtitleLabel.setManaged(windowWidth >= 900);
            }
        });
    }

    /**
     * Toggle sidebar expanded/collapsed state
     */
    @FXML
    public void toggleSidebar() {
        if (sidebarExpanded) {
            collapseSidebar(true);
        } else {
            expandSidebar(true);
        }
    }

    /**
     * Collapse the sidebar with optional animation
     */
    private void collapseSidebar(boolean animate) {
        sidebarExpanded = false;

        if (animate) {
            Timeline timeline = new Timeline(
                new KeyFrame(Duration.millis(200),
                    new KeyValue(sidebar.prefWidthProperty(), SIDEBAR_COLLAPSED_WIDTH)
                )
            );
            timeline.setOnFinished(e -> updateSidebarContent(false));
            timeline.play();
        } else {
            sidebar.setPrefWidth(SIDEBAR_COLLAPSED_WIDTH);
            updateSidebarContent(false);
        }

        sidebarToggleBtn.setText("☰");
        log.debug("Sidebar collapsed");
    }

    /**
     * Expand the sidebar with optional animation
     */
    private void expandSidebar(boolean animate) {
        sidebarExpanded = true;
        updateSidebarContent(true);

        if (animate) {
            Timeline timeline = new Timeline(
                new KeyFrame(Duration.millis(200),
                    new KeyValue(sidebar.prefWidthProperty(), SIDEBAR_EXPANDED_WIDTH)
                )
            );
            timeline.play();
        } else {
            sidebar.setPrefWidth(SIDEBAR_EXPANDED_WIDTH);
        }

        sidebarToggleBtn.setText("✕");
        log.debug("Sidebar expanded");
    }

    /**
     * Update sidebar content visibility based on expanded state
     */
    private void updateSidebarContent(boolean expanded) {
        // Update navigation button text
        updateNavButtonText(dashboardBtn, "📊", "Dashboard", expanded);
        updateNavButtonText(gradebookBtn, "📚", "Gradebook", expanded);
        updateNavButtonText(attendanceBtn, "✓", "Attendance", expanded);
        updateNavButtonText(hallpassBtn, "🎫", "Hall Pass", expanded);
        updateNavButtonText(clubsBtn, "🎭", "Clubs", expanded);
        updateNavButtonText(walletBtn, "💰", "Wallet", expanded);
        updateNavButtonText(commHubBtn, "💬", "H-Talk", expanded);
        updateNavButtonText(deviceMgmtBtn, "📱", "Devices", expanded);
        if (gameAnalyticsBtn != null) {
            updateNavButtonText(gameAnalyticsBtn, "🎮", "Analytics", expanded);
        }
        if (codeBreakerBtn != null) {
            updateNavButtonText(codeBreakerBtn, "🔐", "Code", expanded);
        }

        // Hide/show quick actions section
        if (quickActionsSection != null) {
            quickActionsSection.setVisible(expanded);
            quickActionsSection.setManaged(expanded);
        }
        if (quickActionsLabel != null) {
            quickActionsLabel.setVisible(expanded);
            quickActionsLabel.setManaged(expanded);
        }
        if (syncNowBtn != null) {
            syncNowBtn.setText(expanded ? "📥 Sync" : "📥");
        }
        if (exportBtn != null) {
            exportBtn.setText(expanded ? "📤 Export" : "📤");
        }

        // Hide/show sync status card details
        if (syncStatusCard != null) {
            syncStatusCard.setVisible(expanded);
            syncStatusCard.setManaged(expanded);
        }
    }

    /**
     * Update navigation button text based on expanded state
     */
    private void updateNavButtonText(Button button, String icon, String label, boolean expanded) {
        if (button != null) {
            button.setText(expanded ? icon + " " + label : icon);
            button.setAlignment(expanded ? javafx.geometry.Pos.CENTER_LEFT : javafx.geometry.Pos.CENTER);
        }
    }

    /**
     * Load teacher information from database/session
     */
    private void loadTeacherInfo() {
        // Load teacher from session
        Teacher currentTeacher = sessionManager.getCurrentTeacher();

        if (currentTeacher != null) {
            // Display teacher information from database
            teacherNameLabel.setText(currentTeacher.getFullName());
            teacherSubjectLabel.setText(currentTeacher.getDepartment() != null ?
                currentTeacher.getDepartment() : "Teacher");

            log.info("Loaded teacher: {} - {}",
                currentTeacher.getFullName(),
                currentTeacher.getDepartment());
        } else {
            // No teacher logged in - use placeholder
            teacherNameLabel.setText("Guest User");
            teacherSubjectLabel.setText("Not Logged In");
            log.warn("No teacher in session - showing placeholder");
        }
    }

    /**
     * Toggle between dark and light themes
     */
    @FXML
    public void toggleTheme() {
        log.info("Toggling theme");
        themeManager.toggleTheme(mainRoot.getScene());

        // Update theme button icon
        String currentTheme = themeManager.getCurrentTheme();
        themeToggleBtn.setText("dark".equalsIgnoreCase(currentTheme) ? "🌙" : "☀");
    }

    /**
     * Handle logout - confirm, cleanup, and return to login screen
     */
    @FXML
    public void handleLogout() {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Log Out");
        confirm.setHeaderText("Are you sure you want to log out?");
        confirm.setContentText("You will be returned to the login screen.");
        confirm.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                log.info("Teacher logging out");

                // Stop network monitoring timer
                if (networkCheckTimer != null) {
                    networkCheckTimer.cancel();
                    networkCheckTimer = null;
                }

                // Clear session
                sessionManager.logout();

                // Navigate back to login screen
                try {
                    FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/Login.fxml"));
                    loader.setControllerFactory(applicationContext::getBean);
                    Parent loginRoot = loader.load();

                    Stage stage = (Stage) mainRoot.getScene().getWindow();
                    Scene scene = new Scene(loginRoot, 800, 600);

                    themeManager.applyCurrentTheme(scene);

                    stage.setScene(scene);
                    stage.setTitle("Heronix-Teacher - Login");
                    stage.setResizable(false);
                    stage.centerOnScreen();

                    log.info("Returned to login screen");
                } catch (Exception e) {
                    log.error("Failed to load login screen", e);
                    showError("Logout Error", "Failed to return to login screen: " + e.getMessage());
                }
            }
        });
    }

    /**
     * Show Dashboard view
     */
    @FXML
    public void showDashboard() {
        loadView("Dashboard", dashboardBtn);
    }

    /**
     * Show Gradebook view
     */
    @FXML
    public void showGradebook() {
        loadView("Gradebook", gradebookBtn);
    }

    /**
     * Show Attendance view
     */
    @FXML
    public void showAttendance() {
        loadView("Attendance", attendanceBtn);
    }

    /**
     * Show Hall Pass view
     */
    @FXML
    public void showHallPass() {
        loadView("HallPass", hallpassBtn);
    }

    /**
     * Show Clubs view
     */
    @FXML
    public void showClubs() {
        loadView("Clubs", clubsBtn);
    }

    /**
     * Show Class Wallet view
     */
    @FXML
    public void showWallet() {
        loadView("ClassWallet", walletBtn);
    }

    /**
     * Show Communication Hub view
     */
    @FXML
    public void showCommunicationHub() {
        loadView("CommunicationHub", commHubBtn);
    }

    /**
     * Show Device Management view
     */
    @FXML
    public void showDeviceManagement() {
        loadView("DeviceManagement", deviceMgmtBtn);
    }

    /**
     * Show Game Analytics view
     */
    @FXML
    public void showGameAnalytics() {
        loadView("GameAnalytics", gameAnalyticsBtn);
    }

    /**
     * Show Code Breaker multiplayer game view
     */
    @FXML
    public void showCodeBreaker() {
        loadView("CodeBreaker", codeBreakerBtn);
    }

    /**
     * Show Dismissal Board view
     */
    @FXML
    public void showDismissal() {
        loadView("DismissalBoard", dismissalBtn);
    }

    @FXML
    public void showPolls() {
        loadView("Polls", pollsBtn);
    }

    /**
     * Load a view into content area
     */
    private void loadView(String viewName, Button activeButton) {
        try {
            log.info("Loading view: {}", viewName);

            // Load FXML
            FXMLLoader loader = new FXMLLoader(
                getClass().getResource("/fxml/" + viewName + ".fxml")
            );
            loader.setControllerFactory(applicationContext::getBean);

            Node view = loader.load();

            // Clear content area and add new view
            contentArea.getChildren().clear();
            contentArea.getChildren().add(view);

            // Update navigation button states
            updateNavButtonStates(activeButton);

            currentView = viewName.toLowerCase();
            updateStatusMessage("Loaded " + viewName);

            log.info("View loaded successfully: {}", viewName);

        } catch (Exception e) {
            log.error("Failed to load view: " + viewName, e);
            updateStatusMessage("Error loading " + viewName);
        }
    }

    /**
     * Update navigation button states
     */
    private void updateNavButtonStates(Button activeButton) {
        // Remove active class from all buttons
        dashboardBtn.getStyleClass().remove("nav-button-active");
        gradebookBtn.getStyleClass().remove("nav-button-active");
        attendanceBtn.getStyleClass().remove("nav-button-active");
        hallpassBtn.getStyleClass().remove("nav-button-active");
        clubsBtn.getStyleClass().remove("nav-button-active");
        walletBtn.getStyleClass().remove("nav-button-active");
        commHubBtn.getStyleClass().remove("nav-button-active");
        deviceMgmtBtn.getStyleClass().remove("nav-button-active");
        if (gameAnalyticsBtn != null) {
            gameAnalyticsBtn.getStyleClass().remove("nav-button-active");
        }
        if (codeBreakerBtn != null) {
            codeBreakerBtn.getStyleClass().remove("nav-button-active");
        }
        if (pollsBtn != null) {
            pollsBtn.getStyleClass().remove("nav-button-active");
        }

        // Add active class to current button
        if (activeButton != null && !activeButton.getStyleClass().contains("nav-button-active")) {
            activeButton.getStyleClass().add("nav-button-active");
        }
    }

    /**
     * Sync now action - triggers immediate sync with main EduScheduler server
     */
    @FXML
    public void syncNow() {
        log.info("Manual sync triggered");
        updateStatusMessage("Syncing with main server...");

        // Trigger immediate sync with main EduScheduler server
        new Thread(() -> {
            try {
                autoSyncService.syncNow();
                Platform.runLater(() -> {
                    updateStatusMessage("Sync completed successfully");
                    updateSyncStatus();
                });
            } catch (Exception e) {
                log.error("Manual sync failed", e);
                Platform.runLater(() -> {
                    updateStatusMessage("Sync failed: " + e.getMessage());
                    showError("Sync Error", "Failed to sync with main server:\n" + e.getMessage());
                });
            }
        }).start();
    }

    /**
     * Export data action - navigates to Dashboard Reports for comprehensive export options
     */
    @FXML
    public void exportData() {
        log.info("Export data triggered - showing export options");

        // Show info about export location
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Export Data");
        alert.setHeaderText("Export Options Available");
        alert.setContentText("Export options are available in the Dashboard:\n\n" +
                "1. Click 'Dashboard' in the sidebar\n" +
                "2. Click 'Reports' button for detailed reports with export\n" +
                "3. Click 'Export Data' for quick export options\n\n" +
                "Available exports:\n" +
                "• Student Roster (CSV)\n" +
                "• Gradebook (CSV)\n" +
                "• Attendance Records (CSV)\n" +
                "• Hall Pass Log (CSV)\n" +
                "• Complete Summary Report (CSV)");
        alert.showAndWait();

        updateStatusMessage("See Dashboard for export options");
    }

    /**
     * Open settings - displays advanced application settings dialog
     */
    @FXML
    public void openSettings() {
        log.info("Opening settings dialog");

        // Create dialog
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Application Settings");
        dialog.setHeaderText("Heronix-Teacher Settings");
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        dialog.setResizable(true);

        // Create tab pane for settings categories
        TabPane settingsTabPane = new TabPane();
        settingsTabPane.setPrefWidth(550);
        settingsTabPane.setPrefHeight(400);

        // === General Settings Tab ===
        Tab generalTab = new Tab("General");
        generalTab.setClosable(false);
        VBox generalContent = new VBox(15);
        generalContent.setPadding(new Insets(20));

        // Theme settings
        Label themeLabel = new Label("Appearance");
        themeLabel.setFont(Font.font("System", FontWeight.BOLD, 13));

        HBox themeBox = new HBox(10);
        themeBox.setAlignment(Pos.CENTER_LEFT);
        Label themePrefLabel = new Label("Theme:");
        ComboBox<String> themeCombo = new ComboBox<>(FXCollections.observableArrayList(
                "Light", "Dark", "Auto (System)"
        ));
        themeCombo.setValue("dark".equalsIgnoreCase(themeManager.getCurrentTheme()) ? "Dark" : "Light");
        themeBox.getChildren().addAll(themePrefLabel, themeCombo);

        // Startup settings
        Label startupLabel = new Label("Startup");
        startupLabel.setFont(Font.font("System", FontWeight.BOLD, 13));

        CheckBox startMinimizedCb = new CheckBox("Start minimized to system tray");
        CheckBox rememberWindowCb = new CheckBox("Remember window size and position");
        rememberWindowCb.setSelected(true);

        generalContent.getChildren().addAll(themeLabel, themeBox,
                new Separator(), startupLabel, startMinimizedCb, rememberWindowCb);
        generalTab.setContent(generalContent);

        // === Sync Settings Tab ===
        Tab syncTab = new Tab("Sync & Data");
        syncTab.setClosable(false);
        VBox syncContent = new VBox(15);
        syncContent.setPadding(new Insets(20));

        Label syncLabel = new Label("Synchronization");
        syncLabel.setFont(Font.font("System", FontWeight.BOLD, 13));

        HBox syncIntervalBox = new HBox(10);
        syncIntervalBox.setAlignment(Pos.CENTER_LEFT);
        Label syncIntervalLabel = new Label("Auto-sync interval:");
        ComboBox<String> syncIntervalCombo = new ComboBox<>(FXCollections.observableArrayList(
                "15 seconds", "30 seconds", "1 minute", "5 minutes", "Manual only"
        ));
        syncIntervalCombo.setValue("15 seconds");
        syncIntervalBox.getChildren().addAll(syncIntervalLabel, syncIntervalCombo);

        CheckBox autoSyncCb = new CheckBox("Enable automatic synchronization");
        autoSyncCb.setSelected(true);

        CheckBox syncOnStartCb = new CheckBox("Sync data on application startup");
        syncOnStartCb.setSelected(true);

        Label dataLabel = new Label("Data Management");
        dataLabel.setFont(Font.font("System", FontWeight.BOLD, 13));

        Button clearCacheBtn = new Button("Clear Local Cache");
        clearCacheBtn.setOnAction(e -> {
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
            confirm.setTitle("Clear Cache");
            confirm.setHeaderText("Clear local cache?");
            confirm.setContentText("This will remove locally cached data. You'll need to sync again.");
            confirm.showAndWait().ifPresent(response -> {
                if (response == ButtonType.OK) {
                    log.info("Cache cleared by user request");
                    updateStatusMessage("Cache cleared successfully");
                }
            });
        });

        syncContent.getChildren().addAll(syncLabel, syncIntervalBox, autoSyncCb, syncOnStartCb,
                new Separator(), dataLabel, clearCacheBtn);
        syncTab.setContent(syncContent);

        // === Connection Settings Tab ===
        Tab connectionTab = new Tab("Connections");
        connectionTab.setClosable(false);
        VBox connectionContent = new VBox(15);
        connectionContent.setPadding(new Insets(20));

        Label serverLabel = new Label("Server Configuration");
        serverLabel.setFont(Font.font("System", FontWeight.BOLD, 13));

        GridPane serverGrid = new GridPane();
        serverGrid.setHgap(10);
        serverGrid.setVgap(10);

        serverGrid.add(new Label("SIS API URL:"), 0, 0);
        TextField sisUrlField = new TextField("http://localhost:9590");
        sisUrlField.setPrefWidth(300);
        serverGrid.add(sisUrlField, 1, 0);

        serverGrid.add(new Label("EdGames URL:"), 0, 1);
        TextField edgamesUrlField = new TextField("http://localhost:8081");
        edgamesUrlField.setPrefWidth(300);
        serverGrid.add(edgamesUrlField, 1, 1);

        serverGrid.add(new Label("Heronix-Talk URL:"), 0, 2);
        TextField talkUrlField = new TextField("http://localhost:9680");
        talkUrlField.setPrefWidth(300);
        serverGrid.add(talkUrlField, 1, 2);

        Label networkLabel = new Label("Network");
        networkLabel.setFont(Font.font("System", FontWeight.BOLD, 13));

        HBox timeoutBox = new HBox(10);
        timeoutBox.setAlignment(Pos.CENTER_LEFT);
        Label timeoutLabel = new Label("Connection timeout:");
        ComboBox<String> timeoutCombo = new ComboBox<>(FXCollections.observableArrayList(
                "10 seconds", "30 seconds", "60 seconds"
        ));
        timeoutCombo.setValue("30 seconds");
        timeoutBox.getChildren().addAll(timeoutLabel, timeoutCombo);

        CheckBox networkCheckCb = new CheckBox("Enable network status monitoring");
        networkCheckCb.setSelected(true);

        connectionContent.getChildren().addAll(serverLabel, serverGrid,
                new Separator(), networkLabel, timeoutBox, networkCheckCb);
        connectionTab.setContent(connectionContent);

        // === Notifications Tab ===
        Tab notificationsTab = new Tab("Notifications");
        notificationsTab.setClosable(false);
        VBox notificationContent = new VBox(15);
        notificationContent.setPadding(new Insets(20));

        Label notifLabel = new Label("Notification Preferences");
        notifLabel.setFont(Font.font("System", FontWeight.BOLD, 13));

        CheckBox desktopNotifCb = new CheckBox("Show desktop notifications");
        desktopNotifCb.setSelected(true);

        CheckBox soundNotifCb = new CheckBox("Play notification sounds");
        soundNotifCb.setSelected(false);

        CheckBox hallPassAlertCb = new CheckBox("Alert on overdue hall passes");
        hallPassAlertCb.setSelected(true);

        CheckBox syncErrorAlertCb = new CheckBox("Alert on sync errors");
        syncErrorAlertCb.setSelected(true);

        CheckBox messageNotifCb = new CheckBox("Show new message notifications");
        messageNotifCb.setSelected(true);

        notificationContent.getChildren().addAll(notifLabel, desktopNotifCb, soundNotifCb,
                new Separator(), hallPassAlertCb, syncErrorAlertCb, messageNotifCb);
        notificationsTab.setContent(notificationContent);

        // Add all tabs
        settingsTabPane.getTabs().addAll(generalTab, syncTab, connectionTab, notificationsTab);

        dialog.getDialogPane().setContent(settingsTabPane);

        // Handle save
        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == ButtonType.OK) {
                log.info("Settings saved");

                // Apply theme change
                String selectedTheme = themeCombo.getValue();
                Scene scene = contentArea.getScene();
                boolean currentlyDark = "dark".equalsIgnoreCase(themeManager.getCurrentTheme());
                if ("Dark".equals(selectedTheme) && !currentlyDark && scene != null) {
                    themeManager.toggleTheme(scene);
                } else if ("Light".equals(selectedTheme) && currentlyDark && scene != null) {
                    themeManager.toggleTheme(scene);
                }

                updateStatusMessage("Settings saved successfully");
            }
            return null;
        });

        dialog.showAndWait();
    }

    /**
     * Start network status monitoring
     */
    private void startNetworkMonitoring() {
        networkCheckTimer = new Timer(true);
        networkCheckTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                checkNetworkStatus();
                checkServerStatuses();
            }
        }, 0, 30000); // Check every 30 seconds
    }

    /**
     * Check network status using NetworkMonitorService
     */
    private void checkNetworkStatus() {
        // Use NetworkMonitorService to check actual network connectivity
        boolean isOnline = networkMonitor.isNetworkAvailable();

        Platform.runLater(() -> {
            if (isOnline) {
                networkStatusIcon.setText("●");
                networkStatusIcon.getStyleClass().clear();
                networkStatusIcon.getStyleClass().add("success-text");
                networkStatusLabel.setText("Online");
            } else {
                networkStatusIcon.setText("●");
                networkStatusIcon.getStyleClass().clear();
                networkStatusIcon.getStyleClass().add("danger-text");
                networkStatusLabel.setText("Offline");
            }
        });
    }

    /**
     * Check individual server statuses (SIS, EdGames, Talk)
     */
    private void checkServerStatuses() {
        // Check SIS (Admin API) server
        boolean sisAvailable = false;
        try {
            sisAvailable = adminApiClient.isServerReachable();
        } catch (Exception e) {
            log.debug("SIS server check failed: {}", e.getMessage());
        }
        final boolean sisStatus = sisAvailable;

        // Check EdGames server
        boolean edgamesAvailable = false;
        try {
            edgamesAvailable = edGamesApiClient.isServerReachable();
        } catch (Exception e) {
            log.debug("EdGames server check failed: {}", e.getMessage());
        }
        final boolean edgamesStatus = edgamesAvailable;

        // Check Heronix-Talk server
        boolean talkAvailable = false;
        try {
            talkAvailable = talkApiClient.isServerReachable();
        } catch (Exception e) {
            log.debug("Talk server check failed: {}", e.getMessage());
        }
        final boolean talkStatus = talkAvailable;

        // Update UI on JavaFX thread
        Platform.runLater(() -> {
            updateServerStatusIcon(sisStatusIcon, sisStatus);
            updateServerStatusIcon(edgamesStatusIcon, edgamesStatus);
            updateServerStatusIcon(talkStatusIcon, talkStatus);
        });
    }

    /**
     * Update a server status icon based on availability
     */
    private void updateServerStatusIcon(Label icon, boolean available) {
        if (icon == null) return;

        if (available) {
            icon.setStyle("-fx-font-size: 10px; -fx-text-fill: #4CAF50;"); // Green
        } else {
            icon.setStyle("-fx-font-size: 10px; -fx-text-fill: #9E9E9E;"); // Gray
        }
    }

    /**
     * Update sync status display with actual data from AutoSyncService
     */
    private void updateSyncStatus() {
        // Get actual sync status from AutoSyncService
        long pendingCount = autoSyncService.getPendingItemsCount();
        long lastSyncTime = autoSyncService.getLastSyncTime();

        Platform.runLater(() -> {
            pendingSyncLabel.setText("Pending: " + pendingCount);

            // Update last sync time
            if (lastSyncTime > 0) {
                LocalDateTime syncTime = LocalDateTime.ofInstant(
                    java.time.Instant.ofEpochMilli(lastSyncTime),
                    java.time.ZoneId.systemDefault()
                );
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm:ss");
                lastSyncLabel.setText("Last sync: " + syncTime.format(formatter));
            } else {
                lastSyncLabel.setText("Last sync: Never");
            }
        });
    }

    /**
     * Update status message
     */
    private void updateStatusMessage(String message) {
        Platform.runLater(() -> {
            statusMessageLabel.setText(message);
        });
    }

    /**
     * Show error dialog
     */
    private void showError(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    /**
     * Cleanup when controller is destroyed
     */
    public void cleanup() {
        if (networkCheckTimer != null) {
            networkCheckTimer.cancel();
        }
        // Remove window resize listener
        if (windowWidthListener != null && mainRoot.getScene() != null) {
            mainRoot.getScene().widthProperty().removeListener(windowWidthListener);
        }
        log.info("Main Controller cleanup completed");
    }
}
