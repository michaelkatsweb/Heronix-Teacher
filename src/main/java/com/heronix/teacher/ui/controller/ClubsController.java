package com.heronix.teacher.ui.controller;

import com.heronix.teacher.model.domain.Club;
import com.heronix.teacher.model.domain.Student;
import com.heronix.teacher.service.ClubService;
import com.heronix.teacher.service.SessionManager;
import javafx.beans.property.SimpleIntegerProperty;
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
import java.util.*;
import java.util.stream.Collectors;

/**
 * Clubs Controller
 *
 * Manages club roster and attendance
 * - View all clubs
 * - Manage memberships
 * - Track club attendance
 * - Generate reports
 *
 * @author Heronix-Teacher Team
 * @version 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ClubsController {

    private final ClubService clubService;
    private final SessionManager sessionManager;

    // ====================  Tab Pane ====================
    @FXML private TabPane clubTabPane;

    // ==================== Club Management Tab ====================

    // Filter controls
    @FXML private ComboBox<String> categoryFilterCombo;
    @FXML private ComboBox<String> statusFilterCombo;
    @FXML private ComboBox<String> capacityFilterCombo;
    @FXML private TextField searchField;

    // Summary labels
    @FXML private Label totalClubsLabel;
    @FXML private Label totalMembersLabel;
    @FXML private Label availableSpotsLabel;
    @FXML private Label categoriesLabel;
    @FXML private Label clubCountLabel;

    // Buttons
    @FXML private Button syncBtn;

    // Clubs table
    @FXML private TableView<Club> clubsTable;
    @FXML private TableColumn<Club, String> clubNameColumn;
    @FXML private TableColumn<Club, String> categoryColumn;
    @FXML private TableColumn<Club, String> advisorColumn;
    @FXML private TableColumn<Club, String> meetingDayColumn;
    @FXML private TableColumn<Club, String> meetingTimeColumn;
    @FXML private TableColumn<Club, String> locationColumn;
    @FXML private TableColumn<Club, String> enrollmentColumn;
    @FXML private TableColumn<Club, Integer> capacityColumn;
    @FXML private TableColumn<Club, String> statusColumn;

    // ==================== Club Attendance Tab ====================

    @FXML private ComboBox<Club> attendanceClubCombo;
    @FXML private DatePicker attendanceDatePicker;
    @FXML private VBox clubInfoCard;
    @FXML private Label selectedClubNameLabel;
    @FXML private Label selectedAdvisorLabel;
    @FXML private Label selectedMeetingTimeLabel;
    @FXML private Label selectedMembersLabel;
    @FXML private Label attendanceStatsLabel;
    @FXML private Label presentCountLabel;
    @FXML private Label absentCountLabel;
    @FXML private Label excusedCountLabel;
    @FXML private Label attendanceRateLabel;

    // Attendance table
    @FXML private TableView<AttendanceRecord> attendanceTable;
    @FXML private TableColumn<AttendanceRecord, String> memberNameColumn;
    @FXML private TableColumn<AttendanceRecord, String> memberIdColumn;
    @FXML private TableColumn<AttendanceRecord, Integer> gradeLevelColumn;
    @FXML private TableColumn<AttendanceRecord, String> attendanceStatusColumn;
    @FXML private TableColumn<AttendanceRecord, String> notesColumn;
    @FXML private TableColumn<AttendanceRecord, String> timeColumn;

    private ObservableList<Club> clubs = FXCollections.observableArrayList();
    private ObservableList<AttendanceRecord> attendanceRecords = FXCollections.observableArrayList();
    private Club selectedClub = null;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("MMM dd, yyyy");
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("h:mm a");

    /**
     * Inner class to represent an attendance record
     */
    public static class AttendanceRecord {
        private final Student student;
        private String status; // PRESENT, ABSENT, EXCUSED
        private String notes;
        private LocalDate date;

        public AttendanceRecord(Student student) {
            this.student = student;
            this.status = "ABSENT"; // Default
            this.notes = "";
            this.date = LocalDate.now();
        }

        public Student getStudent() { return student; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public String getNotes() { return notes; }
        public void setNotes(String notes) { this.notes = notes; }
        public LocalDate getDate() { return date; }
    }

    /**
     * Initialize controller
     */
    @FXML
    public void initialize() {
        log.info("Initializing Clubs Controller");

        setupClubTable();
        setupAttendanceTable();
        setupFilters();
        loadClubs();
        updateStatistics();
        setupAttendanceControls();

        log.info("Clubs Controller initialized successfully");
    }

    // ==================== Club Management Methods ====================

    /**
     * Setup club table columns
     */
    private void setupClubTable() {
        clubNameColumn.setCellValueFactory(cellData ->
            new SimpleStringProperty(cellData.getValue().getName()));

        categoryColumn.setCellValueFactory(cellData ->
            new SimpleStringProperty(cellData.getValue().getCategory()));

        advisorColumn.setCellValueFactory(cellData ->
            new SimpleStringProperty(cellData.getValue().getAdvisorName()));

        meetingDayColumn.setCellValueFactory(cellData ->
            new SimpleStringProperty(cellData.getValue().getMeetingDay()));

        meetingTimeColumn.setCellValueFactory(cellData -> {
            if (cellData.getValue().getMeetingTime() != null) {
                return new SimpleStringProperty(
                    cellData.getValue().getMeetingTime().format(TIME_FORMATTER)
                );
            }
            return new SimpleStringProperty("-");
        });

        locationColumn.setCellValueFactory(cellData ->
            new SimpleStringProperty(cellData.getValue().getLocation()));

        enrollmentColumn.setCellValueFactory(cellData -> {
            Club club = cellData.getValue();
            Integer current = club.getCurrentEnrollment() != null ? club.getCurrentEnrollment() : 0;
            Integer max = club.getMaxCapacity();
            return new SimpleStringProperty(
                max != null ? current + " / " + max : String.valueOf(current)
            );
        });

        capacityColumn.setCellValueFactory(cellData ->
            new SimpleIntegerProperty(
                cellData.getValue().getMaxCapacity() != null ?
                cellData.getValue().getMaxCapacity() : 0
            ).asObject());

        statusColumn.setCellValueFactory(cellData ->
            new SimpleStringProperty(cellData.getValue().getActive() ? "✓" : "✗"));
        statusColumn.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(item);
                    if (item.equals("✓")) {
                        setStyle("-fx-text-fill: green; -fx-font-size: 14px; -fx-font-weight: bold;");
                    } else {
                        setStyle("-fx-text-fill: red; -fx-font-size: 14px;");
                    }
                }
            }
        });

        clubsTable.setItems(clubs);
    }

    /**
     * Setup attendance table columns
     */
    private void setupAttendanceTable() {
        memberNameColumn.setCellValueFactory(cellData ->
            new SimpleStringProperty(cellData.getValue().getStudent().getFullName()));

        memberIdColumn.setCellValueFactory(cellData ->
            new SimpleStringProperty(cellData.getValue().getStudent().getStudentId()));

        gradeLevelColumn.setCellValueFactory(cellData ->
            new SimpleIntegerProperty(cellData.getValue().getStudent().getGradeLevel()).asObject());

        // Attendance status with ComboBox for editing
        attendanceStatusColumn.setCellValueFactory(cellData ->
            new SimpleStringProperty(cellData.getValue().getStatus()));
        attendanceStatusColumn.setCellFactory(column -> new TableCell<>() {
            private final ComboBox<String> comboBox = new ComboBox<>(
                FXCollections.observableArrayList("PRESENT", "ABSENT", "EXCUSED")
            );

            {
                comboBox.setOnAction(e -> {
                    AttendanceRecord record = getTableView().getItems().get(getIndex());
                    record.setStatus(comboBox.getValue());
                    updateAttendanceStatistics();
                });
            }

            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setGraphic(null);
                } else {
                    comboBox.setValue(item);
                    setGraphic(comboBox);
                }
            }
        });

        // Notes with TextField for editing
        notesColumn.setCellValueFactory(cellData ->
            new SimpleStringProperty(cellData.getValue().getNotes()));
        notesColumn.setCellFactory(column -> new TableCell<>() {
            private final TextField textField = new TextField();

            {
                textField.setOnAction(e -> {
                    AttendanceRecord record = getTableView().getItems().get(getIndex());
                    record.setNotes(textField.getText());
                });
            }

            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setGraphic(null);
                } else {
                    textField.setText(item != null ? item : "");
                    setGraphic(textField);
                }
            }
        });

        timeColumn.setCellValueFactory(cellData ->
            new SimpleStringProperty(cellData.getValue().getDate().format(DATE_FORMATTER)));

        attendanceTable.setItems(attendanceRecords);
    }

    /**
     * Setup filter controls
     */
    private void setupFilters() {
        // Category filter
        categoryFilterCombo.setItems(FXCollections.observableArrayList("All Categories"));
        categoryFilterCombo.setValue("All Categories");
        categoryFilterCombo.setOnAction(e -> applyFilters());

        // Status filter
        statusFilterCombo.setItems(FXCollections.observableArrayList(
            "All Status", "Active Only", "Inactive Only"
        ));
        statusFilterCombo.setValue("Active Only");
        statusFilterCombo.setOnAction(e -> applyFilters());

        // Capacity filter
        capacityFilterCombo.setItems(FXCollections.observableArrayList(
            "All Clubs", "Has Space", "At Capacity"
        ));
        capacityFilterCombo.setValue("All Clubs");
        capacityFilterCombo.setOnAction(e -> applyFilters());

        // Search field
        searchField.textProperty().addListener((obs, oldVal, newVal) -> applyFilters());
    }

    /**
     * Setup attendance controls
     */
    private void setupAttendanceControls() {
        // Set default date to today
        attendanceDatePicker.setValue(LocalDate.now());

        // Club selection
        attendanceClubCombo.setItems(clubs);
        attendanceClubCombo.setConverter(new javafx.util.StringConverter<Club>() {
            @Override
            public String toString(Club club) {
                return club != null ? club.getName() : "";
            }

            @Override
            public Club fromString(String string) {
                return null;
            }
        });

        attendanceClubCombo.setOnAction(e -> loadClubAttendance());
    }

    /**
     * Load clubs data
     */
    private void loadClubs() {
        try {
            List<Club> allClubs = clubService.getAllActiveClubs();
            clubs.setAll(allClubs);

            // Update category filter
            List<String> categories = clubService.getAllCategories();
            ObservableList<String> categoryItems = FXCollections.observableArrayList("All Categories");
            categoryItems.addAll(categories);
            categoryFilterCombo.setItems(categoryItems);

            log.info("Loaded {} clubs", allClubs.size());

        } catch (Exception e) {
            log.error("Error loading clubs", e);
            showError("Failed to load clubs: " + e.getMessage());
        }
    }

    /**
     * Apply filters to club list
     */
    private void applyFilters() {
        try {
            List<Club> filtered = clubService.getAllActiveClubs();

            // Filter by status
            String selectedStatus = statusFilterCombo.getValue();
            if ("Active Only".equals(selectedStatus)) {
                filtered = filtered.stream()
                    .filter(Club::getActive)
                    .collect(Collectors.toList());
            } else if ("Inactive Only".equals(selectedStatus)) {
                filtered = filtered.stream()
                    .filter(c -> !c.getActive())
                    .collect(Collectors.toList());
            }

            // Filter by category
            String selectedCategory = categoryFilterCombo.getValue();
            if (selectedCategory != null && !selectedCategory.equals("All Categories")) {
                filtered = filtered.stream()
                    .filter(c -> selectedCategory.equals(c.getCategory()))
                    .collect(Collectors.toList());
            }

            // Filter by capacity
            String selectedCapacity = capacityFilterCombo.getValue();
            if ("Has Space".equals(selectedCapacity)) {
                filtered = filtered.stream()
                    .filter(c -> !c.isAtCapacity())
                    .collect(Collectors.toList());
            } else if ("At Capacity".equals(selectedCapacity)) {
                filtered = filtered.stream()
                    .filter(Club::isAtCapacity)
                    .collect(Collectors.toList());
            }

            // Search filter
            String searchText = searchField.getText();
            if (searchText != null && !searchText.trim().isEmpty()) {
                String search = searchText.toLowerCase();
                filtered = filtered.stream()
                    .filter(c ->
                        (c.getName() != null && c.getName().toLowerCase().contains(search)) ||
                        (c.getDescription() != null && c.getDescription().toLowerCase().contains(search)) ||
                        (c.getAdvisorName() != null && c.getAdvisorName().toLowerCase().contains(search))
                    )
                    .collect(Collectors.toList());
            }

            clubs.setAll(filtered);
            updateStatistics();

        } catch (Exception e) {
            log.error("Error applying filters", e);
        }
    }

    /**
     * Clear all filters
     */
    @FXML
    public void handleClearFilters() {
        categoryFilterCombo.setValue("All Categories");
        statusFilterCombo.setValue("Active Only");
        capacityFilterCombo.setValue("All Clubs");
        searchField.clear();
        applyFilters();
    }

    /**
     * Update statistics
     */
    private void updateStatistics() {
        try {
            Map<String, Object> stats = clubService.getClubStatistics();

            Long totalClubs = (Long) stats.get("totalClubs");
            Long totalMembers = (Long) stats.get("totalMembers");
            Long clubsWithSpace = (Long) stats.get("clubsWithSpace");

            // Calculate available spots
            long availableSpots = clubService.getClubsWithAvailability().stream()
                .mapToLong(c -> {
                    Integer spots = c.getAvailableSpots();
                    return spots != null ? spots.longValue() : 0L;
                })
                .sum();

            List<String> categories = clubService.getAllCategories();

            totalClubsLabel.setText(String.valueOf(totalClubs));
            totalMembersLabel.setText(String.valueOf(totalMembers));
            availableSpotsLabel.setText(String.valueOf(availableSpots));
            categoriesLabel.setText(String.valueOf(categories.size()));
            clubCountLabel.setText(clubs.size() + " clubs");

        } catch (Exception e) {
            log.error("Error updating statistics", e);
        }
    }

    /**
     * Load club attendance for selected club
     */
    private void loadClubAttendance() {
        selectedClub = attendanceClubCombo.getValue();
        if (selectedClub == null) {
            clubInfoCard.setVisible(false);
            clubInfoCard.setManaged(false);
            attendanceRecords.clear();
            return;
        }

        // Show club info card
        clubInfoCard.setVisible(true);
        clubInfoCard.setManaged(true);
        selectedClubNameLabel.setText(selectedClub.getName());
        selectedAdvisorLabel.setText(selectedClub.getAdvisorName());
        selectedMeetingTimeLabel.setText(
            selectedClub.getMeetingDay() + " " +
            (selectedClub.getMeetingTime() != null ? selectedClub.getMeetingTime().format(TIME_FORMATTER) : "")
        );
        selectedMembersLabel.setText(String.valueOf(selectedClub.getCurrentEnrollment()));

        // Load members for attendance
        Set<Student> members = clubService.getClubMembers(selectedClub.getId());
        attendanceRecords.clear();
        members.forEach(student -> attendanceRecords.add(new AttendanceRecord(student)));

        updateAttendanceStatistics();
    }

    /**
     * Update attendance statistics
     */
    private void updateAttendanceStatistics() {
        long presentCount = attendanceRecords.stream()
            .filter(r -> "PRESENT".equals(r.getStatus()))
            .count();
        long absentCount = attendanceRecords.stream()
            .filter(r -> "ABSENT".equals(r.getStatus()))
            .count();
        long excusedCount = attendanceRecords.stream()
            .filter(r -> "EXCUSED".equals(r.getStatus()))
            .count();

        int total = attendanceRecords.size();
        double rate = total > 0 ? (presentCount * 100.0 / total) : 0.0;

        presentCountLabel.setText(String.valueOf(presentCount));
        absentCountLabel.setText(String.valueOf(absentCount));
        excusedCountLabel.setText(String.valueOf(excusedCount));
        attendanceRateLabel.setText(String.format("%.1f%%", rate));
        attendanceStatsLabel.setText(String.format("Present: %d | Absent: %d", presentCount, absentCount));
    }

    // ==================== Event Handlers ====================

    /**
     * Handle sync button
     */
    @FXML
    public void handleSync() {
        log.info("Syncing clubs data");
        try {
            loadClubs();
            updateStatistics();
            showSuccess("Clubs data refreshed successfully");
        } catch (Exception e) {
            log.error("Error syncing clubs data", e);
            showError("Failed to refresh: " + e.getMessage());
        }
    }

    /**
     * Handle view members
     */
    @FXML
    public void handleViewMembers() {
        Club selected = clubsTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showWarning("Please select a club first");
            return;
        }

        Set<Student> members = clubService.getClubMembers(selected.getId());
        StringBuilder message = new StringBuilder();
        message.append("Members of ").append(selected.getName()).append(":\n\n");

        if (members.isEmpty()) {
            message.append("No members yet.");
        } else {
            int count = 1;
            for (Student student : members) {
                message.append(String.format("%d. %s (Grade %d)\n",
                    count++, student.getFullName(), student.getGradeLevel()));
            }
        }

        showInfo("Club Members", message.toString());
    }

    /**
     * Handle manage enrollment
     */
    @FXML
    public void handleManageEnrollment() {
        showInfo("Manage Enrollment", "Enrollment management dialog will be implemented");
        // FUTURE ENHANCEMENT: Enrollment Management Dialog - Framework placeholder, dialog UI pending
    }

    /**
     * Handle view details
     */
    @FXML
    public void handleViewDetails() {
        Club selected = clubsTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showWarning("Please select a club first");
            return;
        }

        StringBuilder details = new StringBuilder();
        details.append("Club: ").append(selected.getName()).append("\n\n");
        details.append("Category: ").append(selected.getCategory()).append("\n");
        details.append("Advisor: ").append(selected.getAdvisorName()).append("\n");
        details.append("Meeting: ").append(selected.getMeetingDay()).append(" at ");
        details.append(selected.getMeetingTime() != null ? selected.getMeetingTime().format(TIME_FORMATTER) : "TBD").append("\n");
        details.append("Location: ").append(selected.getLocation()).append("\n");
        details.append("Enrollment: ").append(selected.getCurrentEnrollment()).append("/").append(selected.getMaxCapacity()).append("\n");
        details.append("\nDescription:\n").append(selected.getDescription());

        showInfo("Club Details", details.toString());
    }

    /**
     * Handle reports
     */
    @FXML
    public void handleReports() {
        showInfo("Reports", "Club reports will be implemented");
        // FUTURE ENHANCEMENT: Reports Dialog - Framework placeholder, dialog UI pending
    }

    /**
     * Handle export
     */
    @FXML
    public void handleExport() {
        showInfo("Export", "CSV export will be implemented");
        // FUTURE ENHANCEMENT: CSV Export - Framework placeholder, export feature pending
    }

    /**
     * Mark all present
     */
    @FXML
    public void handleMarkAllPresent() {
        attendanceRecords.forEach(record -> record.setStatus("PRESENT"));
        attendanceTable.refresh();
        updateAttendanceStatistics();
        showSuccess("All members marked as present");
    }

    /**
     * Save attendance
     */
    @FXML
    public void handleSaveAttendance() {
        if (selectedClub == null) {
            showWarning("Please select a club first");
            return;
        }

        LocalDate date = attendanceDatePicker.getValue();
        if (date == null) {
            showWarning("Please select a date");
            return;
        }

        // Save attendance records to database
        try {
            // Get all attendance records from table
            List<AttendanceRecord> records = attendanceTable.getItems();
            int savedCount = 0;

            // Save each attendance record
            for (AttendanceRecord record : records) {
                if (record.getStudent() != null && record.getStatus() != null) {
                    clubService.saveClubAttendance(
                        selectedClub.getId(),
                        record.getStudent().getId(),
                        date,
                        record.getStatus(),
                        record.getNotes()
                    );
                    savedCount++;
                }
            }

            showSuccess(String.format("Saved attendance for %d students in %s on %s",
                savedCount, selectedClub.getName(), date.format(DATE_FORMATTER)));
            log.info("Saved {} attendance records for club {} on {}", savedCount, selectedClub.getName(), date);

        } catch (Exception e) {
            log.error("Failed to save club attendance", e);
            showError("Failed to save attendance: " + e.getMessage());
        }
    }

    // ==================== Alert Methods ====================

    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Error");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void showInfo(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void showSuccess(String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Success");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void showWarning(String message) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle("Warning");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
