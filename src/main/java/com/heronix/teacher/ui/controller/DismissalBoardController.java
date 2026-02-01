package com.heronix.teacher.ui.controller;

import com.heronix.teacher.service.DismissalService;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.stream.Collectors;

/**
 * Read-only dismissal board view for teachers.
 * Fetches data from SIS Server via REST API.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DismissalBoardController {

    private final DismissalService dismissalService;

    @FXML private Label dateLabel;
    @FXML private CheckBox autoRefreshToggle;
    @FXML private ProgressIndicator loadingIndicator;

    // Notification banner
    @FXML private VBox notificationBanner;
    @FXML private Label notificationTitle;
    @FXML private Label notificationMessage;

    @FXML private Label statBusArrivals;
    @FXML private Label statCarPickups;
    @FXML private Label statPending;
    @FXML private Label statDeparted;

    @FXML private ComboBox<String> typeFilter;
    @FXML private TextField searchField;
    @FXML private Label recordCountLabel;

    @FXML private TableView<Map<String, Object>> eventsTable;
    @FXML private TableColumn<Map<String, Object>, String> typeColumn;
    @FXML private TableColumn<Map<String, Object>, String> busNumberColumn;
    @FXML private TableColumn<Map<String, Object>, String> studentNameColumn;
    @FXML private TableColumn<Map<String, Object>, String> parentNameColumn;
    @FXML private TableColumn<Map<String, Object>, String> statusColumn;
    @FXML private TableColumn<Map<String, Object>, String> arrivalTimeColumn;
    @FXML private TableColumn<Map<String, Object>, String> calledTimeColumn;
    @FXML private TableColumn<Map<String, Object>, String> notesColumn;

    private final ObservableList<Map<String, Object>> allEvents = FXCollections.observableArrayList();
    private final ObservableList<Map<String, Object>> filteredEvents = FXCollections.observableArrayList();
    private Timer autoRefreshTimer;

    @FXML
    public void initialize() {
        dateLabel.setText(LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy")));
        setupFilters();
        setupTableColumns();
        setupRowFactory();
        setupAutoRefresh();
        loadEvents();
    }

    private void setupFilters() {
        typeFilter.setItems(FXCollections.observableArrayList(
                "All Types", "Bus Arrival", "Car Pickup", "Walker", "Aftercare", "Athletics", "Counselor Summon"));
        typeFilter.getSelectionModel().selectFirst();
        typeFilter.setOnAction(e -> applyFilters());
        searchField.textProperty().addListener((obs, oldVal, newVal) -> applyFilters());
    }

    private void setupTableColumns() {
        typeColumn.setCellValueFactory(cd -> new SimpleStringProperty(
                getStr(cd.getValue(), "eventType", "")));
        busNumberColumn.setCellValueFactory(cd -> new SimpleStringProperty(
                getStr(cd.getValue(), "busNumber", "")));
        studentNameColumn.setCellValueFactory(cd -> new SimpleStringProperty(
                getStr(cd.getValue(), "studentName", "")));
        parentNameColumn.setCellValueFactory(cd -> new SimpleStringProperty(
                getStr(cd.getValue(), "parentName", "")));
        statusColumn.setCellValueFactory(cd -> new SimpleStringProperty(
                getStr(cd.getValue(), "status", "")));
        arrivalTimeColumn.setCellValueFactory(cd -> new SimpleStringProperty(
                getStr(cd.getValue(), "arrivalTime", "")));
        calledTimeColumn.setCellValueFactory(cd -> new SimpleStringProperty(
                getStr(cd.getValue(), "calledTime", "")));
        notesColumn.setCellValueFactory(cd -> new SimpleStringProperty(
                getStr(cd.getValue(), "notes", "")));

        eventsTable.setItems(filteredEvents);
    }

    private void setupRowFactory() {
        eventsTable.setRowFactory(tv -> new TableRow<>() {
            @Override
            protected void updateItem(Map<String, Object> item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setStyle("");
                } else {
                    String type = getStr(item, "eventType", "");
                    String status = getStr(item, "status", "");
                    String bgColor = switch (type) {
                        case "BUS_ARRIVAL" -> "#FFF3E0";
                        case "CAR_PICKUP" -> "#E3F2FD";
                        case "WALKER" -> "#E8F5E9";
                        case "AFTERCARE" -> "#F3E5F5";
                        case "ATHLETICS" -> "#FFF9C4";
                        case "COUNSELOR_SUMMON" -> "#FFECB3";
                        default -> "transparent";
                    };

                    if ("DEPARTED".equals(status)) {
                        setStyle("-fx-background-color: " + bgColor + "; -fx-opacity: 0.5; -fx-font-weight: bold;");
                    } else {
                        setStyle("-fx-background-color: " + bgColor + "; -fx-font-weight: bold;");
                    }
                }
            }
        });
    }

    private void setupAutoRefresh() {
        autoRefreshToggle.selectedProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal) startAutoRefresh();
            else stopAutoRefresh();
        });
        startAutoRefresh();
    }

    private void startAutoRefresh() {
        stopAutoRefresh();
        autoRefreshTimer = new Timer(true);
        autoRefreshTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                Platform.runLater(() -> loadEvents());
            }
        }, 15000, 15000);
    }

    private void stopAutoRefresh() {
        if (autoRefreshTimer != null) {
            autoRefreshTimer.cancel();
            autoRefreshTimer = null;
        }
    }

    private void loadEvents() {
        try {
            loadingIndicator.setVisible(true);
            List<Map<String, Object>> events = dismissalService.getTodaysEvents();
            allEvents.setAll(events);
            applyFilters();
            updateStats();
            checkNotifications();
        } catch (Exception e) {
            log.error("Failed to load dismissal events", e);
        } finally {
            loadingIndicator.setVisible(false);
        }
    }

    private void applyFilters() {
        String typeVal = typeFilter.getValue();
        String searchText = searchField.getText() != null ? searchField.getText().toLowerCase().trim() : "";

        List<Map<String, Object>> filtered = allEvents.stream()
                .filter(ev -> {
                    if (typeVal == null || "All Types".equals(typeVal)) return true;
                    String type = getStr(ev, "eventType", "");
                    return type.replace("_", " ").equalsIgnoreCase(typeVal);
                })
                .filter(ev -> {
                    if (searchText.isEmpty()) return true;
                    String bus = getStr(ev, "busNumber", "").toLowerCase();
                    String student = getStr(ev, "studentName", "").toLowerCase();
                    String parent = getStr(ev, "parentName", "").toLowerCase();
                    return bus.contains(searchText) || student.contains(searchText) || parent.contains(searchText);
                })
                .collect(Collectors.toList());

        filteredEvents.setAll(filtered);
        recordCountLabel.setText(filtered.size() + " event" + (filtered.size() != 1 ? "s" : ""));
    }

    private void updateStats() {
        try {
            Map<String, Object> stats = dismissalService.getTodaysStats();
            statBusArrivals.setText(String.valueOf(stats.getOrDefault("busArrivals", 0)));
            statCarPickups.setText(String.valueOf(stats.getOrDefault("carPickups", 0)));
            statPending.setText(String.valueOf(stats.getOrDefault("pending", 0)));
            statDeparted.setText(String.valueOf(stats.getOrDefault("departed", 0)));
        } catch (Exception e) {
            log.error("Failed to load stats", e);
        }
    }

    private void checkNotifications() {
        try {
            List<Map<String, Object>> notifications = dismissalService.getCounselorSummonNotifications();
            if (!notifications.isEmpty()) {
                Map<String, Object> latest = notifications.get(0);
                notificationTitle.setText(getStr(latest, "title", "Counselor Summon"));
                notificationMessage.setText(getStr(latest, "message", ""));
                notificationBanner.setVisible(true);
                notificationBanner.setManaged(true);
            }
        } catch (Exception e) {
            log.debug("Failed to check notifications", e);
        }
    }

    @FXML
    private void handleDismissNotification() {
        notificationBanner.setVisible(false);
        notificationBanner.setManaged(false);
    }

    @FXML
    private void handleRefresh() {
        loadEvents();
    }

    private String getStr(Map<String, Object> map, String key, String defaultVal) {
        Object val = map.get(key);
        return val != null ? val.toString() : defaultVal;
    }
}
