package com.heronix.teacher.ui.controller;

import com.heronix.teacher.service.EdGamesApiClient;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Controller for Device Management view
 * Manages Ed-Games client device registrations and approvals
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DeviceManagementController {

    private final EdGamesApiClient edGamesApi;

    // Header Statistics
    @FXML private Label totalDevicesLabel;
    @FXML private Label pendingDevicesLabel;
    @FXML private Label approvedDevicesLabel;

    // Pending Devices Tab
    @FXML private TextField pendingSearchField;
    @FXML private TableView<Map<String, Object>> pendingDevicesTable;
    @FXML private TableColumn<Map<String, Object>, String> pendingDeviceIdCol;
    @FXML private TableColumn<Map<String, Object>, String> pendingDeviceNameCol;
    @FXML private TableColumn<Map<String, Object>, String> pendingDeviceTypeCol;
    @FXML private TableColumn<Map<String, Object>, String> pendingOsCol;
    @FXML private TableColumn<Map<String, Object>, String> pendingRegisteredAtCol;
    @FXML private TableColumn<Map<String, Object>, Void> pendingActionsCol;

    // Active Devices Tab
    @FXML private TextField activeSearchField;
    @FXML private TableView<Map<String, Object>> activeDevicesTable;
    @FXML private TableColumn<Map<String, Object>, String> activeDeviceIdCol;
    @FXML private TableColumn<Map<String, Object>, String> activeDeviceNameCol;
    @FXML private TableColumn<Map<String, Object>, String> activeStudentIdCol;
    @FXML private TableColumn<Map<String, Object>, String> activeDeviceTypeCol;
    @FXML private TableColumn<Map<String, Object>, String> activeApprovedAtCol;
    @FXML private TableColumn<Map<String, Object>, String> activeLastSyncCol;
    @FXML private TableColumn<Map<String, Object>, Void> activeActionsCol;

    // All Devices Tab
    @FXML private ComboBox<String> statusFilterCombo;
    @FXML private TextField allSearchField;
    @FXML private TableView<Map<String, Object>> allDevicesTable;
    @FXML private TableColumn<Map<String, Object>, String> allDeviceIdCol;
    @FXML private TableColumn<Map<String, Object>, String> allDeviceNameCol;
    @FXML private TableColumn<Map<String, Object>, String> allStudentIdCol;
    @FXML private TableColumn<Map<String, Object>, String> allStatusCol;
    @FXML private TableColumn<Map<String, Object>, String> allDeviceTypeCol;
    @FXML private TableColumn<Map<String, Object>, String> allRegisteredAtCol;

    // Status Bar
    @FXML private Label statusLabel;
    @FXML private Label serverStatusLabel;

    private ObservableList<Map<String, Object>> pendingDevices = FXCollections.observableArrayList();
    private ObservableList<Map<String, Object>> activeDevices = FXCollections.observableArrayList();
    private ObservableList<Map<String, Object>> allDevices = FXCollections.observableArrayList();

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("MM/dd/yyyy HH:mm");

    @FXML
    public void initialize() {
        log.info("Initializing Device Management Controller");

        setupPendingDevicesTable();
        setupActiveDevicesTable();
        setupAllDevicesTable();
        setupSearchFilters();
        setupStatusFilter();

        checkServerStatus();
        refreshDevices();

        log.info("Device Management Controller initialized");
    }

    /**
     * Setup pending devices table
     */
    private void setupPendingDevicesTable() {
        pendingDeviceIdCol.setCellValueFactory(data ->
                new SimpleStringProperty(getStringValue(data.getValue(), "deviceId")));

        pendingDeviceNameCol.setCellValueFactory(data ->
                new SimpleStringProperty(getStringValue(data.getValue(), "deviceName")));

        pendingDeviceTypeCol.setCellValueFactory(data ->
                new SimpleStringProperty(getStringValue(data.getValue(), "deviceType")));

        pendingOsCol.setCellValueFactory(data ->
                new SimpleStringProperty(getStringValue(data.getValue(), "operatingSystem")));

        pendingRegisteredAtCol.setCellValueFactory(data ->
                new SimpleStringProperty(formatDate(getStringValue(data.getValue(), "registeredAt"))));

        // Add action buttons column
        pendingActionsCol.setCellFactory(param -> new TableCell<>() {
            private final Button approveBtn = new Button("✓ Approve");
            private final Button rejectBtn = new Button("✗ Reject");
            private final HBox actionBox = new HBox(5, approveBtn, rejectBtn);

            {
                approveBtn.setStyle("-fx-background-color: #4CAF50; -fx-text-fill: white; -fx-cursor: hand;");
                rejectBtn.setStyle("-fx-background-color: #f44336; -fx-text-fill: white; -fx-cursor: hand;");
                actionBox.setAlignment(Pos.CENTER);

                approveBtn.setOnAction(event -> {
                    Map<String, Object> device = getTableView().getItems().get(getIndex());
                    approveDevice(device);
                });

                rejectBtn.setOnAction(event -> {
                    Map<String, Object> device = getTableView().getItems().get(getIndex());
                    rejectDevice(device);
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setGraphic(null);
                } else {
                    setGraphic(actionBox);
                }
            }
        });

        pendingDevicesTable.setItems(pendingDevices);
    }

    /**
     * Setup active devices table
     */
    private void setupActiveDevicesTable() {
        activeDeviceIdCol.setCellValueFactory(data ->
                new SimpleStringProperty(getStringValue(data.getValue(), "deviceId")));

        activeDeviceNameCol.setCellValueFactory(data ->
                new SimpleStringProperty(getStringValue(data.getValue(), "deviceName")));

        activeStudentIdCol.setCellValueFactory(data ->
                new SimpleStringProperty(getStringValue(data.getValue(), "studentId")));

        activeDeviceTypeCol.setCellValueFactory(data ->
                new SimpleStringProperty(getStringValue(data.getValue(), "deviceType")));

        activeApprovedAtCol.setCellValueFactory(data ->
                new SimpleStringProperty(formatDate(getStringValue(data.getValue(), "approvedAt"))));

        activeLastSyncCol.setCellValueFactory(data ->
                new SimpleStringProperty(formatDate(getStringValue(data.getValue(), "lastSyncAt"))));

        // Add revoke action button
        activeActionsCol.setCellFactory(param -> new TableCell<>() {
            private final Button revokeBtn = new Button("⊗ Revoke");

            {
                revokeBtn.setStyle("-fx-background-color: #FF9800; -fx-text-fill: white; -fx-cursor: hand;");

                revokeBtn.setOnAction(event -> {
                    Map<String, Object> device = getTableView().getItems().get(getIndex());
                    revokeDevice(device);
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setGraphic(null);
                } else {
                    setGraphic(revokeBtn);
                }
            }
        });

        activeDevicesTable.setItems(activeDevices);
    }

    /**
     * Setup all devices table
     */
    private void setupAllDevicesTable() {
        allDeviceIdCol.setCellValueFactory(data ->
                new SimpleStringProperty(getStringValue(data.getValue(), "deviceId")));

        allDeviceNameCol.setCellValueFactory(data ->
                new SimpleStringProperty(getStringValue(data.getValue(), "deviceName")));

        allStudentIdCol.setCellValueFactory(data ->
                new SimpleStringProperty(getStringValue(data.getValue(), "studentId")));

        allStatusCol.setCellValueFactory(data ->
                new SimpleStringProperty(getStringValue(data.getValue(), "status")));

        allDeviceTypeCol.setCellValueFactory(data ->
                new SimpleStringProperty(getStringValue(data.getValue(), "deviceType")));

        allRegisteredAtCol.setCellValueFactory(data ->
                new SimpleStringProperty(formatDate(getStringValue(data.getValue(), "registeredAt"))));

        allDevicesTable.setItems(allDevices);
    }

    /**
     * Setup search filters
     */
    private void setupSearchFilters() {
        // Pending search
        pendingSearchField.textProperty().addListener((obs, oldVal, newVal) -> {
            // TODO: Implement search filtering
        });

        // Active search
        activeSearchField.textProperty().addListener((obs, oldVal, newVal) -> {
            // TODO: Implement search filtering
        });

        // All search
        allSearchField.textProperty().addListener((obs, oldVal, newVal) -> {
            // TODO: Implement search filtering
        });
    }

    /**
     * Setup status filter combo box
     */
    private void setupStatusFilter() {
        statusFilterCombo.setItems(FXCollections.observableArrayList(
                "All", "PENDING", "APPROVED", "REJECTED", "REVOKED"
        ));
        statusFilterCombo.setValue("All");

        statusFilterCombo.valueProperty().addListener((obs, oldVal, newVal) -> {
            // TODO: Implement status filtering
        });
    }

    /**
     * Refresh all devices from server
     */
    @FXML
    public void refreshDevices() {
        statusLabel.setText("Refreshing devices...");

        new Thread(() -> {
            try {
                // Fetch pending devices
                List<Map<String, Object>> pending = edGamesApi.getPendingDevices();

                // Fetch active devices
                List<Map<String, Object>> active = edGamesApi.getActiveDevices();

                // Fetch device statistics
                Map<String, Object> stats = edGamesApi.getDeviceStats();

                Platform.runLater(() -> {
                    pendingDevices.clear();
                    pendingDevices.addAll(pending);

                    activeDevices.clear();
                    activeDevices.addAll(active);

                    allDevices.clear();
                    allDevices.addAll(pending);
                    allDevices.addAll(active);

                    updateStatistics(stats);
                    statusLabel.setText("Devices refreshed at " + LocalDateTime.now().format(DATE_FORMATTER));
                });

            } catch (Exception e) {
                log.error("Error refreshing devices", e);
                Platform.runLater(() -> {
                    statusLabel.setText("Error refreshing devices");
                    showError("Refresh Error", "Failed to refresh devices from server: " + e.getMessage());
                });
            }
        }).start();
    }

    /**
     * Update statistics labels
     */
    private void updateStatistics(Map<String, Object> stats) {
        totalDevicesLabel.setText(String.valueOf(stats.getOrDefault("total", 0)));
        pendingDevicesLabel.setText(String.valueOf(stats.getOrDefault("pending", 0)));
        approvedDevicesLabel.setText(String.valueOf(stats.getOrDefault("approved", 0)));
    }

    /**
     * Approve a device
     */
    private void approveDevice(Map<String, Object> device) {
        String deviceId = getStringValue(device, "deviceId");
        String deviceName = getStringValue(device, "deviceName");

        // Ask for student ID
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Approve Device");
        dialog.setHeaderText("Approve device: " + deviceName);
        dialog.setContentText("Enter Student ID:");

        Optional<String> result = dialog.showAndWait();
        if (result.isPresent() && !result.get().trim().isEmpty()) {
            String studentId = result.get().trim();

            statusLabel.setText("Approving device " + deviceId + "...");

            new Thread(() -> {
                boolean success = edGamesApi.approveDevice(deviceId, studentId);

                Platform.runLater(() -> {
                    if (success) {
                        statusLabel.setText("Device approved successfully");
                        showInfo("Success", "Device " + deviceName + " approved for student " + studentId);
                        refreshDevices();
                    } else {
                        statusLabel.setText("Failed to approve device");
                        showError("Error", "Failed to approve device. Please try again.");
                    }
                });
            }).start();
        }
    }

    /**
     * Reject a device
     */
    private void rejectDevice(Map<String, Object> device) {
        String deviceId = getStringValue(device, "deviceId");
        String deviceName = getStringValue(device, "deviceName");

        Alert confirmDialog = new Alert(Alert.AlertType.CONFIRMATION);
        confirmDialog.setTitle("Reject Device");
        confirmDialog.setHeaderText("Reject device: " + deviceName);
        confirmDialog.setContentText("Are you sure you want to reject this device registration?");

        Optional<ButtonType> result = confirmDialog.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            statusLabel.setText("Rejecting device " + deviceId + "...");

            new Thread(() -> {
                boolean success = edGamesApi.rejectDevice(deviceId, "Rejected by teacher");

                Platform.runLater(() -> {
                    if (success) {
                        statusLabel.setText("Device rejected");
                        showInfo("Success", "Device " + deviceName + " has been rejected");
                        refreshDevices();
                    } else {
                        statusLabel.setText("Failed to reject device");
                        showError("Error", "Failed to reject device. Please try again.");
                    }
                });
            }).start();
        }
    }

    /**
     * Revoke a device
     */
    private void revokeDevice(Map<String, Object> device) {
        String deviceId = getStringValue(device, "deviceId");
        String deviceName = getStringValue(device, "deviceName");

        Alert confirmDialog = new Alert(Alert.AlertType.CONFIRMATION);
        confirmDialog.setTitle("Revoke Device");
        confirmDialog.setHeaderText("Revoke device: " + deviceName);
        confirmDialog.setContentText("This will deactivate the device and require re-approval. Continue?");

        Optional<ButtonType> result = confirmDialog.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            statusLabel.setText("Revoking device " + deviceId + "...");

            new Thread(() -> {
                boolean success = edGamesApi.revokeDevice(deviceId, "Revoked by teacher");

                Platform.runLater(() -> {
                    if (success) {
                        statusLabel.setText("Device revoked");
                        showInfo("Success", "Device " + deviceName + " has been revoked");
                        refreshDevices();
                    } else {
                        statusLabel.setText("Failed to revoke device");
                        showError("Error", "Failed to revoke device. Please try again.");
                    }
                });
            }).start();
        }
    }

    /**
     * Check Ed-Games server status
     */
    private void checkServerStatus() {
        new Thread(() -> {
            boolean reachable = edGamesApi.isServerReachable();

            Platform.runLater(() -> {
                if (reachable) {
                    serverStatusLabel.setText("Ed-Games Server: ● Online");
                    serverStatusLabel.setStyle("-fx-text-fill: #4CAF50;");
                } else {
                    serverStatusLabel.setText("Ed-Games Server: ● Offline");
                    serverStatusLabel.setStyle("-fx-text-fill: #f44336;");
                }
            });
        }).start();
    }

    /**
     * Get string value from map
     */
    private String getStringValue(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value != null ? value.toString() : "";
    }

    /**
     * Format date string
     */
    private String formatDate(String dateStr) {
        if (dateStr == null || dateStr.isEmpty()) {
            return "N/A";
        }
        try {
            LocalDateTime dateTime = LocalDateTime.parse(dateStr);
            return dateTime.format(DATE_FORMATTER);
        } catch (Exception e) {
            return dateStr;
        }
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
     * Show info dialog
     */
    private void showInfo(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
