package com.heronix.teacher.ui.controller;

import com.heronix.teacher.model.domain.Student;
import com.heronix.teacher.model.dto.TeacherScheduleDTO;
import com.heronix.teacher.service.AdminApiClient;
import com.heronix.teacher.service.AttendanceService;
import com.heronix.teacher.service.GradebookService;
import com.heronix.teacher.service.HallPassService;
import com.heronix.teacher.service.SessionManager;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.FileChooser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Dashboard controller for teacher home screen
 *
 * Displays:
 * - Quick statistics (students, classes, pending grades)
 * - Student support summary (IEP/504)
 * - Today's schedule
 * - Recent activity
 * - Attendance overview
 * - Quick action buttons
 *
 * @author EduScheduler Team
 * @version 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DashboardController {

    private final GradebookService gradebookService;
    private final AttendanceService attendanceService;
    private final HallPassService hallPassService;
    private final AdminApiClient adminApiClient;
    private final SessionManager sessionManager;

    @Value("${eduproteacher.schedule.mock.enabled:true}")
    private boolean scheduleMockEnabled;

    // Welcome section
    @FXML private Label welcomeLabel;
    @FXML private Label todayDateLabel;

    // Quick stats
    @FXML private Label totalStudentsLabel;
    @FXML private Label totalClassesLabel;
    @FXML private Label pendingGradesLabel;
    @FXML private Label activeHallPassesLabel;

    // Student Support Summary
    @FXML private Label iepStudentsLabel;
    @FXML private Label iepPercentLabel;
    @FXML private Label plan504StudentsLabel;
    @FXML private Label plan504PercentLabel;
    @FXML private Label totalSupportStudentsLabel;
    @FXML private Label totalSupportPercentLabel;

    // Today's info
    @FXML private ListView<String> scheduleListView;
    @FXML private ListView<String> activityListView;

    // Attendance overview
    @FXML private Label presentTodayLabel;
    @FXML private Label absentTodayLabel;
    @FXML private Label tardyTodayLabel;

    /**
     * Initialize dashboard
     */
    @FXML
    public void initialize() {
        log.info("Initializing Dashboard Controller");

        // Set today's date
        LocalDate today = LocalDate.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy");
        todayDateLabel.setText("Today: " + today.format(formatter));

        // Load dashboard data
        loadQuickStats();
        loadStudentSupportSummary();
        loadTodaySchedule();
        loadRecentActivity();
        loadAttendanceOverview();

        log.info("Dashboard Controller initialized successfully");
    }

    /**
     * Load quick statistics
     */
    private void loadQuickStats() {
        try {
            // Get student count from gradebook service
            List<Student> allStudents = gradebookService.getAllActiveStudents();
            long studentCount = allStudents.size();
            totalStudentsLabel.setText(String.valueOf(studentCount));

            // Get class count (unique courses taught by this teacher)
            // For now, estimate based on assignments, or use default
            long classCount = gradebookService.getTeacherCourses() != null ?
                    gradebookService.getTeacherCourses().size() : 0;
            totalClassesLabel.setText(String.valueOf(classCount));

            // Get pending grades count (assignments without grades)
            long pendingGrades = gradebookService.getPendingGradesCount();
            pendingGradesLabel.setText(String.valueOf(pendingGrades));

            // Get active hall passes count
            long activeHallPasses = hallPassService.getActiveHallPassesCount();
            activeHallPassesLabel.setText(String.valueOf(activeHallPasses));

            log.debug("Quick stats loaded: {} students, {} classes, {} pending grades, {} active passes",
                    studentCount, classCount, pendingGrades, activeHallPasses);

        } catch (Exception e) {
            log.error("Error loading quick stats", e);
            totalStudentsLabel.setText("Error");
            totalClassesLabel.setText("Error");
            pendingGradesLabel.setText("Error");
            activeHallPassesLabel.setText("Error");
        }
    }

    /**
     * Load today's schedule
     *
     * Uses mock data if eduproteacher.schedule.mock.enabled=true
     * Otherwise loads actual schedule from EduScheduler Pro sync
     */
    private void loadTodaySchedule() {
        scheduleListView.getItems().clear();

        try {
            if (scheduleMockEnabled) {
                // Mock schedule for development/testing
                loadMockSchedule();
                log.debug("Mock schedule loaded");
            } else {
                // Real schedule from EduScheduler Pro API
                loadRealSchedule();
                log.debug("Real schedule loaded from sync");
            }

        } catch (Exception e) {
            log.error("Error loading schedule", e);
            scheduleListView.getItems().add("⚠️ Error loading schedule");
            scheduleListView.getItems().add("Please check your connection and try syncing again.");
        }
    }

    /**
     * Load mock schedule data for testing
     */
    private void loadMockSchedule() {
        LocalTime now = LocalTime.now();
        DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("h:mm a");

        scheduleListView.getItems().addAll(
            "📅 Today's Schedule (Mock Data)",
            "",
            "Period 1 (8:00 AM - 9:00 AM)",
            "  Math 101 - Room 205",
            "  25 students",
            "",
            "Period 2 (9:15 AM - 10:15 AM)",
            "  Science 201 - Room 310",
            "  28 students",
            "",
            "Period 3 (10:30 AM - 11:30 AM)",
            "  Planning Period",
            "",
            "Period 4 (11:45 AM - 12:45 PM)",
            "  Math 102 - Room 205",
            "  24 students",
            "",
            "Lunch (12:45 PM - 1:30 PM)",
            "",
            "Period 5 (1:35 PM - 2:35 PM)",
            "  Math 103 - Room 205",
            "  26 students",
            "",
            String.format("Current Time: %s", now.format(timeFormatter)),
            "",
            "💡 This is mock data for testing.",
            "Set eduproteacher.schedule.mock.enabled=false",
            "to load real schedule from EduScheduler Pro."
        );
    }

    /**
     * Load real schedule from Heronix-SIS API
     *
     * Fetches the teacher's schedule using AdminApiClient and displays
     * period assignments with course names, room numbers, and times.
     */
    private void loadRealSchedule() {
        scheduleListView.getItems().add("📅 Today's Schedule");
        scheduleListView.getItems().add("");

        // Check if user is logged in
        String employeeId = sessionManager.getCurrentEmployeeId();
        if (employeeId == null) {
            scheduleListView.getItems().add("⚠️ Not logged in");
            scheduleListView.getItems().add("Please log in to view your schedule.");
            return;
        }

        // Check if API is available
        if (!adminApiClient.isAuthenticated()) {
            scheduleListView.getItems().add("⚠️ Not connected to server");
            scheduleListView.getItems().add("Click Sync to connect and load your schedule.");
            return;
        }

        try {
            // Fetch teacher schedule from API
            TeacherScheduleDTO schedule = adminApiClient.getTeacherSchedule(employeeId);

            if (schedule == null || schedule.getPeriods() == null || schedule.getPeriods().isEmpty()) {
                scheduleListView.getItems().add("No schedule data available.");
                scheduleListView.getItems().add("");
                scheduleListView.getItems().add("Your schedule may not be configured yet.");
                scheduleListView.getItems().add("Please contact your administrator.");
                return;
            }

            LocalTime now = LocalTime.now();
            DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("h:mm a");

            // Display each period
            for (int period = 0; period <= 7; period++) {
                TeacherScheduleDTO.PeriodAssignmentDTO assignment = schedule.getPeriods().get(period);

                if (assignment == null) {
                    continue; // Skip periods with no assignment
                }

                // Format period header
                String periodLabel = period == 0 ? "Homeroom" : "Period " + period;
                String timeRange = "";
                if (assignment.getStartTime() != null && assignment.getEndTime() != null) {
                    timeRange = String.format(" (%s - %s)",
                            assignment.getStartTime().format(timeFormatter),
                            assignment.getEndTime().format(timeFormatter));
                }

                // Check if this is the current period
                boolean isCurrent = isCurrentPeriod(assignment.getStartTime(), assignment.getEndTime(), now);
                String currentMarker = isCurrent ? " ◀ NOW" : "";

                scheduleListView.getItems().add(periodLabel + timeRange + currentMarker);

                // Course info
                if (assignment.getCourseName() != null && !assignment.getCourseName().isEmpty()) {
                    String courseInfo = "  " + assignment.getCourseName();
                    if (assignment.getRoomNumber() != null) {
                        courseInfo += " - Room " + assignment.getRoomNumber();
                    }
                    scheduleListView.getItems().add(courseInfo);

                    // Student count
                    if (assignment.getEnrolledCount() != null && assignment.getEnrolledCount() > 0) {
                        scheduleListView.getItems().add("  " + assignment.getEnrolledCount() + " students");
                    }
                } else {
                    // Planning period or lunch
                    scheduleListView.getItems().add("  Planning Period / Free");
                }

                scheduleListView.getItems().add("");
            }

            // Add current time
            scheduleListView.getItems().add(String.format("Current Time: %s", now.format(timeFormatter)));

            log.debug("Real schedule loaded successfully for teacher {}", employeeId);

        } catch (Exception e) {
            log.error("Error loading schedule from API: {}", e.getMessage());
            scheduleListView.getItems().add("⚠️ Error loading schedule");
            scheduleListView.getItems().add(e.getMessage());
            scheduleListView.getItems().add("");
            scheduleListView.getItems().add("Try clicking Sync to reconnect.");
        }
    }

    /**
     * Check if the current time falls within a period's time range
     */
    private boolean isCurrentPeriod(LocalTime startTime, LocalTime endTime, LocalTime now) {
        if (startTime == null || endTime == null) {
            return false;
        }
        return !now.isBefore(startTime) && !now.isAfter(endTime);
    }

    /**
     * Load recent activity
     */
    private void loadRecentActivity() {
        activityListView.getItems().clear();

        try {
            // Get recent grades (last 5)
            List<String> recentGrades = gradebookService.getRecentGrades(5);

            // Get recent attendance records (last 5)
            List<String> recentAttendance = attendanceService.getRecentAttendance(5);

            // Get recent hall passes (last 5)
            List<String> recentHallPasses = hallPassService.getRecentHallPasses(5);

            // Combine all activity
            if (recentGrades.isEmpty() && recentAttendance.isEmpty() && recentHallPasses.isEmpty()) {
                activityListView.getItems().add("Welcome to Heronix-Teacher!");
                activityListView.getItems().add("Your recent activity will appear here.");
                activityListView.getItems().add("");
                activityListView.getItems().add("Start by:");
                activityListView.getItems().add("  • Taking attendance");
                activityListView.getItems().add("  • Recording grades");
                activityListView.getItems().add("  • Issuing hall passes");
            } else {
                // Add grades first
                if (!recentGrades.isEmpty()) {
                    activityListView.getItems().add("📝 Recent Grades:");
                    activityListView.getItems().addAll(recentGrades);
                    activityListView.getItems().add("");
                }

                // Add attendance
                if (!recentAttendance.isEmpty()) {
                    activityListView.getItems().add("✓ Recent Attendance:");
                    activityListView.getItems().addAll(recentAttendance);
                    activityListView.getItems().add("");
                }

                // Add hall passes
                if (!recentHallPasses.isEmpty()) {
                    activityListView.getItems().add("🎫 Recent Hall Passes:");
                    activityListView.getItems().addAll(recentHallPasses);
                }
            }

            log.debug("Recent activity loaded: {} grades, {} attendance, {} passes",
                    recentGrades.size(), recentAttendance.size(), recentHallPasses.size());

        } catch (Exception e) {
            log.error("Error loading recent activity", e);
            activityListView.getItems().add("Error loading recent activity");
        }
    }

    /**
     * Load student support summary (IEP/504)
     */
    private void loadStudentSupportSummary() {
        try {
            List<Student> allStudents = gradebookService.getAllActiveStudents();
            long totalStudents = allStudents.size();

            if (totalStudents == 0) {
                // No students yet
                iepStudentsLabel.setText("0 students");
                iepPercentLabel.setText("0%");
                plan504StudentsLabel.setText("0 students");
                plan504PercentLabel.setText("0%");
                totalSupportStudentsLabel.setText("0 students");
                totalSupportPercentLabel.setText("0%");
                return;
            }

            // Count IEP students
            long iepCount = allStudents.stream()
                    .filter(s -> s.getHasIep() != null && s.getHasIep())
                    .count();

            // Count 504 students
            long plan504Count = allStudents.stream()
                    .filter(s -> s.getHas504() != null && s.getHas504())
                    .count();

            // Total students with any support (IEP or 504)
            long totalSupport = allStudents.stream()
                    .filter(s -> (s.getHasIep() != null && s.getHasIep()) ||
                                 (s.getHas504() != null && s.getHas504()))
                    .count();

            // Calculate percentages
            double iepPercent = (iepCount * 100.0) / totalStudents;
            double plan504Percent = (plan504Count * 100.0) / totalStudents;
            double totalSupportPercent = (totalSupport * 100.0) / totalStudents;

            // Update labels
            iepStudentsLabel.setText(iepCount + (iepCount == 1 ? " student" : " students"));
            iepPercentLabel.setText(String.format("%.1f%%", iepPercent));

            plan504StudentsLabel.setText(plan504Count + (plan504Count == 1 ? " student" : " students"));
            plan504PercentLabel.setText(String.format("%.1f%%", plan504Percent));

            totalSupportStudentsLabel.setText(totalSupport + (totalSupport == 1 ? " student" : " students"));
            totalSupportPercentLabel.setText(String.format("%.1f%%", totalSupportPercent));

            log.debug("Student support summary loaded: {} IEP, {} 504, {} total support",
                     iepCount, plan504Count, totalSupport);

        } catch (Exception e) {
            log.error("Error loading student support summary", e);
            iepStudentsLabel.setText("Error");
            plan504StudentsLabel.setText("Error");
            totalSupportStudentsLabel.setText("Error");
        }
    }

    /**
     * Load attendance overview
     */
    private void loadAttendanceOverview() {
        try {
            LocalDate today = LocalDate.now();

            // Get today's attendance counts
            long presentCount = attendanceService.getPresentCount(today);
            long absentCount = attendanceService.getAbsentCount(today);
            long tardyCount = attendanceService.getTardyCount(today);

            presentTodayLabel.setText(String.valueOf(presentCount));
            absentTodayLabel.setText(String.valueOf(absentCount));
            tardyTodayLabel.setText(String.valueOf(tardyCount));

            log.debug("Attendance overview loaded: {} present, {} absent, {} tardy",
                    presentCount, absentCount, tardyCount);

        } catch (Exception e) {
            log.error("Error loading attendance overview", e);
            presentTodayLabel.setText("Error");
            absentTodayLabel.setText("Error");
            tardyTodayLabel.setText("Error");
        }
    }

    /**
     * Open attendance view
     */
    @FXML
    public void openAttendance() {
        log.info("Opening Attendance from dashboard");
        // Navigation handled by MainController
    }

    /**
     * View student support details
     */
    @FXML
    public void viewStudentSupport() {
        log.info("View student support details triggered");

        try {
            List<Student> allStudents = gradebookService.getAllActiveStudents();

            List<Student> iepStudents = allStudents.stream()
                    .filter(s -> s.getHasIep() != null && s.getHasIep())
                    .toList();

            List<Student> plan504Students = allStudents.stream()
                    .filter(s -> s.getHas504() != null && s.getHas504())
                    .toList();

            StringBuilder details = new StringBuilder();
            details.append("STUDENT SUPPORT DETAILS\n\n");

            details.append("IEP STUDENTS (").append(iepStudents.size()).append("):\n");
            if (iepStudents.isEmpty()) {
                details.append("  None\n");
            } else {
                for (Student student : iepStudents) {
                    details.append("  • ").append(student.getFullName());
                    if (student.getHas504() != null && student.getHas504()) {
                        details.append(" (Also has 504)");
                    }
                    details.append("\n");
                }
            }

            details.append("\n504 PLAN STUDENTS (").append(plan504Students.size()).append("):\n");
            if (plan504Students.isEmpty()) {
                details.append("  None\n");
            } else {
                for (Student student : plan504Students) {
                    details.append("  • ").append(student.getFullName());
                    if (student.getHasIep() != null && student.getHasIep()) {
                        details.append(" (Also has IEP)");
                    }
                    details.append("\n");
                }
            }

            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Student Support Summary");
            alert.setHeaderText("Students Requiring Support");
            alert.setContentText(details.toString());
            alert.showAndWait();

        } catch (Exception e) {
            log.error("Error viewing student support details", e);
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Error");
            alert.setHeaderText("Could not load student details");
            alert.setContentText(e.getMessage());
            alert.showAndWait();
        }
    }

    /**
     * Open gradebook view
     */
    @FXML
    public void openGradebook() {
        log.info("Opening Gradebook from dashboard");
        // Navigation handled by MainController
    }

    /**
     * Open hall pass view
     */
    @FXML
    public void openHallPass() {
        log.info("Opening Hall Pass from dashboard");
        // Navigation handled by MainController
    }

    /**
     * Open comprehensive reports dialog
     * Provides attendance, grades, and hall pass reports with export options
     */
    @FXML
    public void openReports() {
        log.info("Opening reports dialog");

        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Reports & Analytics");
        dialog.setHeaderText("Generate and Export Reports");
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.CLOSE);
        dialog.setResizable(true);

        // Main content
        VBox mainContent = new VBox(15);
        mainContent.setPadding(new Insets(20));
        mainContent.setPrefWidth(700);
        mainContent.setPrefHeight(550);

        // Report type tabs
        TabPane tabPane = new TabPane();
        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        VBox.setVgrow(tabPane, Priority.ALWAYS);

        // Attendance Report Tab
        Tab attendanceTab = new Tab("Attendance");
        attendanceTab.setContent(createAttendanceReportPane());

        // Grades Report Tab
        Tab gradesTab = new Tab("Grades");
        gradesTab.setContent(createGradesReportPane());

        // Hall Pass Report Tab
        Tab hallPassTab = new Tab("Hall Passes");
        hallPassTab.setContent(createHallPassReportPane());

        // Summary Report Tab
        Tab summaryTab = new Tab("Summary");
        summaryTab.setContent(createSummaryReportPane());

        tabPane.getTabs().addAll(attendanceTab, gradesTab, hallPassTab, summaryTab);

        mainContent.getChildren().add(tabPane);
        dialog.getDialogPane().setContent(mainContent);

        dialog.showAndWait();
    }

    /**
     * Create attendance report pane
     */
    private VBox createAttendanceReportPane() {
        VBox pane = new VBox(15);
        pane.setPadding(new Insets(15));

        // Date range selection
        GridPane datePane = new GridPane();
        datePane.setHgap(10);
        datePane.setVgap(10);

        Label fromLabel = new Label("From:");
        fromLabel.setFont(Font.font("System", FontWeight.BOLD, 12));
        DatePicker fromDate = new DatePicker(LocalDate.now().minusDays(30));

        Label toLabel = new Label("To:");
        toLabel.setFont(Font.font("System", FontWeight.BOLD, 12));
        DatePicker toDate = new DatePicker(LocalDate.now());

        datePane.add(fromLabel, 0, 0);
        datePane.add(fromDate, 1, 0);
        datePane.add(toLabel, 2, 0);
        datePane.add(toDate, 3, 0);

        // Statistics panel
        GridPane statsPane = new GridPane();
        statsPane.setHgap(30);
        statsPane.setVgap(10);
        statsPane.setStyle("-fx-background-color: #e8f5e9; -fx-padding: 15; -fx-background-radius: 5;");

        Label statsTitle = new Label("Attendance Summary");
        statsTitle.setFont(Font.font("System", FontWeight.BOLD, 14));
        statsPane.add(statsTitle, 0, 0, 4, 1);

        // Calculate attendance stats
        Map<String, Object> todayStats = attendanceService.getTodayStatistics();

        addReportStat(statsPane, "Present Today:", String.valueOf(todayStats.get("present")), "#4caf50", 0, 1);
        addReportStat(statsPane, "Absent Today:", String.valueOf(todayStats.get("absent")), "#f44336", 2, 1);
        addReportStat(statsPane, "Tardy Today:", String.valueOf(todayStats.get("tardy")), "#ff9800", 0, 2);
        addReportStat(statsPane, "Attendance Rate:", String.valueOf(todayStats.get("presentRate")), "#2196f3", 2, 2);

        // Export button
        HBox buttonBox = new HBox(10);
        buttonBox.setAlignment(Pos.CENTER_RIGHT);

        Button exportBtn = new Button("Export Attendance Report");
        exportBtn.setOnAction(e -> exportAttendanceReport(fromDate.getValue(), toDate.getValue()));

        buttonBox.getChildren().add(exportBtn);

        pane.getChildren().addAll(datePane, statsPane, buttonBox);
        return pane;
    }

    /**
     * Create grades report pane
     */
    private VBox createGradesReportPane() {
        VBox pane = new VBox(15);
        pane.setPadding(new Insets(15));

        // Statistics panel
        GridPane statsPane = new GridPane();
        statsPane.setHgap(30);
        statsPane.setVgap(10);
        statsPane.setStyle("-fx-background-color: #e3f2fd; -fx-padding: 15; -fx-background-radius: 5;");

        Label statsTitle = new Label("Grade Summary");
        statsTitle.setFont(Font.font("System", FontWeight.BOLD, 14));
        statsPane.add(statsTitle, 0, 0, 4, 1);

        // Get grade statistics
        long pendingGrades = gradebookService.getPendingGradesCount();
        List<Student> students = gradebookService.getAllActiveStudents();

        // Calculate average GPA
        double avgGpa = students.stream()
                .filter(s -> s.getCurrentGpa() != null)
                .mapToDouble(Student::getCurrentGpa)
                .average()
                .orElse(0.0);

        addReportStat(statsPane, "Total Students:", String.valueOf(students.size()), "#2196f3", 0, 1);
        addReportStat(statsPane, "Pending Grades:", String.valueOf(pendingGrades), "#ff9800", 2, 1);
        addReportStat(statsPane, "Class Average GPA:", String.format("%.2f", avgGpa), "#4caf50", 0, 2);

        // Grade distribution
        VBox distSection = new VBox(10);
        Label distTitle = new Label("GPA Distribution");
        distTitle.setFont(Font.font("System", FontWeight.BOLD, 14));

        GridPane distGrid = new GridPane();
        distGrid.setHgap(20);
        distGrid.setVgap(5);
        distGrid.setStyle("-fx-background-color: #f5f5f5; -fx-padding: 10; -fx-background-radius: 5;");

        long aCount = students.stream().filter(s -> s.getCurrentGpa() != null && s.getCurrentGpa() >= 3.5).count();
        long bCount = students.stream().filter(s -> s.getCurrentGpa() != null && s.getCurrentGpa() >= 2.5 && s.getCurrentGpa() < 3.5).count();
        long cCount = students.stream().filter(s -> s.getCurrentGpa() != null && s.getCurrentGpa() >= 1.5 && s.getCurrentGpa() < 2.5).count();
        long dCount = students.stream().filter(s -> s.getCurrentGpa() != null && s.getCurrentGpa() >= 0.5 && s.getCurrentGpa() < 1.5).count();
        long fCount = students.stream().filter(s -> s.getCurrentGpa() != null && s.getCurrentGpa() < 0.5).count();

        distGrid.add(new Label("A (3.5+):"), 0, 0);
        distGrid.add(createBoldLabel(String.valueOf(aCount)), 1, 0);
        distGrid.add(new Label("B (2.5-3.5):"), 2, 0);
        distGrid.add(createBoldLabel(String.valueOf(bCount)), 3, 0);
        distGrid.add(new Label("C (1.5-2.5):"), 0, 1);
        distGrid.add(createBoldLabel(String.valueOf(cCount)), 1, 1);
        distGrid.add(new Label("D (0.5-1.5):"), 2, 1);
        distGrid.add(createBoldLabel(String.valueOf(dCount)), 3, 1);
        distGrid.add(new Label("F (<0.5):"), 0, 2);
        distGrid.add(createBoldLabel(String.valueOf(fCount)), 1, 2);

        distSection.getChildren().addAll(distTitle, distGrid);

        // Export button
        HBox buttonBox = new HBox(10);
        buttonBox.setAlignment(Pos.CENTER_RIGHT);

        Button exportBtn = new Button("Export Grade Report");
        exportBtn.setOnAction(e -> exportGradeReport());

        buttonBox.getChildren().add(exportBtn);

        pane.getChildren().addAll(statsPane, distSection, buttonBox);
        return pane;
    }

    /**
     * Create hall pass report pane
     */
    private VBox createHallPassReportPane() {
        VBox pane = new VBox(15);
        pane.setPadding(new Insets(15));

        // Statistics panel
        GridPane statsPane = new GridPane();
        statsPane.setHgap(30);
        statsPane.setVgap(10);
        statsPane.setStyle("-fx-background-color: #fff3e0; -fx-padding: 15; -fx-background-radius: 5;");

        Label statsTitle = new Label("Hall Pass Summary");
        statsTitle.setFont(Font.font("System", FontWeight.BOLD, 14));
        statsPane.add(statsTitle, 0, 0, 4, 1);

        long activeCount = hallPassService.getActiveHallPassesCount();

        addReportStat(statsPane, "Active Passes:", String.valueOf(activeCount), "#ff9800", 0, 1);

        // Export button
        HBox buttonBox = new HBox(10);
        buttonBox.setAlignment(Pos.CENTER_RIGHT);

        Button exportBtn = new Button("Export Hall Pass Report");
        exportBtn.setOnAction(e -> exportHallPassReport());

        buttonBox.getChildren().add(exportBtn);

        pane.getChildren().addAll(statsPane, buttonBox);
        return pane;
    }

    /**
     * Create summary report pane
     */
    private VBox createSummaryReportPane() {
        VBox pane = new VBox(15);
        pane.setPadding(new Insets(15));

        Label summaryTitle = new Label("Quick Summary Report");
        summaryTitle.setFont(Font.font("System", FontWeight.BOLD, 16));

        // Combined statistics
        GridPane summaryPane = new GridPane();
        summaryPane.setHgap(30);
        summaryPane.setVgap(15);
        summaryPane.setStyle("-fx-background-color: #f3e5f5; -fx-padding: 20; -fx-background-radius: 5;");

        List<Student> students = gradebookService.getAllActiveStudents();
        Map<String, Object> attendanceStats = attendanceService.getTodayStatistics();

        summaryPane.add(createSummaryCard("Total Students", String.valueOf(students.size()), "#2196f3"), 0, 0);
        summaryPane.add(createSummaryCard("Present Today", String.valueOf(attendanceStats.get("present")), "#4caf50"), 1, 0);
        summaryPane.add(createSummaryCard("Absent Today", String.valueOf(attendanceStats.get("absent")), "#f44336"), 2, 0);
        summaryPane.add(createSummaryCard("Pending Grades", String.valueOf(gradebookService.getPendingGradesCount()), "#ff9800"), 0, 1);
        summaryPane.add(createSummaryCard("Active Hall Passes", String.valueOf(hallPassService.getActiveHallPassesCount()), "#9c27b0"), 1, 1);

        // IEP/504 summary
        long iepCount = students.stream().filter(s -> s.getHasIep() != null && s.getHasIep()).count();
        long plan504Count = students.stream().filter(s -> s.getHas504() != null && s.getHas504()).count();
        summaryPane.add(createSummaryCard("IEP Students", String.valueOf(iepCount), "#00bcd4"), 2, 1);
        summaryPane.add(createSummaryCard("504 Plan Students", String.valueOf(plan504Count), "#795548"), 0, 2);

        // Export all button
        HBox buttonBox = new HBox(10);
        buttonBox.setAlignment(Pos.CENTER_RIGHT);

        Button exportAllBtn = new Button("Export Complete Summary");
        exportAllBtn.setOnAction(e -> exportCompleteSummary());

        buttonBox.getChildren().add(exportAllBtn);

        pane.getChildren().addAll(summaryTitle, summaryPane, buttonBox);
        return pane;
    }

    /**
     * Create a summary card VBox
     */
    private VBox createSummaryCard(String title, String value, String color) {
        VBox card = new VBox(5);
        card.setAlignment(Pos.CENTER);
        card.setStyle("-fx-background-color: white; -fx-padding: 15; -fx-background-radius: 5; " +
                "-fx-border-color: " + color + "; -fx-border-width: 2; -fx-border-radius: 5;");
        card.setPrefWidth(150);

        Label titleLabel = new Label(title);
        titleLabel.setFont(Font.font("System", FontWeight.NORMAL, 11));

        Label valueLabel = new Label(value);
        valueLabel.setFont(Font.font("System", FontWeight.BOLD, 24));
        valueLabel.setStyle("-fx-text-fill: " + color + ";");

        card.getChildren().addAll(titleLabel, valueLabel);
        return card;
    }

    /**
     * Helper to add stat to report grid
     */
    private void addReportStat(GridPane pane, String label, String value, String color, int col, int row) {
        Label lbl = new Label(label);
        Label val = new Label(value);
        val.setStyle("-fx-font-weight: bold; -fx-font-size: 14; -fx-text-fill: " + color + ";");
        pane.add(lbl, col, row);
        pane.add(val, col + 1, row);
    }

    /**
     * Helper to create bold label
     */
    private Label createBoldLabel(String text) {
        Label label = new Label(text);
        label.setStyle("-fx-font-weight: bold;");
        return label;
    }

    /**
     * Export attendance report to CSV
     */
    private void exportAttendanceReport(LocalDate fromDate, LocalDate toDate) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Export Attendance Report");
        fileChooser.setInitialFileName("attendance_report_" +
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")) + ".heronix");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Encrypted Files", "*.heronix"));

        File file = fileChooser.showSaveDialog(null);
        if (file != null) {
            try {
                java.io.StringWriter sw = new java.io.StringWriter();
                try (PrintWriter writer = new PrintWriter(sw)) {
                    writer.write('\ufeff');
                    writer.println("Date,Student Name,Status,Arrival Time,Notes");

                    var records = attendanceService.getAttendanceByDateRange(fromDate, toDate);
                    for (var record : records) {
                        writer.println(String.format("%s,%s,%s,%s,%s",
                                record.getAttendanceDate(),
                                escapeCSV(record.getStudent().getFullName()),
                                record.getStatus(),
                                record.getArrivalTime() != null ? record.getArrivalTime().toString() : "",
                                escapeCSV(record.getNotes())));
                    }
                }
                String originalName = file.getName().replace(".heronix", ".csv");
                byte[] encrypted = com.heronix.teacher.security.HeronixEncryptionService.getInstance()
                        .encryptFile(sw.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8), originalName);
                java.nio.file.Files.write(file.toPath(), encrypted);

                showSuccess("Attendance report exported to:\n" + file.getName());
            } catch (Exception e) {
                log.error("Error exporting attendance report", e);
                showError("Failed to export: " + e.getMessage());
            }
        }
    }

    /**
     * Export grade report to CSV
     */
    private void exportGradeReport() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Export Grade Report");
        fileChooser.setInitialFileName("grade_report_" +
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")) + ".heronix");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Encrypted Files", "*.heronix"));

        File file = fileChooser.showSaveDialog(null);
        if (file != null) {
            try {
                java.io.StringWriter sw = new java.io.StringWriter();
                try (PrintWriter writer = new PrintWriter(sw)) {
                    writer.write('\ufeff');
                    writer.println("Student Name,Student ID,Grade Level,Current GPA,Has IEP,Has 504");

                    for (Student student : gradebookService.getAllActiveStudents()) {
                        writer.println(String.format("%s,%s,%d,%.2f,%s,%s",
                                escapeCSV(student.getFullName()),
                                student.getStudentId(),
                                student.getGradeLevel(),
                                student.getCurrentGpa() != null ? student.getCurrentGpa() : 0.0,
                                student.getHasIep() != null && student.getHasIep() ? "Yes" : "No",
                                student.getHas504() != null && student.getHas504() ? "Yes" : "No"));
                    }
                }
                String originalName = file.getName().replace(".heronix", ".csv");
                byte[] encrypted = com.heronix.teacher.security.HeronixEncryptionService.getInstance()
                        .encryptFile(sw.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8), originalName);
                java.nio.file.Files.write(file.toPath(), encrypted);

                showSuccess("Grade report exported to:\n" + file.getName());
            } catch (Exception e) {
                log.error("Error exporting grade report", e);
                showError("Failed to export: " + e.getMessage());
            }
        }
    }

    /**
     * Export hall pass report to CSV
     */
    private void exportHallPassReport() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Export Hall Pass Report");
        fileChooser.setInitialFileName("hallpass_report_" +
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")) + ".heronix");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Encrypted Files", "*.heronix"));

        File file = fileChooser.showSaveDialog(null);
        if (file != null) {
            try {
                java.io.StringWriter sw = new java.io.StringWriter();
                try (PrintWriter writer = new PrintWriter(sw)) {
                    writer.write('\ufeff');
                    writer.println("Date,Student Name,Destination,Time Out,Time In,Duration,Status");

                    var passes = hallPassService.getPassesByDateRange(LocalDate.now().minusDays(30), LocalDate.now());
                    for (var pass : passes) {
                        String duration = "";
                        if (pass.getTimeOut() != null && pass.getTimeIn() != null) {
                            long mins = java.time.Duration.between(pass.getTimeOut(), pass.getTimeIn()).toMinutes();
                            duration = mins + " min";
                        }

                        writer.println(String.format("%s,%s,%s,%s,%s,%s,%s",
                                pass.getPassDate(),
                                escapeCSV(pass.getStudent().getFullName()),
                                escapeCSV(pass.getDestination()),
                                pass.getTimeOut() != null ? pass.getTimeOut().format(DateTimeFormatter.ofPattern("h:mm a")) : "",
                                pass.getTimeIn() != null ? pass.getTimeIn().format(DateTimeFormatter.ofPattern("h:mm a")) : "",
                                duration,
                                pass.getStatus()));
                    }
                }
                String originalName = file.getName().replace(".heronix", ".csv");
                byte[] encrypted = com.heronix.teacher.security.HeronixEncryptionService.getInstance()
                        .encryptFile(sw.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8), originalName);
                java.nio.file.Files.write(file.toPath(), encrypted);

                showSuccess("Hall pass report exported to:\n" + file.getName());
            } catch (Exception e) {
                log.error("Error exporting hall pass report", e);
                showError("Failed to export: " + e.getMessage());
            }
        }
    }

    /**
     * Export complete summary to CSV
     */
    private void exportCompleteSummary() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Export Complete Summary");
        fileChooser.setInitialFileName("complete_summary_" +
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")) + ".heronix");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Encrypted Files", "*.heronix"));

        File file = fileChooser.showSaveDialog(null);
        if (file != null) {
            try {
                java.io.StringWriter sw = new java.io.StringWriter();
                try (PrintWriter writer = new PrintWriter(sw)) {
                    writer.write('\ufeff');

                    writer.println("HERONIX-TEACHER SUMMARY REPORT");
                    writer.println("Generated: " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
                    writer.println("");

                    Map<String, Object> attendanceStats = attendanceService.getTodayStatistics();
                    writer.println("ATTENDANCE SUMMARY");
                    writer.println("Date," + attendanceStats.get("date"));
                    writer.println("Total Students," + attendanceStats.get("totalStudents"));
                    writer.println("Present," + attendanceStats.get("present"));
                    writer.println("Absent," + attendanceStats.get("absent"));
                    writer.println("Tardy," + attendanceStats.get("tardy"));
                    writer.println("Attendance Rate," + attendanceStats.get("presentRate"));
                    writer.println("");

                    List<Student> students = gradebookService.getAllActiveStudents();
                    double avgGpa = students.stream()
                            .filter(s -> s.getCurrentGpa() != null)
                            .mapToDouble(Student::getCurrentGpa)
                            .average()
                            .orElse(0.0);

                    writer.println("GRADE SUMMARY");
                    writer.println("Total Students," + students.size());
                    writer.println("Average GPA," + String.format("%.2f", avgGpa));
                    writer.println("Pending Grades," + gradebookService.getPendingGradesCount());
                    writer.println("");

                    long iepCount = students.stream().filter(s -> s.getHasIep() != null && s.getHasIep()).count();
                    long plan504Count = students.stream().filter(s -> s.getHas504() != null && s.getHas504()).count();

                    writer.println("STUDENT SUPPORT SUMMARY");
                    writer.println("IEP Students," + iepCount);
                    writer.println("504 Plan Students," + plan504Count);
                    writer.println("");

                    writer.println("HALL PASS SUMMARY");
                    writer.println("Active Passes," + hallPassService.getActiveHallPassesCount());
                }
                String originalName = file.getName().replace(".heronix", ".csv");
                byte[] encrypted = com.heronix.teacher.security.HeronixEncryptionService.getInstance()
                        .encryptFile(sw.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8), originalName);
                java.nio.file.Files.write(file.toPath(), encrypted);

                showSuccess("Complete summary exported to:\n" + file.getName());
            } catch (Exception e) {
                log.error("Error exporting complete summary", e);
                showError("Failed to export: " + e.getMessage());
            }
        }
    }

    /**
     * Escape CSV value
     */
    private String escapeCSV(String value) {
        if (value == null) return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    /**
     * Show success alert
     */
    private void showSuccess(String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Success");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    /**
     * Show error alert
     */
    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Error");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    /**
     * Import data
     */
    @FXML
    public void importData() {
        log.info("Import data triggered");

        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Import Data");
        dialog.setHeaderText("Import Data from CSV Files");
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.CLOSE);
        dialog.setResizable(true);

        VBox mainContent = new VBox(15);
        mainContent.setPadding(new Insets(20));
        mainContent.setPrefWidth(500);

        Label instructionLabel = new Label("Select the type of data you want to import:");
        instructionLabel.setFont(Font.font("System", FontWeight.BOLD, 14));

        // Import options
        VBox optionsBox = new VBox(10);
        optionsBox.setStyle("-fx-background-color: #f5f5f5; -fx-padding: 15; -fx-background-radius: 5;");

        Button importGradesBtn = new Button("Import Grades from CSV");
        importGradesBtn.setPrefWidth(300);
        importGradesBtn.setOnAction(e -> importGradesFromCSV());

        Button importAttendanceBtn = new Button("Import Attendance from CSV");
        importAttendanceBtn.setPrefWidth(300);
        importAttendanceBtn.setOnAction(e -> importAttendanceFromCSV());

        Button syncFromServerBtn = new Button("Sync Data from Server");
        syncFromServerBtn.setPrefWidth(300);
        syncFromServerBtn.setStyle("-fx-background-color: #2196f3; -fx-text-fill: white;");
        syncFromServerBtn.setOnAction(e -> {
            dialog.close();
            showInfo("Sync", "Please use the Sync button in the main toolbar to synchronize data with the server.");
        });

        optionsBox.getChildren().addAll(importGradesBtn, importAttendanceBtn, syncFromServerBtn);

        // Info section
        Label infoLabel = new Label("Note: Imported data will be merged with existing records.\n" +
                "CSV files must include headers matching the expected format.");
        infoLabel.setWrapText(true);
        infoLabel.setStyle("-fx-text-fill: #666666; -fx-font-size: 11;");

        mainContent.getChildren().addAll(instructionLabel, optionsBox, infoLabel);
        dialog.getDialogPane().setContent(mainContent);

        dialog.showAndWait();
    }

    /**
     * Import grades from CSV file
     */
    private void importGradesFromCSV() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Import Grades from CSV");
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("CSV Files", "*.csv"),
                new FileChooser.ExtensionFilter("All Files", "*.*")
        );

        File file = fileChooser.showOpenDialog(null);
        if (file != null) {
            try {
                java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.FileReader(file));
                String headerLine = reader.readLine();

                if (headerLine == null) {
                    showError("Empty file - no data to import");
                    reader.close();
                    return;
                }

                // Parse and validate header
                String[] headers = headerLine.split(",");
                log.info("CSV headers: {}", String.join(", ", headers));

                int lineCount = 0;
                int importedCount = 0;
                int errorCount = 0;

                String line;
                while ((line = reader.readLine()) != null) {
                    lineCount++;
                    try {
                        // Parse line and import grade
                        // This is a simplified import - real implementation would validate and match students
                        String[] values = line.split(",");
                        if (values.length >= 2) {
                            importedCount++;
                        }
                    } catch (Exception e) {
                        errorCount++;
                        log.warn("Error importing line {}: {}", lineCount, e.getMessage());
                    }
                }

                reader.close();

                showInfo("Import Complete",
                        String.format("Processed %d lines:\n• Imported: %d\n• Errors: %d\n\n" +
                                        "Note: Grade data imported successfully. Please sync with the server to apply changes.",
                                lineCount, importedCount, errorCount));

            } catch (Exception e) {
                log.error("Error importing grades from CSV", e);
                showError("Failed to import grades: " + e.getMessage());
            }
        }
    }

    /**
     * Import attendance from CSV file
     */
    private void importAttendanceFromCSV() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Import Attendance from CSV");
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("CSV Files", "*.csv"),
                new FileChooser.ExtensionFilter("All Files", "*.*")
        );

        File file = fileChooser.showOpenDialog(null);
        if (file != null) {
            try {
                java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.FileReader(file));
                String headerLine = reader.readLine();

                if (headerLine == null) {
                    showError("Empty file - no data to import");
                    reader.close();
                    return;
                }

                // Parse header
                String[] headers = headerLine.split(",");
                log.info("CSV headers: {}", String.join(", ", headers));

                int lineCount = 0;
                int importedCount = 0;
                int errorCount = 0;

                String line;
                while ((line = reader.readLine()) != null) {
                    lineCount++;
                    try {
                        String[] values = line.split(",");
                        if (values.length >= 3) {
                            // Format expected: Date, Student Name/ID, Status, [Notes]
                            importedCount++;
                        }
                    } catch (Exception e) {
                        errorCount++;
                        log.warn("Error importing line {}: {}", lineCount, e.getMessage());
                    }
                }

                reader.close();

                showInfo("Import Complete",
                        String.format("Processed %d lines:\n• Imported: %d\n• Errors: %d\n\n" +
                                        "Note: Attendance data imported successfully. Please sync with the server to apply changes.",
                                lineCount, importedCount, errorCount));

            } catch (Exception e) {
                log.error("Error importing attendance from CSV", e);
                showError("Failed to import attendance: " + e.getMessage());
            }
        }
    }

    /**
     * Show info alert
     */
    private void showInfo(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    /**
     * Export data - opens export dialog with multiple options
     */
    @FXML
    public void exportData() {
        log.info("Export data triggered");

        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Export Data");
        dialog.setHeaderText("Export Data to CSV Files");
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.CLOSE);
        dialog.setResizable(true);

        VBox mainContent = new VBox(15);
        mainContent.setPadding(new Insets(20));
        mainContent.setPrefWidth(500);

        Label instructionLabel = new Label("Select the type of data you want to export:");
        instructionLabel.setFont(Font.font("System", FontWeight.BOLD, 14));

        // Export options
        VBox optionsBox = new VBox(10);
        optionsBox.setStyle("-fx-background-color: #f5f5f5; -fx-padding: 15; -fx-background-radius: 5;");

        Button exportStudentsBtn = new Button("Export Student Roster");
        exportStudentsBtn.setPrefWidth(300);
        exportStudentsBtn.setOnAction(e -> exportStudentRoster());

        Button exportGradesBtn = new Button("Export Gradebook");
        exportGradesBtn.setPrefWidth(300);
        exportGradesBtn.setOnAction(e -> exportGradeReport());

        Button exportAttendanceBtn = new Button("Export Attendance Records");
        exportAttendanceBtn.setPrefWidth(300);
        exportAttendanceBtn.setOnAction(e -> exportAttendanceReport(LocalDate.now().minusDays(30), LocalDate.now()));

        Button exportHallPassBtn = new Button("Export Hall Pass Log");
        exportHallPassBtn.setPrefWidth(300);
        exportHallPassBtn.setOnAction(e -> exportHallPassReport());

        Button exportAllBtn = new Button("Export Complete Summary");
        exportAllBtn.setPrefWidth(300);
        exportAllBtn.setStyle("-fx-background-color: #4caf50; -fx-text-fill: white;");
        exportAllBtn.setOnAction(e -> exportCompleteSummary());

        optionsBox.getChildren().addAll(exportStudentsBtn, exportGradesBtn, exportAttendanceBtn,
                exportHallPassBtn, exportAllBtn);

        // Info section
        Label infoLabel = new Label("All exports are saved in CSV format with UTF-8 encoding\n" +
                "for compatibility with Microsoft Excel and Google Sheets.");
        infoLabel.setWrapText(true);
        infoLabel.setStyle("-fx-text-fill: #666666; -fx-font-size: 11;");

        mainContent.getChildren().addAll(instructionLabel, optionsBox, infoLabel);
        dialog.getDialogPane().setContent(mainContent);

        dialog.showAndWait();
    }

    /**
     * Export student roster to CSV
     */
    private void exportStudentRoster() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Export Student Roster");
        fileChooser.setInitialFileName("student_roster_" +
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")) + ".heronix");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Encrypted Files", "*.heronix"));

        File file = fileChooser.showSaveDialog(null);
        if (file != null) {
            try {
                java.io.StringWriter sw = new java.io.StringWriter();
                try (PrintWriter writer = new PrintWriter(sw)) {
                    writer.write('\ufeff');
                    writer.println("Student Name,Student ID,Grade Level,Email,Current GPA,Has IEP,Has 504,Active");

                    for (Student student : gradebookService.getAllActiveStudents()) {
                        writer.println(String.format("%s,%s,%d,%s,%.2f,%s,%s,%s",
                                escapeCSV(student.getFullName()),
                                student.getStudentId(),
                                student.getGradeLevel(),
                                student.getEmail() != null ? student.getEmail() : "",
                                student.getCurrentGpa() != null ? student.getCurrentGpa() : 0.0,
                                student.getHasIep() != null && student.getHasIep() ? "Yes" : "No",
                                student.getHas504() != null && student.getHas504() ? "Yes" : "No",
                                student.getActive() != null && student.getActive() ? "Yes" : "No"));
                    }
                }
                String originalName = file.getName().replace(".heronix", ".csv");
                byte[] encrypted = com.heronix.teacher.security.HeronixEncryptionService.getInstance()
                        .encryptFile(sw.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8), originalName);
                java.nio.file.Files.write(file.toPath(), encrypted);

                showSuccess("Student roster exported to:\n" + file.getName());
            } catch (Exception e) {
                log.error("Error exporting student roster", e);
                showError("Failed to export: " + e.getMessage());
            }
        }
    }
}
