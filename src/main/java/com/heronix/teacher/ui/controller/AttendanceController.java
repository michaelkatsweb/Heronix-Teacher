package com.heronix.teacher.ui.controller;

import com.heronix.teacher.model.domain.Attendance;
import com.heronix.teacher.model.domain.Student;
import com.heronix.teacher.model.dto.ClassRosterDTO;
import com.heronix.teacher.repository.StudentRepository;
import com.heronix.teacher.service.AdminApiClient;
import com.heronix.teacher.service.AttendanceService;
import com.heronix.teacher.service.GradebookService;
import com.heronix.teacher.service.SessionManager;
import com.heronix.teacher.service.StudentEnrollmentCache;
import com.heronix.teacher.ui.dialog.StudentCardDialog;
import javafx.beans.property.SimpleStringProperty;
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

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import javafx.stage.FileChooser;

/**
 * Period-Based Attendance Controller
 *
 * Manages attendance tracking with separate tabs for each period (0-7)
 *
 * Features:
 * - 8 period tabs (Homeroom + Periods 1-7)
 * - Per-period statistics and student lists
 * - Quick attendance marking per period
 * - Date-based navigation
 *
 * @author EduScheduler Team
 * @version 2.0.0 (Period-Based)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AttendanceController {

    private final AttendanceService attendanceService;
    private final GradebookService gradebookService;
    private final AdminApiClient adminApiClient;
    private final SessionManager sessionManager;
    private final StudentRepository studentRepository;
    private final StudentEnrollmentCache studentEnrollmentCache;

    // FXML elements from Attendance.fxml
    @FXML private Label dateLabel;
    @FXML private DatePicker datePicker;
    @FXML private TabPane periodTabPane;
    @FXML private Label statusLabel;
    @FXML private Label summaryLabel;

    private LocalDate currentDate = LocalDate.now();

    // Maps to store period tabs and their data
    private final Map<Integer, Tab> periodTabs = new HashMap<>();
    private final Map<Integer, TableView<AttendanceRow>> periodTables = new HashMap<>();
    private final Map<Integer, ObservableList<AttendanceRow>> periodData = new HashMap<>();
    private final Map<Integer, Map<String, Label>> periodStatsLabels = new HashMap<>();
    private final Map<Integer, ClassRosterDTO> periodRosters = new HashMap<>();

    // Bell schedule data (loaded from server)
    private Map<String, Object> bellScheduleData = new HashMap<>();
    private Map<Integer, Map<String, Object>> periodTimings = new HashMap<>();  // period -> {startTime, endTime}

    @FXML
    public void initialize() {
        log.info("Initializing Period-Based Attendance Controller");

        // Setup date picker
        setupDatePicker();

        // Create tabs for all 8 periods (0-7)
        createPeriodTabs();

        // Load initial data
        loadAllPeriodsData();

        log.info("Period-Based Attendance Controller initialized successfully");
    }

    /**
     * Setup date picker with change listener
     */
    private void setupDatePicker() {
        datePicker.setValue(currentDate);
        dateLabel.setText(formatDate(currentDate));

        datePicker.setOnAction(event -> {
            currentDate = datePicker.getValue();
            dateLabel.setText(formatDate(currentDate));
            loadAllPeriodsData();
            statusLabel.setText("Loaded attendance for " + formatDate(currentDate));
        });
    }

    /**
     * Create tabs for all 8 periods dynamically
     */
    private void createPeriodTabs() {
        for (int period = 0; period <= 7; period++) {
            Tab tab = createPeriodTab(period);
            periodTabs.put(period, tab);
            periodTabPane.getTabs().add(tab);
        }

        // Select first tab (Homeroom) by default
        if (!periodTabPane.getTabs().isEmpty()) {
            periodTabPane.getSelectionModel().select(0);
        }
    }

    /**
     * Create a single period tab with its own table and statistics
     */
    private Tab createPeriodTab(int period) {
        String tabText = period == 0 ? "Homeroom" : "Period " + period;
        Tab tab = new Tab(tabText);

        // Create main content VBox
        VBox content = new VBox(15);
        content.setPadding(new Insets(15));
        content.getStyleClass().add("period-content");

        // Statistics cards HBox
        HBox statsBox = createStatisticsBox(period);
        content.getChildren().add(statsBox);

        // Create table for this period
        TableView<AttendanceRow> table = createPeriodTable(period);
        periodTables.put(period, table);
        VBox.setVgrow(table, javafx.scene.layout.Priority.ALWAYS);
        content.getChildren().add(table);

        // Action buttons HBox
        HBox actionsBox = createActionButtons(period);
        content.getChildren().add(actionsBox);

        tab.setContent(content);
        return tab;
    }

    /**
     * Create statistics cards for a period
     */
    private HBox createStatisticsBox(int period) {
        HBox statsBox = new HBox(15);

        Map<String, Label> labels = new HashMap<>();

        // Present card
        VBox presentCard = createStatCard("✓ PRESENT", "0", "success-text");
        labels.put("present", (Label) ((VBox) presentCard.getChildren().get(0)).getChildren().get(1));
        statsBox.getChildren().add(presentCard);

        // Absent card
        VBox absentCard = createStatCard("✗ ABSENT", "0", "danger-text");
        labels.put("absent", (Label) ((VBox) absentCard.getChildren().get(0)).getChildren().get(1));
        statsBox.getChildren().add(absentCard);

        // Tardy card
        VBox tardyCard = createStatCard("⏱ TARDY", "0", "warning-text");
        labels.put("tardy", (Label) ((VBox) tardyCard.getChildren().get(0)).getChildren().get(1));
        statsBox.getChildren().add(tardyCard);

        // Total card
        VBox totalCard = createStatCard("TOTAL", "0", "text-secondary");
        labels.put("total", (Label) ((VBox) totalCard.getChildren().get(0)).getChildren().get(1));
        statsBox.getChildren().add(totalCard);

        periodStatsLabels.put(period, labels);
        return statsBox;
    }

    /**
     * Create a single stat card
     */
    private VBox createStatCard(String title, String value, String titleStyle) {
        VBox card = new VBox(8);
        card.getStyleClass().addAll("card", "stat-card");
        card.setPadding(new Insets(10));
        HBox.setHgrow(card, javafx.scene.layout.Priority.ALWAYS);

        VBox innerBox = new VBox(5);

        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add(titleStyle);
        titleLabel.setStyle("-fx-font-size: 11px; -fx-font-weight: bold;");

        Label valueLabel = new Label(value);
        valueLabel.setStyle("-fx-font-size: 24px; -fx-font-weight: bold;");

        innerBox.getChildren().addAll(titleLabel, valueLabel);
        card.getChildren().add(innerBox);

        return card;
    }

    /**
     * Create table for a specific period
     */
    private TableView<AttendanceRow> createPeriodTable(int period) {
        TableView<AttendanceRow> table = new TableView<>();

        // Student ID column
        TableColumn<AttendanceRow, String> idCol = new TableColumn<>("Student ID");
        idCol.setCellValueFactory(data -> data.getValue().studentIdProperty());
        idCol.setPrefWidth(100);

        // Student Name column
        TableColumn<AttendanceRow, String> nameCol = new TableColumn<>("Student Name");
        nameCol.setCellValueFactory(data -> data.getValue().studentNameProperty());
        nameCol.setPrefWidth(200);

        // Status column with styling
        TableColumn<AttendanceRow, String> statusCol = new TableColumn<>("Status");
        statusCol.setCellValueFactory(data -> data.getValue().statusProperty());
        statusCol.setPrefWidth(120);
        statusCol.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(item);
                    switch (item) {
                        case "PRESENT" -> setStyle("-fx-text-fill: #4CAF50; -fx-font-weight: bold;");
                        case "ABSENT" -> setStyle("-fx-text-fill: #F44336; -fx-font-weight: bold;");
                        case "TARDY" -> setStyle("-fx-text-fill: #FF9800; -fx-font-weight: bold;");
                        case "ISS" -> setStyle("-fx-text-fill: #9C27B0; -fx-font-weight: bold;");
                        case "OSS" -> setStyle("-fx-text-fill: #880E4F; -fx-font-weight: bold;");
                        case "TESTING" -> setStyle("-fx-text-fill: #1565C0; -fx-font-weight: bold;");
                        case "MEDICAL" -> setStyle("-fx-text-fill: #E65100; -fx-font-weight: bold;");
                        case "EARLY_DISMISSAL" -> setStyle("-fx-text-fill: #6A1B9A; -fx-font-weight: bold;");
                        default -> setStyle("");
                    }
                }
            }
        });

        // Arrival Time column
        TableColumn<AttendanceRow, String> timeCol = new TableColumn<>("Arrival");
        timeCol.setCellValueFactory(data -> data.getValue().arrivalTimeProperty());
        timeCol.setPrefWidth(80);

        // Notes column
        TableColumn<AttendanceRow, String> notesCol = new TableColumn<>("Notes");
        notesCol.setCellValueFactory(data -> data.getValue().notesProperty());
        notesCol.setPrefWidth(150);

        // Actions column with buttons
        TableColumn<AttendanceRow, Void> actionsCol = new TableColumn<>("Actions");
        actionsCol.setPrefWidth(240);
        actionsCol.setCellFactory(column -> new TableCell<>() {
            private final Button presentBtn = new Button("✓");
            private final Button absentBtn = new Button("✗");
            private final Button tardyBtn = new Button("⏱");
            private final MenuButton moreBtn = new MenuButton("▼");
            private final HBox buttons = new HBox(5, presentBtn, absentBtn, tardyBtn, moreBtn);

            {
                buttons.setAlignment(Pos.CENTER);
                presentBtn.getStyleClass().add("button-success");
                absentBtn.getStyleClass().add("button-danger");
                tardyBtn.getStyleClass().add("button-warning");

                presentBtn.setStyle("-fx-min-width: 35px; -fx-min-height: 25px;");
                absentBtn.setStyle("-fx-min-width: 35px; -fx-min-height: 25px;");
                tardyBtn.setStyle("-fx-min-width: 35px; -fx-min-height: 25px;");
                moreBtn.setStyle("-fx-min-width: 35px; -fx-min-height: 25px;");

                MenuItem issItem = new MenuItem("ISS (In-School Suspension)");
                MenuItem ossItem = new MenuItem("OSS (Out-of-School Suspension)");
                MenuItem testingItem = new MenuItem("Testing");
                MenuItem medicalItem = new MenuItem("Medical");
                MenuItem earlyDismissalItem = new MenuItem("Early Dismissal");
                moreBtn.getItems().addAll(issItem, ossItem, testingItem, medicalItem, earlyDismissalItem);

                presentBtn.setOnAction(event -> {
                    AttendanceRow row = getTableView().getItems().get(getIndex());
                    markStudentPresent(row.getStudentId(), period);
                });

                absentBtn.setOnAction(event -> {
                    AttendanceRow row = getTableView().getItems().get(getIndex());
                    markStudentAbsent(row.getStudentId(), period);
                });

                tardyBtn.setOnAction(event -> {
                    AttendanceRow row = getTableView().getItems().get(getIndex());
                    markStudentTardy(row.getStudentId(), period);
                });

                issItem.setOnAction(event -> {
                    AttendanceRow row = getTableView().getItems().get(getIndex());
                    markStudentStatus(row.getStudentId(), period, "ISS", "In-School Suspension");
                });

                ossItem.setOnAction(event -> {
                    AttendanceRow row = getTableView().getItems().get(getIndex());
                    markStudentStatus(row.getStudentId(), period, "OSS", "Out-of-School Suspension");
                });

                testingItem.setOnAction(event -> {
                    AttendanceRow row = getTableView().getItems().get(getIndex());
                    markStudentStatus(row.getStudentId(), period, "TESTING", "Testing session");
                });

                medicalItem.setOnAction(event -> {
                    AttendanceRow row = getTableView().getItems().get(getIndex());
                    markStudentStatus(row.getStudentId(), period, "MEDICAL", "Medical visit");
                });

                earlyDismissalItem.setOnAction(event -> {
                    AttendanceRow row = getTableView().getItems().get(getIndex());
                    markStudentStatus(row.getStudentId(), period, "EARLY_DISMISSAL", "Early dismissal");
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : buttons);
            }
        });

        table.getColumns().addAll(idCol, nameCol, statusCol, timeCol, notesCol, actionsCol);

        // Double-click row → open Student Card dialog
        table.setRowFactory(tv -> {
            TableRow<AttendanceRow> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && !row.isEmpty()) {
                    AttendanceRow item = row.getItem();
                    Long serverId = getServerIdForStudent(item.getStudentId());
                    if (serverId != null) {
                        StudentCardDialog.show(adminApiClient, serverId,
                                item.getStudentName(), table.getScene().getWindow());
                    }
                }
            });
            return row;
        });

        // Initialize empty data list
        ObservableList<AttendanceRow> data = FXCollections.observableArrayList();
        periodData.put(period, data);
        table.setItems(data);

        return table;
    }

    /**
     * Create action buttons for a period
     */
    private HBox createActionButtons(int period) {
        HBox actionsBox = new HBox(15);
        actionsBox.setAlignment(Pos.CENTER_LEFT);

        Button markAllPresentBtn = new Button("✓ Mark All Present");
        markAllPresentBtn.getStyleClass().add("button-success");
        markAllPresentBtn.setPrefHeight(35);
        markAllPresentBtn.setPrefWidth(140);
        markAllPresentBtn.setOnAction(event -> markAllPresentForPeriod(period));

        actionsBox.getChildren().add(markAllPresentBtn);

        return actionsBox;
    }

    /**
     * Load attendance data for all periods
     * NEW: Syncs rosters from Heronix-SIS server and loads bell schedule
     */
    private void loadAllPeriodsData() {
        log.info("Loading period rosters from server...");

        // Load bell schedule first
        loadBellSchedule();

        // Get current teacher's employee ID from session
        String employeeId = sessionManager.getCurrentEmployeeId();

        if (employeeId == null) {
            log.warn("No employee ID in session - cannot load rosters");
            statusLabel.setText("Not logged in - Please log in to sync rosters");
            return;
        }

        try {
            // Try new Heronix-SIS API first (uses teacherId)
            Long teacherId = adminApiClient.getTeacherId();

            if (teacherId != null) {
                // Use new Teacher Attendance API
                java.util.Map<String, Object> response = adminApiClient.getTeacherRosters(teacherId);

                if (Boolean.TRUE.equals(response.get("success"))) {
                    periodRosters.clear();
                    convertNewRostersFormat(response);
                    log.info("Synced rosters from Heronix-SIS API for teacher ID {}", teacherId);
                    statusLabel.setText("Synced rosters from Heronix-SIS");
                } else {
                    // Fall back to legacy API
                    loadRostersLegacy(employeeId);
                }
            } else {
                // No teacherId, use legacy API
                loadRostersLegacy(employeeId);
            }

            // Update shared enrollment cache for Gradebook/Assignment dialogs
            studentEnrollmentCache.updateRosters(periodRosters);

            // Load each period's data
            for (int period = 0; period <= 7; period++) {
                loadPeriodData(period);
            }

            updateSummary();

        } catch (Exception e) {
            log.error("Failed to sync rosters from server", e);
            statusLabel.setText("Error syncing rosters: " + e.getMessage());

            // Fall back to local data if sync fails
            for (int period = 0; period <= 7; period++) {
                loadPeriodData(period);
            }
            updateSummary();
        }
    }

    /**
     * Load bell schedule from server
     */
    private void loadBellSchedule() {
        try {
            bellScheduleData = adminApiClient.getBellSchedule();

            if (Boolean.TRUE.equals(bellScheduleData.get("success"))) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> periods = (List<Map<String, Object>>) bellScheduleData.get("periods");

                if (periods != null) {
                    periodTimings.clear();
                    for (Map<String, Object> period : periods) {
                        Integer periodNum = (Integer) period.get("periodNumber");
                        periodTimings.put(periodNum, period);
                    }
                    log.info("Loaded bell schedule with {} periods", periodTimings.size());
                }
            }
        } catch (Exception e) {
            log.warn("Could not load bell schedule: {}", e.getMessage());
        }
    }

    /**
     * Load rosters using legacy API (employeeId-based)
     */
    private void loadRostersLegacy(String employeeId) throws Exception {
        java.util.Map<Integer, ClassRosterDTO> rosters = adminApiClient.getAllRosters(employeeId);
        periodRosters.clear();
        periodRosters.putAll(rosters);
        log.info("Synced {} period rosters from legacy API", rosters.size());
        statusLabel.setText("Synced " + rosters.size() + " class rosters from server");
    }

    /**
     * Convert new Heronix-SIS roster format to ClassRosterDTO format
     */
    @SuppressWarnings("unchecked")
    private void convertNewRostersFormat(Map<String, Object> response) {
        List<Map<String, Object>> rosters = (List<Map<String, Object>>) response.get("rosters");

        if (rosters == null) return;

        for (Map<String, Object> roster : rosters) {
            Integer period = (Integer) roster.get("period");
            if (period == null) continue;

            // Convert to ClassRosterDTO
            ClassRosterDTO dto = new ClassRosterDTO();
            dto.setCourseCode((String) roster.get("courseCode"));
            dto.setCourseName((String) roster.get("courseName"));
            dto.setSectionId(getLongValue(roster, "sectionId"));
            dto.setPeriod(period);
            dto.setRoomNumber((String) roster.get("roomNumber"));

            // Convert students
            List<Map<String, Object>> students = (List<Map<String, Object>>) roster.get("students");
            if (students != null) {
                List<ClassRosterDTO.RosterStudentDTO> studentDtos = new java.util.ArrayList<>();
                for (Map<String, Object> student : students) {
                    ClassRosterDTO.RosterStudentDTO studentDto = new ClassRosterDTO.RosterStudentDTO();
                    studentDto.setStudentId(getLongValue(student, "id"));
                    studentDto.setStudentNumber((String) student.get("studentId"));
                    studentDto.setFirstName((String) student.get("firstName"));
                    studentDto.setLastName((String) student.get("lastName"));
                    studentDto.setGradeLevel((String) student.get("gradeLevel"));
                    studentDto.setEmail((String) student.get("email"));
                    studentDtos.add(studentDto);
                }
                dto.setStudents(studentDtos);
            }

            periodRosters.put(period, dto);
        }
    }

    /**
     * Helper to get Long value from map (handles Integer/Long conversion)
     */
    private Long getLongValue(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) return null;
        if (value instanceof Long) return (Long) value;
        if (value instanceof Integer) return ((Integer) value).longValue();
        return null;
    }

    /**
     * Load attendance data for a specific period
     * NEW: Uses synced roster data from server
     */
    private void loadPeriodData(int period) {
        log.debug("Loading attendance data for period {} on {}", period, currentDate);

        ObservableList<AttendanceRow> data = periodData.get(period);
        data.clear();

        // Check if we have a roster for this period from the server
        ClassRosterDTO roster = periodRosters.get(period);

        List<Student> students;
        if (roster != null && roster.getStudents() != null) {
            // Use synced roster from server
            log.debug("Using synced roster for period {} with {} students",
                    period, roster.getStudents().size());

            // Convert RosterStudentDTOs to local Students
            students = convertRosterStudentsToLocal(roster.getStudents());
        } else {
            // Fall back to local data (planning period, lunch, or sync failed)
            log.debug("No roster from server for period {}, using local data", period);
            students = attendanceService.getStudentsForPeriod(period);
        }

        // Get existing attendance records for this period
        List<Attendance> attendanceRecords = attendanceService.getAttendanceByDateAndPeriod(currentDate, period);

        for (Student student : students) {
            Optional<Attendance> record = attendanceRecords.stream()
                    .filter(a -> a.getStudent().getId().equals(student.getId()))
                    .findFirst();

            AttendanceRow row;
            if (record.isPresent()) {
                row = AttendanceRow.fromAttendance(record.get());
            } else {
                // Student not yet marked for this period
                row = new AttendanceRow(
                        student.getId(),
                        student.getStudentId(),
                        student.getFullName(),
                        "UNMARKED",
                        "",
                        ""
                );
            }

            data.add(row);
        }

        // Update statistics for this period
        updatePeriodStatistics(period);

        log.debug("Loaded {} students for period {}", students.size(), period);
    }

    /**
     * Convert RosterStudentDTOs from server to local Student entities.
     * Persists students to the local DB (upsert) so attendance marking can find them.
     */
    private List<Student> convertRosterStudentsToLocal(List<ClassRosterDTO.RosterStudentDTO> rosterStudents) {
        List<Student> students = new java.util.ArrayList<>();

        for (ClassRosterDTO.RosterStudentDTO rosterStudent : rosterStudents) {
            String studentNumber = rosterStudent.getStudentNumber();
            if (studentNumber == null || studentNumber.isEmpty()) {
                log.warn("Skipping roster student with no student number");
                continue;
            }

            // Look up by studentId string (e.g., "S9001")
            Optional<Student> existingOpt = studentRepository.findByStudentId(studentNumber);

            Student student;
            boolean dirty = false;

            if (existingOpt.isPresent()) {
                // Update existing student if fields changed
                student = existingOpt.get();
                if (rosterStudent.getFirstName() != null && !rosterStudent.getFirstName().equals(student.getFirstName())) {
                    student.setFirstName(rosterStudent.getFirstName());
                    dirty = true;
                }
                if (rosterStudent.getLastName() != null && !rosterStudent.getLastName().equals(student.getLastName())) {
                    student.setLastName(rosterStudent.getLastName());
                    dirty = true;
                }
                if (rosterStudent.getEmail() != null && !rosterStudent.getEmail().equals(student.getEmail())) {
                    student.setEmail(rosterStudent.getEmail());
                    dirty = true;
                }
                if (rosterStudent.getStudentId() != null && !rosterStudent.getStudentId().equals(student.getServerId())) {
                    student.setServerId(rosterStudent.getStudentId());
                    dirty = true;
                }
                if (dirty) {
                    student = studentRepository.save(student);
                }
            } else {
                // Create new student — let H2 auto-generate the local ID
                student = new Student();
                student.setStudentId(studentNumber);
                student.setFirstName(rosterStudent.getFirstName());
                student.setLastName(rosterStudent.getLastName());
                student.setEmail(rosterStudent.getEmail());
                student.setServerId(rosterStudent.getStudentId());
                student.setActive(true);
                student.setSyncStatus("synced");

                // Convert gradeLevel from String to Integer
                String gradeStr = rosterStudent.getGradeLevel();
                if (gradeStr != null && !gradeStr.isEmpty()) {
                    try {
                        student.setGradeLevel(Integer.parseInt(gradeStr));
                    } catch (NumberFormatException e) {
                        log.warn("Could not parse grade level: {}", gradeStr);
                    }
                }

                student = studentRepository.save(student);
                log.debug("Persisted new student: {} ({})", student.getFullName(), studentNumber);
            }

            students.add(student);
        }

        return students;
    }

    /**
     * Update statistics labels for a period
     */
    private static final Set<String> PRESENT_STATUSES = Set.of(
            "PRESENT", "TARDY", "REMOTE", "TESTING", "ISS", "SCHOOL_ACTIVITY");
    private static final Set<String> ABSENT_STATUSES = Set.of(
            "ABSENT", "EXCUSED_ABSENT", "UNEXCUSED_ABSENT", "OSS", "MEDICAL", "EARLY_DISMISSAL");

    private void updatePeriodStatistics(int period) {
        ObservableList<AttendanceRow> data = periodData.get(period);
        Map<String, Label> labels = periodStatsLabels.get(period);

        long presentCount = data.stream().filter(r -> PRESENT_STATUSES.contains(r.getStatus())).count();
        long absentCount = data.stream().filter(r -> ABSENT_STATUSES.contains(r.getStatus())).count();
        long tardyCount = data.stream().filter(r -> "TARDY".equals(r.getStatus())).count();
        long totalCount = data.size();

        labels.get("present").setText(String.valueOf(presentCount));
        labels.get("absent").setText(String.valueOf(absentCount));
        labels.get("tardy").setText(String.valueOf(tardyCount));
        labels.get("total").setText(String.valueOf(totalCount));
    }

    /**
     * Update summary label with overall statistics
     */
    private void updateSummary() {
        long totalPresent = 0;
        long totalAbsent = 0;
        long totalTardy = 0;

        for (ObservableList<AttendanceRow> data : periodData.values()) {
            totalPresent += data.stream().filter(r -> PRESENT_STATUSES.contains(r.getStatus())).count();
            totalAbsent += data.stream().filter(r -> ABSENT_STATUSES.contains(r.getStatus())).count();
            totalTardy += data.stream().filter(r -> "TARDY".equals(r.getStatus())).count();
        }

        summaryLabel.setText(String.format("All Periods: %d Present, %d Absent, %d Tardy",
                totalPresent, totalAbsent, totalTardy));
    }

    // === Server Submission ===

    /**
     * Submit attendance records to SIS Server (fire-and-forget).
     */
    private void submitAttendanceToServer(int period, List<Map<String, Object>> records) {
        ClassRosterDTO roster = periodRosters.get(period);
        if (roster == null || roster.getSectionId() == null) {
            log.debug("No section ID for period {} - skipping server submission", period);
            return;
        }
        new Thread(() -> {
            try {
                adminApiClient.submitBulkAttendance(
                        roster.getSectionId(),
                        currentDate.toString(),
                        period,
                        sessionManager.getCurrentEmployeeId(),
                        records);
                log.info("Submitted attendance to server for period {}", period);
            } catch (Exception e) {
                log.warn("Failed to submit attendance to server for period {}: {}", period, e.getMessage());
            }
        }).start();
    }

    /**
     * Build a single attendance record map for server submission.
     */
    private Map<String, Object> buildServerRecord(Long serverId, String status, String notes) {
        Map<String, Object> record = new HashMap<>();
        record.put("studentId", serverId);
        record.put("status", status);
        if (notes != null) record.put("notes", notes);
        return record;
    }

    /**
     * Look up the server ID for a local student.
     */
    private Long getServerIdForStudent(Long localStudentId) {
        return studentRepository.findById(localStudentId)
                .map(Student::getServerId)
                .orElse(null);
    }

    // === Action Handlers ===

    /**
     * Mark student present for a specific period
     */
    private void markStudentPresent(Long studentId, int period) {
        try {
            attendanceService.markPresentForPeriod(studentId, currentDate, period);
            loadPeriodData(period);
            updateSummary();
            statusLabel.setText("Student marked present for Period " + period);

            // Submit to server
            Long serverId = getServerIdForStudent(studentId);
            if (serverId != null) {
                submitAttendanceToServer(period, List.of(buildServerRecord(serverId, "PRESENT", null)));
            }
        } catch (Exception e) {
            log.error("Failed to mark student present", e);
            showError("Error", "Failed to mark student present: " + e.getMessage());
        }
    }

    /**
     * Mark student absent for a specific period
     */
    private void markStudentAbsent(Long studentId, int period) {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Mark Absent");
        dialog.setHeaderText("Mark Student Absent - Period " + period);
        dialog.setContentText("Reason (optional):");

        dialog.showAndWait().ifPresent(reason -> {
            try {
                attendanceService.markAbsentForPeriod(studentId, currentDate, period, reason);
                loadPeriodData(period);
                updateSummary();
                statusLabel.setText("Student marked absent for Period " + period);

                // Submit to server
                Long serverId = getServerIdForStudent(studentId);
                if (serverId != null) {
                    submitAttendanceToServer(period, List.of(buildServerRecord(serverId, "ABSENT", reason)));
                }
            } catch (Exception e) {
                log.error("Failed to mark student absent", e);
                showError("Error", "Failed to mark student absent: " + e.getMessage());
            }
        });
    }

    /**
     * Mark student tardy for a specific period
     */
    private void markStudentTardy(Long studentId, int period) {
        TextInputDialog dialog = new TextInputDialog(LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm")));
        dialog.setTitle("Mark Tardy");
        dialog.setHeaderText("Mark Student Tardy - Period " + period);
        dialog.setContentText("Arrival time (HH:mm):");

        dialog.showAndWait().ifPresent(timeStr -> {
            try {
                LocalTime arrivalTime = LocalTime.parse(timeStr, DateTimeFormatter.ofPattern("HH:mm"));
                attendanceService.markTardyForPeriod(studentId, currentDate, period, arrivalTime, "Arrived late");
                loadPeriodData(period);
                updateSummary();
                statusLabel.setText("Student marked tardy for Period " + period);

                // Submit to server
                Long serverId = getServerIdForStudent(studentId);
                if (serverId != null) {
                    submitAttendanceToServer(period, List.of(buildServerRecord(serverId, "TARDY", "Arrived late")));
                }
            } catch (Exception e) {
                log.error("Failed to mark student tardy", e);
                showError("Error", "Failed to mark student tardy: " + e.getMessage());
            }
        });
    }

    /**
     * Mark student with a special status (ISS, OSS, TESTING) for a specific period
     */
    private void markStudentStatus(Long studentId, int period, String status, String defaultNote) {
        TextInputDialog dialog = new TextInputDialog(defaultNote);
        dialog.setTitle("Mark " + status);
        dialog.setHeaderText("Mark Student " + status + " - Period " + period);
        dialog.setContentText("Notes:");

        dialog.showAndWait().ifPresent(notes -> {
            try {
                attendanceService.markStatusForPeriod(studentId, currentDate, period, status, notes);
                loadPeriodData(period);
                updateSummary();
                statusLabel.setText("Student marked " + status + " for Period " + period);
            } catch (Exception e) {
                log.error("Failed to mark student {}", status, e);
                showError("Error", "Failed to mark student " + status + ": " + e.getMessage());
            }
        });
    }

    /**
     * Mark all students present for a specific period
     */
    private void markAllPresentForPeriod(int period) {
        String periodName = period == 0 ? "Homeroom" : "Period " + period;

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Mark All Present");
        confirm.setHeaderText("Mark All Students Present?");
        confirm.setContentText("This will mark all unmarked students as present for " + periodName);

        confirm.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                ObservableList<AttendanceRow> data = periodData.get(period);
                int marked = 0;
                List<Map<String, Object>> serverRecords = new java.util.ArrayList<>();

                for (AttendanceRow row : data) {
                    if ("UNMARKED".equals(row.getStatus())) {
                        try {
                            attendanceService.markPresentForPeriod(row.getStudentId(), currentDate, period);
                            marked++;

                            // Collect server records for bulk submission
                            Long serverId = getServerIdForStudent(row.getStudentId());
                            if (serverId != null) {
                                serverRecords.add(buildServerRecord(serverId, "PRESENT", null));
                            }
                        } catch (Exception e) {
                            log.error("Failed to mark student present: {}", e.getMessage());
                        }
                    }
                }

                statusLabel.setText("Marked " + marked + " students present for " + periodName);
                loadPeriodData(period);
                updateSummary();

                // Submit all records to server in one batch
                if (!serverRecords.isEmpty()) {
                    submitAttendanceToServer(period, serverRecords);
                }
            }
        });
    }

    /**
     * Export attendance for all periods to CSV
     */
    @FXML
    private void exportAttendance() {
        log.info("Exporting attendance for all periods (encrypted)");
        statusLabel.setText("Exporting attendance...");

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Export Attendance");
        fileChooser.setInitialFileName("attendance_" + currentDate.format(DateTimeFormatter.ofPattern("yyyyMMdd")) +
                "_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("HHmmss")) + ".heronix");
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Encrypted Files", "*.heronix")
        );

        File file = fileChooser.showSaveDialog(periodTabPane.getScene().getWindow());

        if (file != null) {
            try {
                java.io.StringWriter sw = new java.io.StringWriter();
                int totalRecords = 0;
                try (PrintWriter writer = new PrintWriter(sw)) {
                    writer.write('\ufeff');
                    writer.println("Date,Period,Student Name,Student ID,Status,Arrival Time,Notes");

                    for (int period = 0; period <= 7; period++) {
                        ObservableList<AttendanceRow> data = periodData.get(period);
                        if (data != null && !data.isEmpty()) {
                            String periodName = period == 0 ? "Homeroom" : "Period " + period;

                            for (AttendanceRow row : data) {
                                writer.println(String.format("%s,%s,%s,%s,%s,%s,%s",
                                        currentDate.format(DateTimeFormatter.ISO_LOCAL_DATE),
                                        periodName,
                                        escapeCSV(row.getStudentName()),
                                        escapeCSV(row.getStudentIdStr()),
                                        row.getStatus(),
                                        row.arrivalTimeProperty().get() != null ? row.arrivalTimeProperty().get() : "",
                                        escapeCSV(row.notesProperty().get())
                                ));
                                totalRecords++;
                            }
                        }
                    }
                }
                String originalName = file.getName().replace(".heronix", ".csv");
                byte[] encrypted = com.heronix.teacher.security.HeronixEncryptionService.getInstance()
                        .encryptFile(sw.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8), originalName);
                java.nio.file.Files.write(file.toPath(), encrypted);

                log.info("Attendance exported (encrypted) to {} - {} records", file.getAbsolutePath(), totalRecords);

                Alert alert = new Alert(Alert.AlertType.INFORMATION);
                alert.setTitle("Export Successful");
                alert.setHeaderText("Attendance Exported");
                alert.setContentText(String.format("Exported %d attendance records to:\n%s (encrypted)",
                        totalRecords, file.getName()));
                alert.showAndWait();

                statusLabel.setText("Export complete - " + totalRecords + " records");

            } catch (Exception e) {
                log.error("Error exporting attendance", e);
                showError("Export Failed", "Failed to export attendance: " + e.getMessage());
                statusLabel.setText("Export failed");
            }
        } else {
            statusLabel.setText("Export cancelled");
        }
    }

    /**
     * Escape CSV value to handle commas, quotes, and newlines
     */
    private String escapeCSV(String value) {
        if (value == null) return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    /**
     * Format date for display
     */
    private String formatDate(LocalDate date) {
        if (date.equals(LocalDate.now())) {
            return "Today - " + date.format(DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy"));
        } else {
            return date.format(DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy"));
        }
    }

    /**
     * Show error dialog
     */
    private void showError(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }

    // === Inner Class for Table Data ===

    public static class AttendanceRow {
        private final Long studentId;
        private final SimpleStringProperty studentIdStr;
        private final SimpleStringProperty studentName;
        private final SimpleStringProperty status;
        private final SimpleStringProperty arrivalTime;
        private final SimpleStringProperty notes;

        public AttendanceRow(Long studentId, String studentIdStr, String studentName,
                             String status, String arrivalTime, String notes) {
            this.studentId = studentId;
            this.studentIdStr = new SimpleStringProperty(studentIdStr);
            this.studentName = new SimpleStringProperty(studentName);
            this.status = new SimpleStringProperty(status);
            this.arrivalTime = new SimpleStringProperty(arrivalTime);
            this.notes = new SimpleStringProperty(notes);
        }

        public static AttendanceRow fromAttendance(Attendance attendance) {
            String arrivalTimeStr = attendance.getArrivalTime() != null ?
                    attendance.getArrivalTime().format(DateTimeFormatter.ofPattern("HH:mm")) : "";

            String notesStr = attendance.getNotes() != null ? attendance.getNotes() : "";

            return new AttendanceRow(
                    attendance.getStudent().getId(),
                    attendance.getStudent().getStudentId(),
                    attendance.getStudent().getFullName(),
                    attendance.getStatus(),
                    arrivalTimeStr,
                    notesStr
            );
        }

        public Long getStudentId() {
            return studentId;
        }

        public String getStudentIdStr() {
            return studentIdStr.get();
        }

        public String getStatus() {
            return status.get();
        }

        public String getStudentName() {
            return studentName.get();
        }

        public SimpleStringProperty studentIdProperty() {
            return studentIdStr;
        }

        public SimpleStringProperty studentNameProperty() {
            return studentName;
        }

        public SimpleStringProperty statusProperty() {
            return status;
        }

        public SimpleStringProperty arrivalTimeProperty() {
            return arrivalTime;
        }

        public SimpleStringProperty notesProperty() {
            return notes;
        }
    }
}
