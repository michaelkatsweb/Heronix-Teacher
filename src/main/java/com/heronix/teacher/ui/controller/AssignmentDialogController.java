package com.heronix.teacher.ui.controller;

import com.heronix.teacher.model.domain.Assignment;
import com.heronix.teacher.model.domain.AssignmentCategory;
import com.heronix.teacher.model.enums.AssignmentType;
import com.heronix.teacher.model.enums.GradingStyle;
import com.heronix.teacher.service.GradebookService;
import com.heronix.teacher.service.StudentEnrollmentCache;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.Modality;
import javafx.stage.Stage;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

/**
 * Assignment Dialog Controller
 *
 * Handles creation and editing of assignments
 * Features:
 * - Multiple assignment types (Homework, Quiz, Test, Exam, etc.)
 * - Flexible grading styles (Letter, Points, Percentage)
 * - Auto-weight calculation
 * - Validation
 *
 * @author EduScheduler Team
 * @version 1.0.0
 */
@Slf4j
public class AssignmentDialogController {

    private GradebookService gradebookService;
    private Assignment assignment;
    private boolean editMode = false;
    private boolean saved = false;
    private List<String> periodCourseLabels;

    // FXML Controls
    @FXML private Label titleLabel;
    @FXML private TextField assignmentNameField;
    @FXML private ComboBox<AssignmentType> assignmentTypeCombo;
    @FXML private ComboBox<AssignmentCategory> categoryCombo;
    @FXML private ComboBox<GradingStyle> gradingStyleCombo;
    @FXML private TextField maxPointsField;
    @FXML private TextField weightField;
    @FXML private DatePicker dueDatePicker;
    @FXML private ComboBox<String> courseCombo;
    @FXML private TextArea descriptionArea;
    @FXML private CheckBox activeCheckbox;
    @FXML private Label summaryLabel;
    @FXML private Button saveButton;
    @FXML private Label maxPointsLabel;
    @FXML private Label maxPointsHelpText;

    /**
     * Initialize the dialog
     */
    public void initialize() {
        log.info("Initializing Assignment Dialog Controller");

        setupAssignmentTypeCombo();
        setupCategoryCombo();
        setupGradingStyleCombo();
        setupCourseCombo();
        setupListeners();

        log.info("Assignment Dialog Controller initialized");
    }

