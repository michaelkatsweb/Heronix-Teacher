package com.heronix.teacher.ui.controller;

import com.heronix.teacher.model.domain.Student;
import com.heronix.teacher.service.AttendanceService;
import com.heronix.teacher.service.GradebookService;
import com.heronix.teacher.service.HallPassService;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

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
     * Load real schedule from EduScheduler Pro
     *
     * NOTE: This requires schedule sync service to be implemented
     */
    private void loadRealSchedule() {
        scheduleListView.getItems().add("📅 Today's Schedule");
        scheduleListView.getItems().add("");

        // TODO: Implement schedule sync service integration
        // Example implementation:
        // List<ScheduleSlot> schedule = scheduleService.getTodaySchedule(teacherId);
        // for (ScheduleSlot slot : schedule) {
        //     scheduleListView.getItems().add(formatScheduleSlot(slot));
        // }

        // For now, show sync instruction
        scheduleListView.getItems().add("Your schedule will appear here after syncing");
        scheduleListView.getItems().add("with the main EduScheduler server.");
        scheduleListView.getItems().add("");
        scheduleListView.getItems().add("Click the Sync button in the header to load");
        scheduleListView.getItems().add("your teaching schedule for today.");

        log.debug("Real schedule loading placeholder executed");
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
     * Open reports (Phase 2)
     */
    @FXML
    public void openReports() {
        log.info("Reports feature - Coming in Phase 2");
    }

    /**
     * Import data
     */
    @FXML
    public void importData() {
        log.info("Import data triggered");

        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Import Data");
        alert.setHeaderText("Import from CSV");
        alert.setContentText("Import options available:\n\n" +
                "• Student Grades (CSV)\n" +
                "• Attendance Records (CSV)\n" +
                "• Hall Pass History (CSV)\n\n" +
                "Import functionality will be available in the next release.\n\n" +
                "For now, use the main EduScheduler-Pro application to\n" +
                "import data, then sync to this portal.");
        alert.showAndWait();
    }

    /**
     * Export data
     */
    @FXML
    public void exportData() {
        log.info("Export data triggered");

        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Export Data");
        alert.setHeaderText("Export to CSV");
        alert.setContentText("Export options available:\n\n" +
                "• Current Gradebook (CSV)\n" +
                "• Attendance Records (CSV)\n" +
                "• Hall Pass Log (CSV)\n" +
                "• Student Support Summary (PDF)\n\n" +
                "Export functionality will be available in the next release.\n\n" +
                "For now, use the main EduScheduler-Pro application to\n" +
                "export data.");
        alert.showAndWait();
    }
}
