package com.heronix.teacher.ui.controller;

import com.heronix.teacher.model.domain.Assignment;
import com.heronix.teacher.model.domain.Grade;
import com.heronix.teacher.model.domain.Student;
import com.heronix.teacher.service.AdminApiClient;
import com.heronix.teacher.service.GradebookService;
import com.heronix.teacher.service.StudentEnrollmentCache;
import com.heronix.teacher.ui.dialog.StudentCardDialog;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.layout.HBox;
import javafx.util.converter.DoubleStringConverter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import javafx.stage.FileChooser;

/**
 * Gradebook controller for spreadsheet-style grade entry
 *
 * Features:
 * - Student roster view
 * - Assignment columns
 * - Inline grade editing
 * - Color-coded grades
 * - Statistics
 *
 * @author EduScheduler Team
 * @version 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GradebookController {

    private final GradebookService gradebookService;
    private final StudentEnrollmentCache studentEnrollmentCache;
    private final AdminApiClient adminApiClient;

    // Filter controls
    @FXML private TextField searchField;
    @FXML private ComboBox<String> courseFilterCombo;

    // Statistics labels
    @FXML private Label totalStudentsLabel;
    @FXML private Label totalAssignmentsLabel;
    @FXML private Label averageGpaLabel;
    @FXML private Label pendingGradesLabel;

    // Gradebook table
    @FXML private TableView<Student> gradebookTable;
    @FXML private TableColumn<Student, String> studentNameColumn;
    @FXML private TableColumn<Student, String> studentIdColumn;
    @FXML private TableColumn<Student, Integer> gradeLevelColumn;
    @FXML private TableColumn<Student, Double> currentGpaColumn;

    // Assignment management
    @FXML private Button addAssignmentBtn;
    @FXML private Button editAssignmentBtn;
    @FXML private Button deleteAssignmentBtn;

    private ObservableList<Student> students = FXCollections.observableArrayList();
    private List<Assignment> assignments = new ArrayList<>();
    private List<TableColumn<Student, ?>> assignmentColumns = new ArrayList<>();

    /**
     * Initialize controller
     */
    @FXML
    public void initialize() {
        log.info("Initializing Gradebook Controller");

        setupStudentColumns();
        setupFilters();
        loadData();
        updateStatistics();

        log.info("Gradebook Controller initialized successfully");
    }

    /**
     * Setup static student columns
     */
    private void setupStudentColumns() {
        // Student name column
        studentNameColumn.setCellValueFactory(cellData ->
            new SimpleStringProperty(cellData.getValue().getFullName()));

        // Student ID column
        studentIdColumn.setCellValueFactory(cellData ->
            new SimpleStringProperty(cellData.getValue().getStudentId()));

        // Grade level column
        gradeLevelColumn.setCellValueFactory(cellData ->
            new SimpleObjectProperty<>(cellData.getValue().getGradeLevel()));

        // Current GPA column with color coding
        currentGpaColumn.setCellValueFactory(cellData ->
            new SimpleObjectProperty<>(cellData.getValue().getCurrentGpa()));

        currentGpaColumn.setCellFactory(column -> new TableCell<Student, Double>() {
            @Override
            protected void updateItem(Double gpa, boolean empty) {
                super.updateItem(gpa, empty);

                if (empty || gpa == null) {
                    setText(null);
                    getStyleClass().removeAll("success-text", "warning-text", "danger-text");
                } else {
                    setText(String.format("%.2f", gpa));

                    getStyleClass().removeAll("success-text", "warning-text", "danger-text");
                    if (gpa >= 3.5) {
                        getStyleClass().add("success-text");
                    } else if (gpa >= 2.0) {
                        getStyleClass().add("warning-text");
                    } else {
                        getStyleClass().add("danger-text");
                    }
                }
            }
        });

        gradebookTable.setItems(students);

        // Double-click row → open Student Card dialog
        gradebookTable.setRowFactory(tv -> {
            TableRow<Student> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && !row.isEmpty()) {
                    Student student = row.getItem();
                    Long serverId = student.getServerId();
                    if (serverId != null) {
                        StudentCardDialog.show(adminApiClient, serverId,
                                student.getFullName(), gradebookTable.getScene().getWindow());
                    }
                }
            });
            return row;
        });
    }

    /**
     * Setup filter controls
     */
    private void setupFilters() {
        // Search field
        searchField.textProperty().addListener((obs, oldVal, newVal) -> {
            filterStudents(newVal);
        });

        // Course filter - load from teacher's actual courses
        try {
            courseFilterCombo.getItems().add("All Courses");

            // Get teacher's courses from gradebook service
            List<String> teacherCourses = gradebookService.getTeacherCourses();

            if (teacherCourses != null && !teacherCourses.isEmpty()) {
                courseFilterCombo.getItems().addAll(teacherCourses);
            } else {
                // Fallback to common subjects if no courses found
                courseFilterCombo.getItems().addAll("Math", "English", "Science", "History");
            }

            courseFilterCombo.setValue("All Courses");
            courseFilterCombo.setOnAction(e -> filterByCourse());

            log.debug("Course filter loaded with {} courses", courseFilterCombo.getItems().size() - 1);

        } catch (Exception e) {
            log.error("Error loading course filter", e);
            // Fallback to default courses
            courseFilterCombo.getItems().addAll("All Courses", "Math", "English", "Science", "History");
            courseFilterCombo.setValue("All Courses");
            courseFilterCombo.setOnAction(e2 -> filterByCourse());
        }
    }

    /**
     * Load students and assignments
     */
    private void loadData() {
        loadStudents();
        loadAssignments();
    }

    /**
     * Load students from database
     */
    private void loadStudents() {
        log.debug("Loading students");

        List<Student> studentList = gradebookService.getAllActiveStudents();
        students.clear();
        students.addAll(studentList);

        log.info("Loaded {} students", studentList.size());
    }

    /**
     * Load assignments and create columns
     */
    private void loadAssignments() {
        log.debug("Loading assignments");

        // Remove existing assignment columns
        gradebookTable.getColumns().removeAll(assignmentColumns);
        assignmentColumns.clear();

        // Load assignments
        assignments = gradebookService.getAllActiveAssignments();

        // Create column for each assignment
        for (Assignment assignment : assignments) {
            TableColumn<Student, String> column = createAssignmentColumn(assignment);
            assignmentColumns.add(column);
            gradebookTable.getColumns().add(column);
        }

        log.info("Loaded {} assignments", assignments.size());
    }

    /**
     * Create column for assignment
     */
    private TableColumn<Student, String> createAssignmentColumn(Assignment assignment) {
        TableColumn<Student, String> column = new TableColumn<>(assignment.getName());
        column.setMinWidth(100);

        // Cell value factory - get grade or show empty
        column.setCellValueFactory(cellData -> {
            Student student = cellData.getValue();
            Optional<Grade> gradeOpt = gradebookService.getGrade(student.getId(), assignment.getId());

            if (gradeOpt.isPresent()) {
                Grade grade = gradeOpt.get();
                if (grade.getMissing()) {
                    return new SimpleStringProperty("Missing");
                } else if (grade.getExcused()) {
                    return new SimpleStringProperty("Excused");
                } else if (grade.getScore() != null) {
                    return new SimpleStringProperty(String.format("%.1f", grade.getScore()));
                }
            }

            return new SimpleStringProperty("");
        });

        // Make editable
        column.setCellFactory(col -> new TableCell<Student, String>() {
            private final TextField textField = new TextField();

            {
                textField.setOnAction(e -> commitEdit(textField.getText()));
                textField.focusedProperty().addListener((obs, wasFocused, isNowFocused) -> {
                    if (!isNowFocused) {
                        commitEdit(textField.getText());
                    }
                });
            }

            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);

                if (empty) {
                    setText(null);
                    setGraphic(null);
                    getStyleClass().removeAll("success-text", "warning-text", "danger-text", "text-secondary");
                } else {
                    if (isEditing()) {
                        textField.setText(item);
                        setGraphic(textField);
                        setText(null);
                    } else {
                        setText(item);
                        setGraphic(null);

                        // Color code based on grade
                        getStyleClass().removeAll("success-text", "warning-text", "danger-text", "text-secondary");
                        if (item != null && !item.isEmpty()) {
                            if (item.equals("Missing")) {
                                getStyleClass().add("danger-text");
                            } else if (item.equals("Excused")) {
                                getStyleClass().add("text-secondary");
                            } else {
                                try {
                                    double score = Double.parseDouble(item);
                                    double percentage = (score / assignment.getMaxPoints()) * 100;

                                    if (percentage >= 80) {
                                        getStyleClass().add("success-text");
                                    } else if (percentage >= 60) {
                                        getStyleClass().add("warning-text");
                                    } else {
                                        getStyleClass().add("danger-text");
                                    }
                                } catch (NumberFormatException ignored) {
                                }
                            }
                        }
                    }
                }
            }

            @Override
            public void startEdit() {
                super.startEdit();
                textField.setText(getItem());
                setGraphic(textField);
                setText(null);
                textField.requestFocus();
                textField.selectAll();
            }

            @Override
            public void cancelEdit() {
                super.cancelEdit();
                setText(getItem());
                setGraphic(null);
            }

            @Override
            public void commitEdit(String newValue) {
                super.commitEdit(newValue);

                Student student = getTableView().getItems().get(getIndex());

                if (newValue == null || newValue.trim().isEmpty()) {
                    // Delete grade
                    Optional<Grade> gradeOpt = gradebookService.getGrade(student.getId(), assignment.getId());
                    gradeOpt.ifPresent(grade -> gradebookService.deleteGrade(grade.getId()));
                } else {
                    try {
                        double score = Double.parseDouble(newValue);
                        gradebookService.enterGrade(student.getId(), assignment.getId(), score, "");
                        log.info("Entered grade {} for student {} on assignment {}",
                                 score, student.getFullName(), assignment.getName());
                    } catch (NumberFormatException e) {
                        log.warn("Invalid grade value: {}", newValue);
                        showError("Invalid grade value. Please enter a number.");
                    }
                }

                updateStatistics();
                gradebookTable.refresh();
            }
        });

        // Enable editing
        column.setEditable(true);

        return column;
    }

    /**
     * Filter students by search term
     */
    private void filterStudents(String searchTerm) {
        if (searchTerm == null || searchTerm.trim().isEmpty()) {
            loadStudents();
        } else {
            List<Student> filtered = gradebookService.searchStudents(searchTerm);
            students.clear();
            students.addAll(filtered);
        }
    }

    /**
     * Filter by course (or period-course label)
     */
    private void filterByCourse() {
        String selectedCourse = courseFilterCombo.getValue();
        log.info("Course filter changed to: {}", selectedCourse);

        try {
            if (selectedCourse == null || selectedCourse.equals("All Courses")) {
                // Show all students and assignments
                loadStudents();
                loadAssignments();
            } else {
                // Check if this is a period label (e.g., "Period 1 - Algebra I (MATH101)")
                Integer period = StudentEnrollmentCache.parsePeriodFromLabel(selectedCourse);

                if (period != null) {
                    // Load students from enrollment cache for this period
                    List<Student> periodStudents = gradebookService.getStudentsForPeriod(period);
                    students.clear();
                    students.addAll(periodStudents);

                    // Load assignments for this period and rebuild columns
                    gradebookTable.getColumns().removeAll(assignmentColumns);
                    assignmentColumns.clear();
                    assignments = gradebookService.getAssignmentsForPeriod(period);
                    for (Assignment assignment : assignments) {
                        TableColumn<Student, String> column = createAssignmentColumn(assignment);
                        assignmentColumns.add(column);
                        gradebookTable.getColumns().add(column);
                    }

                    log.info("Period {} filter applied - showing {} students, {} assignments",
                            period, periodStudents.size(), assignments.size());
                } else {
                    // Legacy course name filter
                    List<Student> filteredStudents = gradebookService.getStudentsByCourse(selectedCourse);
                    students.clear();
                    students.addAll(filteredStudents);

                    log.info("Course filter '{}' applied - showing {} students",
                            selectedCourse, filteredStudents.size());
                }

                updateStatistics();
            }
        } catch (Exception e) {
            log.error("Error filtering by course", e);
            showError("Failed to filter by course: " + e.getMessage());
        }
    }

    /**
     * Update statistics
     */
    private void updateStatistics() {
        long totalStudents = gradebookService.countActiveStudents();
        long totalAssignments = assignments.size();
        long pendingCount = gradebookService.countItemsNeedingSync();

        totalStudentsLabel.setText(String.valueOf(totalStudents));
        totalAssignmentsLabel.setText(String.valueOf(totalAssignments));
        pendingGradesLabel.setText(String.valueOf(pendingCount));

        // Calculate average GPA
        double totalGpa = 0;
        int count = 0;
        for (Student student : students) {
            if (student.getCurrentGpa() != null) {
                totalGpa += student.getCurrentGpa();
                count++;
            }
        }
        double avgGpa = count > 0 ? totalGpa / count : 0.0;
        averageGpaLabel.setText(String.format("%.2f", avgGpa));
    }

    /**
     * Add new assignment
     */
    @FXML
    public void addAssignment() {
        log.info("Add assignment triggered");

        try {
            javafx.fxml.FXMLLoader loader = new javafx.fxml.FXMLLoader(
                getClass().getResource("/fxml/AssignmentDialog.fxml"));
            javafx.scene.Parent root = loader.load();

            AssignmentDialogController controller = loader.getController();
            controller.setGradebookService(gradebookService);
            controller.setCourseOptions(studentEnrollmentCache.getPeriodCourseLabels());

            javafx.stage.Stage dialog = new javafx.stage.Stage();
            dialog.setTitle("Create New Assignment");
            dialog.setScene(new javafx.scene.Scene(root));
            dialog.initModality(javafx.stage.Modality.APPLICATION_MODAL);
            dialog.showAndWait();

            if (controller.isSaved()) {
                log.info("Assignment created successfully");
                refresh();
            }

        } catch (Exception e) {
            log.error("Error opening assignment dialog", e);
            showError("Failed to open assignment dialog: " + e.getMessage());
        }
    }

    /**
     * Edit selected assignment
     */
    @FXML
    public void editAssignment() {
        log.info("Edit assignment triggered");

        if (assignments.isEmpty()) {
            showError("No assignments to edit. Please create an assignment first.");
            return;
        }

        // Show selection dialog
        javafx.scene.control.ChoiceDialog<Assignment> choiceDialog =
            new javafx.scene.control.ChoiceDialog<>(assignments.get(0), assignments);
        choiceDialog.setTitle("Edit Assignment");
        choiceDialog.setHeaderText("Select Assignment to Edit");
        choiceDialog.setContentText("Assignment:");

        choiceDialog.showAndWait().ifPresent(selectedAssignment -> {
            try {
                javafx.fxml.FXMLLoader loader = new javafx.fxml.FXMLLoader(
                    getClass().getResource("/fxml/AssignmentDialog.fxml"));
                javafx.scene.Parent root = loader.load();

                AssignmentDialogController controller = loader.getController();
                controller.setGradebookService(gradebookService);
                controller.setCourseOptions(studentEnrollmentCache.getPeriodCourseLabels());
                controller.setAssignment(selectedAssignment);

                javafx.stage.Stage dialog = new javafx.stage.Stage();
                dialog.setTitle("Edit Assignment");
                dialog.setScene(new javafx.scene.Scene(root));
                dialog.initModality(javafx.stage.Modality.APPLICATION_MODAL);
                dialog.showAndWait();

                if (controller.isSaved()) {
                    log.info("Assignment updated successfully");
                    refresh();
                }

            } catch (Exception e) {
                log.error("Error opening edit dialog", e);
                showError("Failed to open edit dialog: " + e.getMessage());
            }
        });
    }

    /**
     * Delete selected assignment
     */
    @FXML
    public void deleteAssignment() {
        log.info("Delete assignment triggered");

        if (assignments.isEmpty()) {
            showError("No assignments to delete.");
            return;
        }

        // Show selection dialog
        javafx.scene.control.ChoiceDialog<Assignment> choiceDialog =
            new javafx.scene.control.ChoiceDialog<>(assignments.get(0), assignments);
        choiceDialog.setTitle("Delete Assignment");
        choiceDialog.setHeaderText("Select Assignment to Delete");
        choiceDialog.setContentText("Assignment:");

        choiceDialog.showAndWait().ifPresent(selectedAssignment -> {
            // Confirm deletion
            Alert confirmAlert = new Alert(Alert.AlertType.CONFIRMATION);
            confirmAlert.setTitle("Confirm Deletion");
            confirmAlert.setHeaderText("Delete Assignment: " + selectedAssignment.getName());
            confirmAlert.setContentText("Are you sure you want to delete this assignment?\n\n" +
                                       "This will also delete all grades for this assignment.\n" +
                                       "This action cannot be undone.");

            confirmAlert.showAndWait().ifPresent(response -> {
                if (response == javafx.scene.control.ButtonType.OK) {
                    try {
                        gradebookService.deleteAssignment(selectedAssignment.getId());
                        log.info("Deleted assignment: {}", selectedAssignment.getName());
                        refresh();
                        showInfo("Success", "Assignment deleted successfully");
                    } catch (Exception e) {
                        log.error("Error deleting assignment", e);
                        showError("Failed to delete assignment: " + e.getMessage());
                    }
                }
            });
        });
    }

    /**
     * Refresh data
     */
    @FXML
    public void refresh() {
        log.info("Refreshing gradebook data");
        loadData();
        updateStatistics();
    }

    /**
     * Export grades to CSV file
     */
    @FXML
    public void exportGrades() {
        log.info("Export grades triggered");

        if (students.isEmpty()) {
            showError("No data to export. Please load students first.");
            return;
        }

        // Show file chooser
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Export Grades");
        fileChooser.setInitialFileName("gradebook_export_" +
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")) + ".csv");
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("CSV Files", "*.csv"),
                new FileChooser.ExtensionFilter("All Files", "*.*")
        );

        File file = fileChooser.showSaveDialog(gradebookTable.getScene().getWindow());

        if (file != null) {
            exportGradesToCsv(file);
        }
    }

    /**
     * Export grades data to CSV file
     */
    private void exportGradesToCsv(File file) {
        try (PrintWriter writer = new PrintWriter(new FileWriter(file))) {
            // Write BOM for Excel UTF-8 compatibility
            writer.write('\ufeff');

            // Build header row
            StringBuilder header = new StringBuilder();
            header.append("Student Name,Student ID,Grade Level,Current GPA");

            for (Assignment assignment : assignments) {
                // Escape assignment name if it contains commas
                String name = escapeCSV(assignment.getName());
                header.append(",").append(name).append(" (").append(assignment.getMaxPoints()).append(" pts)");
            }
            writer.println(header);

            // Write data rows
            for (Student student : students) {
                StringBuilder row = new StringBuilder();
                row.append(escapeCSV(student.getFullName())).append(",");
                row.append(escapeCSV(student.getStudentId())).append(",");
                row.append(student.getGradeLevel()).append(",");
                row.append(student.getCurrentGpa() != null ? String.format("%.2f", student.getCurrentGpa()) : "");

                // Add grade for each assignment
                for (Assignment assignment : assignments) {
                    row.append(",");
                    Optional<Grade> gradeOpt = gradebookService.getGrade(student.getId(), assignment.getId());

                    if (gradeOpt.isPresent()) {
                        Grade grade = gradeOpt.get();
                        if (grade.getMissing()) {
                            row.append("Missing");
                        } else if (grade.getExcused()) {
                            row.append("Excused");
                        } else if (grade.getScore() != null) {
                            row.append(String.format("%.1f", grade.getScore()));
                        }
                    }
                }
                writer.println(row);
            }

            log.info("Grades exported successfully to {}", file.getAbsolutePath());
            showInfo("Export Successful",
                    "Grades exported to:\n" + file.getName() + "\n\n" +
                    "Exported " + students.size() + " students and " + assignments.size() + " assignments.");

        } catch (Exception e) {
            log.error("Error exporting grades to CSV", e);
            showError("Failed to export grades: " + e.getMessage());
        }
    }

    /**
     * Escape value for CSV (handle commas, quotes, newlines)
     */
    private String escapeCSV(String value) {
        if (value == null) {
            return "";
        }
        // If value contains comma, quote, or newline, wrap in quotes and escape existing quotes
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    /**
     * Show info message
     */
    private void showInfo(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    /**
     * Show error message
     */
    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Error");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
