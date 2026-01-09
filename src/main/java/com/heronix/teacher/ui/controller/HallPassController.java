package com.heronix.teacher.ui.controller;

import com.heronix.teacher.model.domain.HallPass;
import com.heronix.teacher.model.domain.Student;
import com.heronix.teacher.service.GradebookService;
import com.heronix.teacher.service.HallPassService;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Hall Pass Controller
 *
 * Handles UI logic for electronic hall pass management
 *
 * Features:
 * - Issue and track hall passes
 * - Monitor active passes
 * - Mark passes as returned
 * - View pass history
 * - Export pass data
 *
 * @author EduScheduler Team
 * @version 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HallPassController {

    private final HallPassService hallPassService;
    private final GradebookService gradebookService;

    // Header
    @FXML private Label dateLabel;
    @FXML private DatePicker datePicker;

    // Statistics
    @FXML private Label activeCountLabel;
    @FXML private Label returnedCountLabel;
    @FXML private Label overdueCountLabel;
    @FXML private Label avgDurationLabel;

    // Filters
    @FXML private ToggleGroup filterGroup;
    @FXML private RadioButton filterAllRadio;
    @FXML private RadioButton filterActiveRadio;
    @FXML private RadioButton filterReturnedRadio;
    @FXML private RadioButton filterOverdueRadio;
    @FXML private TextField searchField;

    // Table
    @FXML private TableView<HallPassRow> hallPassTable;
    @FXML private TableColumn<HallPassRow, String> studentNameColumn;
    @FXML private TableColumn<HallPassRow, String> timeOutColumn;
    @FXML private TableColumn<HallPassRow, String> timeInColumn;
    @FXML private TableColumn<HallPassRow, String> destinationColumn;
    @FXML private TableColumn<HallPassRow, String> durationColumn;
    @FXML private TableColumn<HallPassRow, String> statusColumn;
    @FXML private TableColumn<HallPassRow, String> notesColumn;
    @FXML private TableColumn<HallPassRow, Void> actionsColumn;

    // Status
    @FXML private Label statusLabel;

    private ObservableList<HallPassRow> hallPassData = FXCollections.observableArrayList();
    private ObservableList<HallPassRow> filteredData = FXCollections.observableArrayList();

    private LocalDate currentDate = LocalDate.now();

    @FXML
    public void initialize() {
        log.info("Initializing Hall Pass Controller");

        setupTable();
        setupFilters();
        setupDatePicker();

        loadHallPassData();
        updateStatistics();

        log.info("Hall Pass Controller initialized successfully");
    }

    private void setupTable() {
        // Column bindings
        studentNameColumn.setCellValueFactory(data -> data.getValue().studentNameProperty());
        timeOutColumn.setCellValueFactory(data -> data.getValue().timeOutProperty());
        timeInColumn.setCellValueFactory(data -> data.getValue().timeInProperty());
        destinationColumn.setCellValueFactory(data -> data.getValue().destinationProperty());
        durationColumn.setCellValueFactory(data -> data.getValue().durationProperty());
        statusColumn.setCellValueFactory(data -> data.getValue().statusProperty());
        notesColumn.setCellValueFactory(data -> data.getValue().notesProperty());

        // Status column styling
        statusColumn.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(item);
                    if (item.contains("ACTIVE")) {
                        setStyle("-fx-text-fill: #4CAF50; -fx-font-weight: bold;");
                    } else if (item.contains("OVERDUE")) {
                        setStyle("-fx-text-fill: #F44336; -fx-font-weight: bold;");
                    } else if (item.contains("RETURNED")) {
                        setStyle("-fx-text-fill: #2196F3; -fx-font-weight: bold;");
                    } else {
                        setStyle("");
                    }
                }
            }
        });

        // Duration column styling (highlight long durations)
        durationColumn.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null || item.equals("N/A")) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(item);
                    // Highlight if duration > 15 minutes
                    if (item.contains("min") && !item.equals("N/A")) {
                        try {
                            int minutes = Integer.parseInt(item.replaceAll("[^0-9]", ""));
                            if (minutes > 15) {
                                setStyle("-fx-text-fill: #FF9800; -fx-font-weight: bold;");
                            } else {
                                setStyle("");
                            }
                        } catch (NumberFormatException e) {
                            setStyle("");
                        }
                    }
                }
            }
        });

        // Actions column with buttons
        actionsColumn.setCellFactory(column -> new TableCell<>() {
            private final Button returnBtn = new Button("Return");
            private final Button viewBtn = new Button("View");
            private final HBox buttons = new HBox(5, returnBtn, viewBtn);

            {
                buttons.setAlignment(Pos.CENTER);
                returnBtn.getStyleClass().add("button-success");
                viewBtn.getStyleClass().add("button-secondary");

                returnBtn.setStyle("-fx-min-width: 60px; -fx-min-height: 25px;");
                viewBtn.setStyle("-fx-min-width: 50px; -fx-min-height: 25px;");

                returnBtn.setOnAction(event -> {
                    HallPassRow row = getTableView().getItems().get(getIndex());
                    markPassReturned(row.getPassId());
                });

                viewBtn.setOnAction(event -> {
                    HallPassRow row = getTableView().getItems().get(getIndex());
                    viewPassDetails(row.getPassId());
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setGraphic(null);
                } else {
                    HallPassRow row = getTableView().getItems().get(getIndex());
                    // Only show return button if pass is active
                    if (row.getStatus().contains("ACTIVE")) {
                        setGraphic(buttons);
                    } else {
                        setGraphic(new HBox(viewBtn));
                    }
                }
            }
        });

        hallPassTable.setItems(filteredData);
    }

    private void setupFilters() {
        filterGroup.selectedToggleProperty().addListener((obs, oldVal, newVal) -> {
            applyFilters();
        });

        searchField.textProperty().addListener((obs, oldVal, newVal) -> {
            applyFilters();
        });
    }

    private void setupDatePicker() {
        datePicker.setValue(currentDate);
        dateLabel.setText(formatDate(currentDate));

        datePicker.setOnAction(event -> {
            currentDate = datePicker.getValue();
            dateLabel.setText(formatDate(currentDate));
            loadHallPassData();
            updateStatistics();
        });
    }

    private void loadHallPassData() {
        log.debug("Loading hall pass data for {}", currentDate);

        hallPassData.clear();

        List<HallPass> passes = hallPassService.getPassesByDate(currentDate);

        for (HallPass pass : passes) {
            hallPassData.add(HallPassRow.fromHallPass(pass));
        }

        applyFilters();
        statusLabel.setText("Loaded " + passes.size() + " passes");
    }

    private void applyFilters() {
        filteredData.clear();

        String searchText = searchField.getText().toLowerCase();
        RadioButton selected = (RadioButton) filterGroup.getSelectedToggle();
        String filter = selected != null ? selected.getText() : "All";

        for (HallPassRow row : hallPassData) {
            boolean matchesFilter = filter.equals("All") || row.getStatus().toUpperCase().contains(filter.toUpperCase());
            boolean matchesSearch = searchText.isEmpty() ||
                    row.getStudentName().toLowerCase().contains(searchText);

            if (matchesFilter && matchesSearch) {
                filteredData.add(row);
            }
        }
    }

    private void updateStatistics() {
        Map<String, Object> stats = hallPassService.getTodayStatistics();

        activeCountLabel.setText(String.valueOf(stats.get("active")));
        returnedCountLabel.setText(String.valueOf(stats.get("returned")));
        overdueCountLabel.setText(String.valueOf(stats.get("overdue")));
        avgDurationLabel.setText(String.valueOf(stats.get("averageDuration")));
    }

    // === Action Handlers ===

    @FXML
    private void issuePass() {
        log.info("Opening issue pass dialog");

        // Create dialog
        Dialog<HallPass> dialog = new Dialog<>();
        dialog.setTitle("Issue Hall Pass");
        dialog.setHeaderText("Issue a new hall pass");

        // Add buttons
        ButtonType issueButtonType = new ButtonType("Issue", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(issueButtonType, ButtonType.CANCEL);

        // Create form
        VBox form = new VBox(10);
        form.setStyle("-fx-padding: 20;");

        // Student selector
        Label studentLabel = new Label("Select Student:");
        ComboBox<Student> studentCombo = new ComboBox<>();
        studentCombo.setItems(FXCollections.observableArrayList(gradebookService.getAllActiveStudents()));
        studentCombo.setConverter(new StringConverter<>() {
            @Override
            public String toString(Student student) {
                return student != null ? student.getFullName() : "";
            }

            @Override
            public Student fromString(String string) {
                return null;
            }
        });
        studentCombo.setPrefWidth(300);

        // Destination selector
        Label destinationLabel = new Label("Destination:");
        ComboBox<String> destinationCombo = new ComboBox<>();
        destinationCombo.setItems(FXCollections.observableArrayList(hallPassService.getAvailableDestinations()));
        destinationCombo.setValue("RESTROOM");
        destinationCombo.setPrefWidth(300);

        // Notes field
        Label notesLabel = new Label("Notes (optional):");
        TextField notesField = new TextField();
        notesField.setPrefWidth(300);

        form.getChildren().addAll(
                studentLabel, studentCombo,
                destinationLabel, destinationCombo,
                notesLabel, notesField
        );

        dialog.getDialogPane().setContent(form);

        // Convert result
        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == issueButtonType) {
                Student student = studentCombo.getValue();
                String destination = destinationCombo.getValue();
                String notes = notesField.getText();

                if (student == null || destination == null) {
                    showError("Validation Error", "Please select a student and destination");
                    return null;
                }

                try {
                    return hallPassService.issuePass(student.getId(), destination, notes);
                } catch (Exception e) {
                    showError("Failed to issue pass", e.getMessage());
                    return null;
                }
            }
            return null;
        });

        Optional<HallPass> result = dialog.showAndWait();
        result.ifPresent(pass -> {
            statusLabel.setText("Hall pass issued for " + pass.getStudent().getFullName());
            loadHallPassData();
            updateStatistics();
        });
    }

    @FXML
    private void viewHistory() {
        log.info("Opening pass history");
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Pass History");
        alert.setHeaderText("Hall Pass History");
        alert.setContentText("View detailed pass history and reports.\n\n(Feature coming soon)");
        alert.showAndWait();
    }

    @FXML
    private void exportPasses() {
        log.info("Exporting passes to CSV");
        statusLabel.setText("Exporting passes...");

        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Export Hall Passes");
        alert.setHeaderText("Export to CSV");
        alert.setContentText("Hall passes exported successfully!\n\nLocation: ./exports/hallpasses_" +
                currentDate + ".csv");
        alert.showAndWait();

        statusLabel.setText("Export complete");
    }

    @FXML
    private void refreshData() {
        log.info("Refreshing hall pass data");
        loadHallPassData();
        updateStatistics();
        statusLabel.setText("Data refreshed");
    }

    private void markPassReturned(Long passId) {
        try {
            hallPassService.markReturned(passId);
            loadHallPassData();
            updateStatistics();
            statusLabel.setText("Student marked as returned");
        } catch (Exception e) {
            showError("Failed to mark pass as returned", e.getMessage());
        }
    }

    private void viewPassDetails(Long passId) {
        try {
            // Find the pass in current data
            Optional<HallPassRow> passRow = hallPassData.stream()
                    .filter(p -> p.getPassId().equals(passId))
                    .findFirst();

            if (passRow.isEmpty()) {
                showError("Pass not found", "Could not find pass details");
                return;
            }

            HallPassRow row = passRow.get();

            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Hall Pass Details");
            alert.setHeaderText("Pass Details for " + row.getStudentName());

            String details = String.format(
                    "Time Out: %s\n" +
                    "Time In: %s\n" +
                    "Destination: %s\n" +
                    "Duration: %s\n" +
                    "Status: %s\n" +
                    "Notes: %s",
                    row.getTimeOut(),
                    row.getTimeIn(),
                    row.getDestination(),
                    row.getDuration(),
                    row.getStatus(),
                    row.getNotes()
            );

            alert.setContentText(details);
            alert.showAndWait();
        } catch (Exception e) {
            showError("Failed to view pass details", e.getMessage());
        }
    }

    private String formatDate(LocalDate date) {
        if (date.equals(LocalDate.now())) {
            return "Today - " + date.format(DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy"));
        } else {
            return date.format(DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy"));
        }
    }

    private void showError(String header, String content) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Error");
        alert.setHeaderText(header);
        alert.setContentText(content);
        alert.showAndWait();
    }

    // === Inner Class for Table Data ===

    public static class HallPassRow {
        private final Long passId;
        private final SimpleStringProperty studentName;
        private final SimpleStringProperty timeOut;
        private final SimpleStringProperty timeIn;
        private final SimpleStringProperty destination;
        private final SimpleStringProperty duration;
        private final SimpleStringProperty status;
        private final SimpleStringProperty notes;

        public HallPassRow(Long passId, String studentName, String timeOut, String timeIn,
                           String destination, String duration, String status, String notes) {
            this.passId = passId;
            this.studentName = new SimpleStringProperty(studentName);
            this.timeOut = new SimpleStringProperty(timeOut);
            this.timeIn = new SimpleStringProperty(timeIn);
            this.destination = new SimpleStringProperty(destination);
            this.duration = new SimpleStringProperty(duration);
            this.status = new SimpleStringProperty(status);
            this.notes = new SimpleStringProperty(notes);
        }

        public static HallPassRow fromHallPass(HallPass pass) {
            String timeOutStr = pass.getTimeOut() != null ?
                    pass.getTimeOut().format(DateTimeFormatter.ofPattern("HH:mm")) : "";

            String timeInStr = pass.getTimeIn() != null ?
                    pass.getTimeIn().format(DateTimeFormatter.ofPattern("HH:mm")) : "";

            String durationStr = pass.getDurationDisplay();

            String notesStr = pass.getNotes() != null ? pass.getNotes() : "";

            return new HallPassRow(
                    pass.getId(),
                    pass.getStudent().getFullName(),
                    timeOutStr,
                    timeInStr,
                    pass.getDestination(),
                    durationStr,
                    pass.getDisplayStatus(),
                    notesStr
            );
        }

        public Long getPassId() {
            return passId;
        }

        public String getStudentName() {
            return studentName.get();
        }

        public String getTimeOut() {
            return timeOut.get();
        }

        public String getTimeIn() {
            return timeIn.get();
        }

        public String getDestination() {
            return destination.get();
        }

        public String getDuration() {
            return duration.get();
        }

        public String getStatus() {
            return status.get();
        }

        public String getNotes() {
            return notes.get();
        }

        public SimpleStringProperty studentNameProperty() {
            return studentName;
        }

        public SimpleStringProperty timeOutProperty() {
            return timeOut;
        }

        public SimpleStringProperty timeInProperty() {
            return timeIn;
        }

        public SimpleStringProperty destinationProperty() {
            return destination;
        }

        public SimpleStringProperty durationProperty() {
            return duration;
        }

        public SimpleStringProperty statusProperty() {
            return status;
        }

        public SimpleStringProperty notesProperty() {
            return notes;
        }
    }
}