    /**
     * Setup assignment type combo box
     */
    private void setupAssignmentTypeCombo() {
        assignmentTypeCombo.getItems().addAll(AssignmentType.values());
        assignmentTypeCombo.setButtonCell(new ListCell<AssignmentType>() {
            @Override
            protected void updateItem(AssignmentType item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.getDisplayName());
            }
        });
        assignmentTypeCombo.setCellFactory(param -> new ListCell<AssignmentType>() {
            @Override
            protected void updateItem(AssignmentType item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.getDisplayName());
            }
        });
        assignmentTypeCombo.setValue(AssignmentType.HOMEWORK);
    }

    /**
     * Setup category combo box
     */
    private void setupCategoryCombo() {
        categoryCombo.setButtonCell(new ListCell<AssignmentCategory>() {
            @Override
            protected void updateItem(AssignmentCategory item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? "None (no weighted grading)" : item.getDisplayNameWithWeight());
            }
        });
        categoryCombo.setCellFactory(param -> new ListCell<AssignmentCategory>() {
            @Override
            protected void updateItem(AssignmentCategory item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.getDisplayNameWithWeight());
            }
        });
        loadCategories();
    }

    /**
     * Load categories from service
     */
    private void loadCategories() {
        if (gradebookService != null) {
            List<AssignmentCategory> categories = gradebookService.getAllActiveCategories();
            categoryCombo.getItems().clear();
            categoryCombo.getItems().add(null); // Add "None" option
            categoryCombo.getItems().addAll(categories);
        }
    }

    /**
     * Setup grading style combo box
     */
    private void setupGradingStyleCombo() {
        gradingStyleCombo.getItems().addAll(GradingStyle.values());
        gradingStyleCombo.setButtonCell(new ListCell<GradingStyle>() {
            @Override
            protected void updateItem(GradingStyle item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.getDisplayName());
            }
        });
        gradingStyleCombo.setCellFactory(param -> new ListCell<GradingStyle>() {
            @Override
            protected void updateItem(GradingStyle item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.getDisplayName());
            }
        });
        gradingStyleCombo.setValue(GradingStyle.POINTS);
    }

    /**
     * Setup course combo box with period-course labels if available
     */
    private void setupCourseCombo() {
        courseCombo.getItems().clear();
        if (periodCourseLabels != null && !periodCourseLabels.isEmpty()) {
            courseCombo.getItems().addAll(periodCourseLabels);
        } else {
            courseCombo.getItems().addAll(
                "Math", "English", "Science", "History", "Art", "Music",
                "Physical Education", "Computer Science", "Spanish", "French"
            );
        }
        courseCombo.setEditable(true);
    }

    /**
     * Setup listeners for real-time updates
     */
    private void setupListeners() {
        // Update summary when fields change
        assignmentNameField.textProperty().addListener((obs, oldVal, newVal) -> updateSummary());
        assignmentTypeCombo.valueProperty().addListener((obs, oldVal, newVal) -> {
            updateSummary();
            if (!editMode) {
                autoCalculateWeight();
            }
        });
        gradingStyleCombo.valueProperty().addListener((obs, oldVal, newVal) -> {
            updateSummary();
            updateMaxPointsField(newVal);
        });
        maxPointsField.textProperty().addListener((obs, oldVal, newVal) -> updateSummary());
        weightField.textProperty().addListener((obs, oldVal, newVal) -> updateSummary());
        dueDatePicker.valueProperty().addListener((obs, oldVal, newVal) -> updateSummary());
    }

    /**
     * Update max points field based on grading style
     */
    private void updateMaxPointsField(GradingStyle style) {
        if (style == null) return;

        switch (style) {
            case LETTER -> {
                maxPointsLabel.setText("Letter Grades");
                maxPointsField.setPromptText("Not applicable");
                maxPointsField.setText("100");
                maxPointsField.setDisable(true);
                maxPointsHelpText.setText("Letter grades use standard A-F scale");
            }
            case POINTS -> {
                maxPointsLabel.setText("Maximum Points *");
                maxPointsField.setPromptText("e.g., 100");
                maxPointsField.setDisable(false);
                maxPointsHelpText.setText("Enter the maximum points possible for this assignment");
            }
            case PERCENTAGE -> {
                maxPointsLabel.setText("Percentage Scale");
                maxPointsField.setPromptText("Always 100");
                maxPointsField.setText("100");
                maxPointsField.setDisable(true);
                maxPointsHelpText.setText("Percentage grades are entered as 0-100%");
            }
        }
    }

    /**
     * Auto-calculate weight based on assignment type
     */
    @FXML
    public void autoCalculateWeight() {
        AssignmentType type = assignmentTypeCombo.getValue();
        if (type != null) {
            double weight = type.getDefaultWeight() * 100; // Convert to percentage
            weightField.setText(String.format("%.0f", weight));
        }
    }

    /**
     * Update summary panel
     */
    private void updateSummary() {
        StringBuilder summary = new StringBuilder();

        String name = assignmentNameField.getText();
        AssignmentType type = assignmentTypeCombo.getValue();
        GradingStyle style = gradingStyleCombo.getValue();
        String points = maxPointsField.getText();
        String weight = weightField.getText();

        if (name != null && !name.trim().isEmpty()) {
            summary.append("Name: ").append(name).append("\n");
        }

        if (type != null) {
            summary.append("Type: ").append(type.getDisplayName());
            if (type.isMajorAssessment()) {
                summary.append(" (Major Assessment)");
            }
            summary.append("\n");
        }

        if (style != null) {
            summary.append("Grading: ").append(style.getDisplayName()).append("\n");
        }

        if (points != null && !points.trim().isEmpty()) {
            try {
                double maxPts = Double.parseDouble(points);
                if (style == GradingStyle.POINTS) {
                    summary.append("Max Points: ").append(String.format("%.0f", maxPts)).append("\n");
                }
            } catch (NumberFormatException ignored) {
            }
        }

        if (weight != null && !weight.trim().isEmpty()) {
            try {
                double wt = Double.parseDouble(weight);
                summary.append("Weight: ").append(String.format("%.0f%%", wt)).append(" of final grade\n");
            } catch (NumberFormatException ignored) {
            }
        }

        if (summary.length() == 0) {
            summary.append("Fill in the fields above");
        }

        summaryLabel.setText(summary.toString().trim());
    }

    /**
     * Set the gradebook service
     */
    public void setGradebookService(GradebookService service) {
        this.gradebookService = service;
        loadCategories(); // Reload categories when service is set
    }

    /**
     * Set course options from enrollment cache period-course labels.
     * Must be called after the FXML is loaded (after initialize()).
     */
    public void setCourseOptions(List<String> labels) {
        this.periodCourseLabels = labels;
        if (labels != null && !labels.isEmpty()) {
            courseCombo.getItems().clear();
            courseCombo.getItems().addAll(labels);
        }
    }

    /**
     * Open category management dialog
     */
    @FXML
    public void manageCategories() {
        try {
            FXMLLoader loader = new FXMLLoader(
                getClass().getResource("/fxml/CategoryManagementDialog.fxml"));
            Parent root = loader.load();

            CategoryManagementDialogController controller = loader.getController();
            controller.setGradebookService(gradebookService);

            Stage dialog = new Stage();
            dialog.setTitle("Manage Assignment Categories");
            dialog.setScene(new Scene(root));
            dialog.initModality(Modality.APPLICATION_MODAL);
            dialog.showAndWait();

            // Reload categories after dialog closes
            loadCategories();

        } catch (Exception e) {
            log.error("Error opening category management dialog", e);
            showError("Failed to open category management: " + e.getMessage());
        }
    }

    /**
     * Set assignment for editing
     */
    public void setAssignment(Assignment assignment) {
        this.assignment = assignment;
        this.editMode = true;
        titleLabel.setText("Edit Assignment");
        loadAssignment();
    }

    /**
     * Load assignment data into form
     */
    private void loadAssignment() {
        if (assignment == null) return;

        assignmentNameField.setText(assignment.getName());
        assignmentTypeCombo.setValue(assignment.getAssignmentType());
        categoryCombo.setValue(assignment.getCategory());
        gradingStyleCombo.setValue(assignment.getGradingStyle());
        maxPointsField.setText(assignment.getMaxPoints() != null ?
            String.format("%.0f", assignment.getMaxPoints()) : "");
        weightField.setText(assignment.getWeight() != null ?
            String.format("%.0f", assignment.getWeight() * 100) : "");
        dueDatePicker.setValue(assignment.getDueDate());
        courseCombo.setValue(assignment.getCourseName());
        descriptionArea.setText(assignment.getDescription());
        activeCheckbox.setSelected(assignment.getActive() != null && assignment.getActive());
    }

    /**
     * Validate form
     */
    private boolean validateForm() {
        StringBuilder errors = new StringBuilder();

        if (assignmentNameField.getText() == null || assignmentNameField.getText().trim().isEmpty()) {
            errors.append("• Assignment name is required\n");
        }

        if (assignmentTypeCombo.getValue() == null) {
            errors.append("• Assignment type is required\n");
        }

        if (gradingStyleCombo.getValue() == null) {
            errors.append("• Grading style is required\n");
        }

        if (gradingStyleCombo.getValue() == GradingStyle.POINTS) {
            try {
                double points = Double.parseDouble(maxPointsField.getText());
                if (points <= 0) {
                    errors.append("• Maximum points must be greater than 0\n");
                }
            } catch (NumberFormatException e) {
                errors.append("• Maximum points must be a valid number\n");
            }
        }

        if (errors.length() > 0) {
            showError("Validation Error", errors.toString());
            return false;
        }

        return true;
    }

    /**
     * Save assignment
     */
    @FXML
    public void save() {
        if (!validateForm()) {
            return;
        }

        try {
            if (assignment == null) {
                assignment = new Assignment();
            }

            assignment.setName(assignmentNameField.getText().trim());
            assignment.setAssignmentType(assignmentTypeCombo.getValue());
            assignment.setCategory(categoryCombo.getValue());
            assignment.setGradingStyle(gradingStyleCombo.getValue());

            double maxPoints = gradingStyleCombo.getValue() == GradingStyle.POINTS ?
                Double.parseDouble(maxPointsField.getText()) : 100.0;
            assignment.setMaxPoints(maxPoints);

            if (weightField.getText() != null && !weightField.getText().trim().isEmpty()) {
                double weight = Double.parseDouble(weightField.getText()) / 100.0; // Convert from percentage
                assignment.setWeight(weight);
            } else {
                assignment.setWeight(null);
                assignment.autoCalculateWeight();
            }

            assignment.setDueDate(dueDatePicker.getValue());

            // Extract period number and course name from combo selection
            String courseSelection = courseCombo.getValue();
            Integer periodNumber = StudentEnrollmentCache.parsePeriodFromLabel(courseSelection);
            if (periodNumber != null) {
                assignment.setPeriodNumber(periodNumber);
                // Extract just the course name part for display
                int dashIdx = courseSelection.indexOf(" - ");
                if (dashIdx >= 0) {
                    String courseNamePart = courseSelection.substring(dashIdx + 3);
                    // Strip trailing course code like " (MATH101)"
                    int parenIdx = courseNamePart.lastIndexOf(" (");
                    if (parenIdx >= 0) {
                        courseNamePart = courseNamePart.substring(0, parenIdx);
                    }
                    assignment.setCourseName(courseNamePart);
                } else {
                    assignment.setCourseName(courseSelection);
                }
            } else {
                assignment.setCourseName(courseSelection);
            }

            assignment.setDescription(descriptionArea.getText());
            assignment.setActive(activeCheckbox.isSelected());

            if (editMode) {
                gradebookService.updateAssignment(assignment);
                log.info("Updated assignment: {}", assignment.getName());
            } else {
                gradebookService.createAssignment(assignment);
                log.info("Created new assignment: {}", assignment.getName());
            }

            saved = true;
            closeDialog();

        } catch (Exception e) {
            log.error("Error saving assignment", e);
            showError("Save Error", "Failed to save assignment: " + e.getMessage());
        }
    }

    /**
     * Cancel and close dialog
     */
    @FXML
    public void cancel() {
        saved = false;
        closeDialog();
    }

    /**
     * Close dialog
     */
    private void closeDialog() {
        Stage stage = (Stage) saveButton.getScene().getWindow();
        stage.close();
    }

    /**
     * Check if assignment was saved
     */
    public boolean isSaved() {
        return saved;
    }

    /**
     * Get the saved assignment
     */
    public Assignment getAssignment() {
        return assignment;
    }

    /**
     * Show error alert
     */
    private void showError(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    /**
     * Show error alert (single parameter version)
     */
    private void showError(String message) {
        showError("Error", message);
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
}
