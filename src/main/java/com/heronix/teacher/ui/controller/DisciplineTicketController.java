package com.heronix.teacher.ui.controller;

import com.heronix.teacher.model.DisciplinePromptTemplate;
import com.heronix.teacher.model.domain.Student;
import com.heronix.teacher.repository.StudentRepository;
import com.heronix.teacher.service.AdminApiClient;
import com.heronix.teacher.service.SessionManager;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.VBox;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Controller for the Discipline Ticket submission view.
 *
 * Allows teachers to submit discipline referrals using pre-built prompt templates
 * with proper K-12 legal/educational terminology.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DisciplineTicketController {

    private final AdminApiClient adminApiClient;
    private final SessionManager sessionManager;
    private final StudentRepository studentRepository;

    // Student search
    @FXML private TextField studentSearchField;
    @FXML private ListView<Student> studentListView;
    @FXML private VBox selectedStudentCard;
    @FXML private Label selectedStudentName;
    @FXML private Label selectedStudentGrade;
    @FXML private Label selectedStudentId;

    // Template grid
    @FXML private FlowPane templateGrid;

    // Form fields
    @FXML private ComboBox<String> behaviorTypeCombo;
    @FXML private ComboBox<String> categoryCombo;
    @FXML private ComboBox<String> severityCombo;
    @FXML private ComboBox<String> locationCombo;
    @FXML private DatePicker incidentDatePicker;
    @FXML private TextField incidentTimeField;
    @FXML private TextArea descriptionArea;
    @FXML private Label placeholderHint;
    @FXML private TextArea interventionArea;
    @FXML private CheckBox parentContactedCb;
    @FXML private CheckBox adminReferralCb;

    // Submit
    @FXML private Button submitBtn;
    @FXML private Label feedbackLabel;

    // Recent submissions table
    @FXML private TableView<RecentSubmission> recentSubmissionsTable;
    @FXML private TableColumn<RecentSubmission, String> recentTimeCol;
    @FXML private TableColumn<RecentSubmission, String> recentStudentCol;
    @FXML private TableColumn<RecentSubmission, String> recentCategoryCol;
    @FXML private TableColumn<RecentSubmission, String> recentSeverityCol;
    @FXML private TableColumn<RecentSubmission, String> recentStatusCol;

    private Student selectedStudent;
    private DisciplinePromptTemplate selectedTemplate;
    private final ObservableList<RecentSubmission> recentSubmissions = FXCollections.observableArrayList();
    private List<Student> allStudents = new ArrayList<>();

    private static final String[] POSITIVE_CATEGORIES = {
            "PARTICIPATION", "COLLABORATION", "LEADERSHIP", "IMPROVEMENT", "HELPING_OTHERS", "OTHER"
    };
    private static final String[] NEGATIVE_CATEGORIES = {
            "DISRUPTION", "TARDINESS", "NON_COMPLIANCE", "BULLYING", "FIGHTING", "DEFIANCE",
            "INAPPROPRIATE_LANGUAGE", "VANDALISM", "THEFT", "HARASSMENT", "TECHNOLOGY_MISUSE",
            "DRESS_CODE_VIOLATION", "OTHER"
    };
    private static final String[] SEVERITY_LEVELS = {"MINOR", "MODERATE", "MAJOR", "SEVERE"};
    private static final String[] LOCATIONS = {
            "CLASSROOM", "HALLWAY", "CAFETERIA", "GYMNASIUM", "LIBRARY", "AUDITORIUM",
            "PARKING_LOT", "BUS", "BATHROOM", "PLAYGROUND", "OTHER"
    };

    @FXML
    public void initialize() {
        log.info("Initializing DisciplineTicketController");

        setupFormFields();
        setupTemplateGrid();
        setupStudentSearch();
        setupRecentSubmissionsTable();

        // Default date/time to now
        incidentDatePicker.setValue(LocalDate.now());
        incidentTimeField.setText(LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm")));

        // Load students in background
        loadStudents();

        log.info("DisciplineTicketController initialized");
    }

    private void setupFormFields() {
        behaviorTypeCombo.setItems(FXCollections.observableArrayList("POSITIVE", "NEGATIVE"));
        behaviorTypeCombo.setValue("NEGATIVE");

        categoryCombo.setItems(FXCollections.observableArrayList(NEGATIVE_CATEGORIES));
        severityCombo.setItems(FXCollections.observableArrayList(SEVERITY_LEVELS));
        locationCombo.setItems(FXCollections.observableArrayList(LOCATIONS));
        locationCombo.setValue("CLASSROOM");

        // Switch categories when behavior type changes
        behaviorTypeCombo.setOnAction(e -> {
            String type = behaviorTypeCombo.getValue();
            categoryCombo.getItems().clear();
            if ("POSITIVE".equals(type)) {
                categoryCombo.setItems(FXCollections.observableArrayList(POSITIVE_CATEGORIES));
                severityCombo.setDisable(true);
                severityCombo.setValue(null);
                adminReferralCb.setSelected(false);
                adminReferralCb.setDisable(true);
            } else {
                categoryCombo.setItems(FXCollections.observableArrayList(NEGATIVE_CATEGORIES));
                severityCombo.setDisable(false);
                adminReferralCb.setDisable(false);
            }
        });

        // Auto-check admin referral for MAJOR/SEVERE severity
        severityCombo.setOnAction(e -> {
            String severity = severityCombo.getValue();
            if ("MAJOR".equals(severity) || "SEVERE".equals(severity)) {
                adminReferralCb.setSelected(true);
            }
        });
    }

    private void setupTemplateGrid() {
        List<DisciplinePromptTemplate> templates = DisciplinePromptTemplate.getAll();

        for (DisciplinePromptTemplate template : templates) {
            Button templateBtn = new Button(template.getDisplayName());
            templateBtn.setWrapText(true);
            templateBtn.setPrefWidth(140);
            templateBtn.setPrefHeight(50);
            templateBtn.getStyleClass().add("button-secondary");

            // Color-code by severity
            String borderColor;
            switch (template.getDefaultSeverity()) {
                case "MAJOR": borderColor = "#EF4444"; break;
                case "MODERATE": borderColor = "#F59E0B"; break;
                default: borderColor = "#3B82F6"; break;
            }
            templateBtn.setStyle(templateBtn.getStyle() +
                    "-fx-border-color: " + borderColor + "; -fx-border-width: 0 0 3 0; -fx-font-size: 11px;");

            templateBtn.setOnAction(e -> applyTemplate(template));

            Tooltip tooltip = new Tooltip(template.getDefaultSeverity() + " | " + template.getCategory());
            templateBtn.setTooltip(tooltip);

            templateGrid.getChildren().add(templateBtn);
        }
    }

    private void applyTemplate(DisciplinePromptTemplate template) {
        selectedTemplate = template;
        log.info("Template selected: {}", template.getDisplayName());

        behaviorTypeCombo.setValue("NEGATIVE");
        categoryCombo.setValue(template.getCategory());
        severityCombo.setValue(template.getDefaultSeverity());
        descriptionArea.setText(template.getDescriptionTemplate());
        interventionArea.setText(template.getSuggestedIntervention());
        adminReferralCb.setSelected(template.isRequiresAdminReferral());

        // Show placeholder hint
        if (template.getDescriptionTemplate().contains("[")) {
            placeholderHint.setText("Fill in the [bracketed] placeholders with specific details.");
            placeholderHint.setVisible(true);
            placeholderHint.setManaged(true);
        } else {
            placeholderHint.setVisible(false);
            placeholderHint.setManaged(false);
        }

        feedbackLabel.setText("Template applied: " + template.getDisplayName());
        feedbackLabel.setStyle("-fx-text-fill: #3B82F6;");
    }

    private void setupStudentSearch() {
        studentListView.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(Student student, boolean empty) {
                super.updateItem(student, empty);
                if (empty || student == null) {
                    setText(null);
                } else {
                    setText(student.getFullName() + " (Grade " + student.getGradeLevel() + ")");
                }
            }
        });

        // Filter on typing
        studentSearchField.textProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal == null || newVal.trim().isEmpty()) {
                studentListView.setItems(FXCollections.observableArrayList(allStudents));
            } else {
                String query = newVal.toLowerCase().trim();
                List<Student> filtered = allStudents.stream()
                        .filter(s -> s.getFullName().toLowerCase().contains(query)
                                || (s.getStudentId() != null && s.getStudentId().toLowerCase().contains(query)))
                        .collect(Collectors.toList());
                studentListView.setItems(FXCollections.observableArrayList(filtered));
            }
        });

        // Select student on click
        studentListView.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                selectStudent(newVal);
            }
        });
    }

    private void selectStudent(Student student) {
        selectedStudent = student;
        selectedStudentName.setText(student.getFullName());
        selectedStudentGrade.setText("Grade " + student.getGradeLevel());
        selectedStudentId.setText("ID: " + student.getStudentId());
        selectedStudentCard.setVisible(true);
        selectedStudentCard.setManaged(true);
        log.info("Student selected: {} ({})", student.getFullName(), student.getStudentId());
    }

    private void loadStudents() {
        new Thread(() -> {
            try {
                // Load from local repository (synced from server during attendance sync)
                List<Student> students = studentRepository.findByActiveTrue();
                if (students != null && !students.isEmpty()) {
                    allStudents = new ArrayList<>(students);
                    Platform.runLater(() -> {
                        studentListView.setItems(FXCollections.observableArrayList(allStudents));
                        log.info("Loaded {} active students from local database", allStudents.size());
                    });
                } else {
                    // Fallback: try API
                    log.info("No local students found, attempting API fetch");
                    var dtos = adminApiClient.getStudents();
                    if (dtos != null) {
                        allStudents = dtos.stream().map(dto -> {
                            Student s = new Student();
                            s.setServerId(dto.getId());
                            s.setStudentId(dto.getStudentId());
                            s.setFirstName(dto.getFirstName());
                            s.setLastName(dto.getLastName());
                            s.setGradeLevel(dto.getGradeLevel());
                            s.setEmail(dto.getEmail());
                            return s;
                        }).collect(Collectors.toList());
                        Platform.runLater(() -> {
                            studentListView.setItems(FXCollections.observableArrayList(allStudents));
                            log.info("Loaded {} students from API", allStudents.size());
                        });
                    }
                }
            } catch (Exception e) {
                log.error("Failed to load students", e);
                Platform.runLater(() -> {
                    feedbackLabel.setText("Could not load student list. Check server connection.");
                    feedbackLabel.setStyle("-fx-text-fill: #F59E0B;");
                });
            }
        }).start();
    }

    private void setupRecentSubmissionsTable() {
        recentTimeCol.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().time));
        recentStudentCol.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().studentName));
        recentCategoryCol.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().category));
        recentSeverityCol.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().severity));
        recentStatusCol.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().status));
        recentSubmissionsTable.setItems(recentSubmissions);
        recentSubmissionsTable.setPlaceholder(new Label("No submissions yet this session"));
    }

    @FXML
    public void handleSubmit() {
        // Validate
        if (selectedStudent == null) {
            showFeedback("Please select a student first.", true);
            return;
        }
        if (behaviorTypeCombo.getValue() == null) {
            showFeedback("Please select a behavior type.", true);
            return;
        }
        if (categoryCombo.getValue() == null) {
            showFeedback("Please select a category.", true);
            return;
        }
        if (descriptionArea.getText() == null || descriptionArea.getText().trim().isEmpty()) {
            showFeedback("Please provide an incident description.", true);
            return;
        }
        // Check for unfilled placeholders
        String desc = descriptionArea.getText();
        if (desc.contains("[") && desc.contains("]")) {
            showFeedback("Please fill in all [bracketed] placeholders in the description.", true);
            return;
        }

        // Build data map (matches AdminApiClient.createBehaviorIncident pattern)
        Map<String, Object> data = new LinkedHashMap<>();
        Long studentId = selectedStudent.getServerId() != null ? selectedStudent.getServerId() : selectedStudent.getId();
        data.put("studentId", studentId);
        data.put("reportingTeacherId", adminApiClient.getTeacherId());
        data.put("incidentDate", incidentDatePicker.getValue().toString());
        data.put("incidentTime", incidentTimeField.getText() + ":00");
        data.put("behaviorType", behaviorTypeCombo.getValue());
        data.put("behaviorCategory", categoryCombo.getValue());
        if (severityCombo.getValue() != null) {
            data.put("severityLevel", severityCombo.getValue());
        }
        data.put("incidentLocation", locationCombo.getValue());
        data.put("incidentDescription", desc.trim());
        if (interventionArea.getText() != null && !interventionArea.getText().trim().isEmpty()) {
            data.put("interventionApplied", interventionArea.getText().trim());
        }
        data.put("parentContacted", parentContactedCb.isSelected());
        data.put("adminReferralRequired", adminReferralCb.isSelected());

        // Disable submit and show progress
        submitBtn.setDisable(true);
        showFeedback("Submitting...", false);

        new Thread(() -> {
            try {
                adminApiClient.createBehaviorIncident(data);
                Platform.runLater(() -> {
                    showFeedback("Incident submitted successfully!", false);
                    feedbackLabel.setStyle("-fx-text-fill: #10B981;");

                    // Add to recent submissions
                    recentSubmissions.add(0, new RecentSubmission(
                            LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm")),
                            selectedStudent.getFullName(),
                            categoryCombo.getValue(),
                            severityCombo.getValue() != null ? severityCombo.getValue() : "N/A",
                            adminReferralCb.isSelected() ? "Referred" : "Submitted"
                    ));

                    // Keep only last 10
                    if (recentSubmissions.size() > 10) {
                        recentSubmissions.remove(10, recentSubmissions.size());
                    }

                    clearForm();
                    submitBtn.setDisable(false);
                });
            } catch (Exception ex) {
                log.error("Failed to submit discipline incident", ex);
                Platform.runLater(() -> {
                    showFeedback("Failed to submit: " + ex.getMessage(), true);
                    submitBtn.setDisable(false);
                });
            }
        }).start();
    }

    @FXML
    public void handleClearForm() {
        clearForm();
        feedbackLabel.setText("");
    }

    private void clearForm() {
        behaviorTypeCombo.setValue("NEGATIVE");
        categoryCombo.setValue(null);
        categoryCombo.setItems(FXCollections.observableArrayList(NEGATIVE_CATEGORIES));
        severityCombo.setValue(null);
        severityCombo.setDisable(false);
        locationCombo.setValue("CLASSROOM");
        incidentDatePicker.setValue(LocalDate.now());
        incidentTimeField.setText(LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm")));
        descriptionArea.clear();
        interventionArea.clear();
        parentContactedCb.setSelected(false);
        adminReferralCb.setSelected(false);
        adminReferralCb.setDisable(false);
        placeholderHint.setVisible(false);
        placeholderHint.setManaged(false);
        selectedTemplate = null;
    }

    private void showFeedback(String message, boolean isError) {
        feedbackLabel.setText(message);
        feedbackLabel.setStyle(isError ? "-fx-text-fill: #EF4444;" : "-fx-text-fill: #3B82F6;");
    }

    /**
     * Simple record for the recent submissions table.
     */
    public static class RecentSubmission {
        public final String time;
        public final String studentName;
        public final String category;
        public final String severity;
        public final String status;

        public RecentSubmission(String time, String studentName, String category, String severity, String status) {
            this.time = time;
            this.studentName = studentName;
            this.category = category;
            this.severity = severity;
            this.status = status;
        }
    }
}
