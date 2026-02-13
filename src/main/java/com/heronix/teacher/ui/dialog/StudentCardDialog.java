package com.heronix.teacher.ui.dialog;

import com.heronix.teacher.model.dto.StudentDTO;
import com.heronix.teacher.service.AdminApiClient;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Window;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Student Card popup for the Teacher portal.
 * Shows student overview, behavior history, and a discipline-issue form.
 * Communicates with SIS Server exclusively via {@link AdminApiClient}.
 */
@Slf4j
public final class StudentCardDialog {

    private StudentCardDialog() { }

    // ── Enum value constants (mirror server-side enums) ──────────────
    private static final List<String> POSITIVE_CATEGORIES = List.of(
            "PARTICIPATION", "COLLABORATION", "LEADERSHIP", "IMPROVEMENT", "HELPING_OTHERS", "OTHER");
    private static final List<String> NEGATIVE_CATEGORIES = List.of(
            "DISRUPTION", "TARDINESS", "NON_COMPLIANCE", "BULLYING", "FIGHTING",
            "DEFIANCE", "INAPPROPRIATE_LANGUAGE", "VANDALISM", "THEFT",
            "HARASSMENT", "TECHNOLOGY_MISUSE", "DRESS_CODE_VIOLATION", "OTHER");
    private static final List<String> SEVERITY_LEVELS = List.of("MINOR", "MODERATE", "MAJOR", "SEVERE");
    private static final List<String> LOCATIONS = List.of(
            "CLASSROOM", "HALLWAY", "CAFETERIA", "GYMNASIUM", "LIBRARY",
            "AUDITORIUM", "PARKING_LOT", "BUS", "BATHROOM", "PLAYGROUND", "OTHER");

    /**
     * Open the Student Card dialog.
     *
     * @param apiClient   authenticated AdminApiClient
     * @param studentId   server-side student ID (Long)
     * @param studentName display name (can be null; fetched if needed)
     * @param owner       parent window
     */
    public static void show(AdminApiClient apiClient, Long studentId,
                            String studentName, Window owner) {

        // Attempt to fetch student detail from server
        StudentDTO student = null;
        try {
            student = apiClient.getStudent(studentId);
        } catch (Exception e) {
            log.warn("Could not fetch student detail for ID {}: {}", studentId, e.getMessage());
        }

        final StudentDTO stu = student;
        String displayName = (stu != null) ? stu.getFullName()
                : (studentName != null ? studentName : "Student #" + studentId);

        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Student Card \u2014 " + displayName);
        dialog.setResizable(true);
        dialog.initOwner(owner);

        DialogPane pane = dialog.getDialogPane();
        pane.getButtonTypes().add(ButtonType.CLOSE);
        pane.setPrefSize(700, 620);

        VBox root = new VBox(0);

        // ── Header ──
        HBox header = buildHeader(stu, displayName, studentId);

        // ── Tabs ──
        TabPane tabPane = new TabPane();
        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        VBox.setVgrow(tabPane, Priority.ALWAYS);

        // Behavior history table (shared so Issue-tab can refresh it)
        ObservableList<Map<String, Object>> incidentData = FXCollections.observableArrayList();

        tabPane.getTabs().addAll(
                buildOverviewTab(stu, studentId),
                buildBehaviorHistoryTab(apiClient, studentId, incidentData),
                buildIssueDisciplineTab(apiClient, studentId, incidentData, tabPane)
        );

        root.getChildren().addAll(header, tabPane);
        pane.setContent(root);

        dialog.showAndWait();
    }

    // ====================================================================
    // HEADER
    // ====================================================================

    private static HBox buildHeader(StudentDTO stu, String displayName, Long studentId) {
        HBox header = new HBox(16);
        header.setPadding(new Insets(18, 24, 18, 24));
        header.setAlignment(Pos.CENTER_LEFT);
        header.setStyle("-fx-background-color: linear-gradient(to right, #1E3A5F, #2563EB);");

        // Initials placeholder
        StackPane photo = new StackPane();
        photo.setPrefSize(64, 80);
        photo.setMinSize(64, 80);
        photo.setMaxSize(64, 80);
        photo.setStyle("-fx-background-color: #94A3B8; -fx-background-radius: 4;");
        String initials = "";
        if (stu != null) {
            if (stu.getFirstName() != null && !stu.getFirstName().isEmpty())
                initials += stu.getFirstName().charAt(0);
            if (stu.getLastName() != null && !stu.getLastName().isEmpty())
                initials += stu.getLastName().charAt(0);
        }
        Label initialsLabel = new Label(initials.toUpperCase());
        initialsLabel.setStyle("-fx-text-fill: white; -fx-font-size: 22px; -fx-font-weight: bold;");
        photo.getChildren().add(initialsLabel);
        header.getChildren().add(photo);

        // Right side
        VBox info = new VBox(4);
        Label nameLabel = new Label(displayName);
        nameLabel.setFont(Font.font("System", FontWeight.BOLD, 18));
        nameLabel.setTextFill(Color.WHITE);

        String idStr = (stu != null && stu.getStudentId() != null) ? stu.getStudentId() : String.valueOf(studentId);
        Label idLabel = new Label("ID: " + idStr);
        idLabel.setStyle("-fx-text-fill: #CBD5E1; -fx-font-size: 12px;");

        String grade = (stu != null && stu.getGradeLevel() != null) ? "Grade " + stu.getGradeLevel() : "";
        Label gradeLabel = new Label(grade);
        gradeLabel.setStyle("-fx-text-fill: #CBD5E1; -fx-font-size: 12px;");

        HBox badgeRow = new HBox(8);
        badgeRow.setAlignment(Pos.CENTER_LEFT);
        if (stu != null && stu.getCurrentGpa() != null) {
            Label gpaBadge = new Label(String.format("GPA %.2f", stu.getCurrentGpa()));
            gpaBadge.setStyle("-fx-background-color: #10B981; -fx-text-fill: white; -fx-font-size: 11px; -fx-font-weight: 600; -fx-padding: 2 10; -fx-background-radius: 10;");
            badgeRow.getChildren().add(gpaBadge);
        }
        if (stu != null && Boolean.TRUE.equals(stu.getHasIep())) {
            badgeRow.getChildren().add(flagBadge("IEP", "#8B5CF6"));
        }
        if (stu != null && Boolean.TRUE.equals(stu.getHas504())) {
            badgeRow.getChildren().add(flagBadge("504", "#F59E0B"));
        }

        info.getChildren().addAll(nameLabel, idLabel, gradeLabel, badgeRow);
        HBox.setHgrow(info, Priority.ALWAYS);
        header.getChildren().add(info);

        return header;
    }

    // ====================================================================
    // TAB 1: OVERVIEW
    // ====================================================================

    private static Tab buildOverviewTab(StudentDTO stu, Long studentId) {
        Tab tab = new Tab("Overview");

        VBox content = new VBox(14);
        content.setPadding(new Insets(16));

        if (stu == null) {
            content.getChildren().add(infoLabel("Student detail not available (server unreachable)."));
            tab.setContent(content);
            return tab;
        }

        // Summary cards
        HBox cards = new HBox(12);
        cards.getChildren().addAll(
                summaryCard("GPA", stu.getCurrentGpa() != null ? String.format("%.2f", stu.getCurrentGpa()) : "N/A", "#3B82F6"),
                summaryCard("Grade", stu.getGradeLevel() != null ? String.valueOf(stu.getGradeLevel()) : "N/A", "#8B5CF6"),
                summaryCard("Status", Boolean.TRUE.equals(stu.getActive()) ? "Active" : "Inactive", "#10B981")
        );

        // Detail section
        VBox detailSection = new VBox(8);
        detailSection.setPadding(new Insets(12));
        detailSection.setStyle("-fx-background-color: #F8FAFC; -fx-background-radius: 8; -fx-border-color: #E2E8F0; -fx-border-radius: 8;");

        Label sectionTitle = new Label("Student Information");
        sectionTitle.setStyle("-fx-font-size: 14px; -fx-font-weight: 600; -fx-text-fill: #0F172A;");

        GridPane grid = new GridPane();
        grid.setHgap(16);
        grid.setVgap(6);
        int row = 0;
        addRow(grid, row++, "Full Name", stu.getFullName());
        addRow(grid, row++, "Student ID", stu.getStudentId() != null ? stu.getStudentId() : "N/A");
        addRow(grid, row++, "Email", stu.getEmail() != null ? stu.getEmail() : "N/A");
        addRow(grid, row++, "Grade Level", stu.getGradeLevel() != null ? String.valueOf(stu.getGradeLevel()) : "N/A");
        addRow(grid, row++, "Date of Birth", stu.getDateOfBirth() != null ? stu.getDateOfBirth().format(DateTimeFormatter.ofPattern("MM/dd/yyyy")) : "N/A");

        detailSection.getChildren().addAll(sectionTitle, grid);

        // Flags
        HBox flags = new HBox(8);
        flags.setPadding(new Insets(4, 0, 0, 0));
        if (Boolean.TRUE.equals(stu.getHasIep())) flags.getChildren().add(flagBadge("IEP", "#8B5CF6"));
        if (Boolean.TRUE.equals(stu.getHas504())) flags.getChildren().add(flagBadge("504", "#F59E0B"));
        if (flags.getChildren().isEmpty()) {
            Label noFlags = new Label("No special program flags");
            noFlags.setStyle("-fx-text-fill: #94A3B8; -fx-font-size: 12px;");
            flags.getChildren().add(noFlags);
        }

        content.getChildren().addAll(cards, detailSection, flags);

        ScrollPane sp = new ScrollPane(content);
        sp.setFitToWidth(true);
        sp.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        tab.setContent(sp);
        return tab;
    }

    // ====================================================================
    // TAB 2: BEHAVIOR HISTORY
    // ====================================================================

    private static Tab buildBehaviorHistoryTab(AdminApiClient apiClient, Long studentId,
                                               ObservableList<Map<String, Object>> data) {
        Tab tab = new Tab("Behavior History");

        VBox content = new VBox(10);
        content.setPadding(new Insets(12));

        TableView<Map<String, Object>> table = new TableView<>(data);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setPlaceholder(new Label("No behavior records found"));
        VBox.setVgrow(table, Priority.ALWAYS);

        TableColumn<Map<String, Object>, String> dateCol = new TableColumn<>("Date");
        dateCol.setCellValueFactory(c -> new SimpleStringProperty(strVal(c.getValue(), "incidentDate")));
        dateCol.setPrefWidth(90);

        TableColumn<Map<String, Object>, String> catCol = new TableColumn<>("Category");
        catCol.setCellValueFactory(c -> new SimpleStringProperty(displayEnum(strVal(c.getValue(), "behaviorCategory"))));
        catCol.setPrefWidth(130);

        TableColumn<Map<String, Object>, String> sevCol = new TableColumn<>("Severity");
        sevCol.setCellValueFactory(c -> new SimpleStringProperty(displayEnum(strVal(c.getValue(), "severityLevel"))));
        sevCol.setPrefWidth(80);

        TableColumn<Map<String, Object>, String> statusCol = new TableColumn<>("Status");
        statusCol.setCellValueFactory(c -> new SimpleStringProperty(displayEnum(strVal(c.getValue(), "status"))));
        statusCol.setPrefWidth(90);

        TableColumn<Map<String, Object>, String> locCol = new TableColumn<>("Location");
        locCol.setCellValueFactory(c -> new SimpleStringProperty(displayEnum(strVal(c.getValue(), "incidentLocation"))));
        locCol.setPrefWidth(100);

        table.getColumns().addAll(dateCol, catCol, sevCol, statusCol, locCol);

        content.getChildren().add(table);
        tab.setContent(content);

        // Load data lazily when tab is first selected
        tab.setOnSelectionChanged(e -> {
            if (tab.isSelected() && data.isEmpty()) {
                loadBehaviorIncidents(apiClient, studentId, data);
            }
        });

        // Also load eagerly on first show
        loadBehaviorIncidents(apiClient, studentId, data);

        return tab;
    }

    private static void loadBehaviorIncidents(AdminApiClient apiClient, Long studentId,
                                              ObservableList<Map<String, Object>> data) {
        try {
            List<Map<String, Object>> incidents = apiClient.getStudentBehaviorIncidents(studentId);
            data.setAll(incidents);
            log.debug("Loaded {} behavior incidents for student {}", incidents.size(), studentId);
        } catch (Exception e) {
            log.warn("Could not load behavior incidents for student {}: {}", studentId, e.getMessage());
        }
    }

    // ====================================================================
    // TAB 3: ISSUE DISCIPLINE
    // ====================================================================

    private static Tab buildIssueDisciplineTab(AdminApiClient apiClient, Long studentId,
                                               ObservableList<Map<String, Object>> historyData,
                                               TabPane tabPane) {
        Tab tab = new Tab("Issue Discipline");

        VBox content = new VBox(12);
        content.setPadding(new Insets(16));

        Label title = new Label("Report a Behavior Incident");
        title.setStyle("-fx-font-size: 15px; -fx-font-weight: 700; -fx-text-fill: #0F172A;");

        // ── Form fields ──
        ComboBox<String> typeCombo = new ComboBox<>(FXCollections.observableArrayList("POSITIVE", "NEGATIVE"));
        typeCombo.setPromptText("Behavior Type");
        typeCombo.setPrefWidth(280);

        ComboBox<String> categoryCombo = new ComboBox<>();
        categoryCombo.setPromptText("Category");
        categoryCombo.setPrefWidth(280);

        ComboBox<String> severityCombo = new ComboBox<>(FXCollections.observableArrayList(SEVERITY_LEVELS));
        severityCombo.setPromptText("Severity Level");
        severityCombo.setPrefWidth(280);

        ComboBox<String> locationCombo = new ComboBox<>(FXCollections.observableArrayList(LOCATIONS));
        locationCombo.setPromptText("Location");
        locationCombo.setPrefWidth(280);

        TextArea descArea = new TextArea();
        descArea.setPromptText("Describe the incident...");
        descArea.setPrefRowCount(3);
        descArea.setWrapText(true);

        TextArea interventionArea = new TextArea();
        interventionArea.setPromptText("Intervention applied (optional)...");
        interventionArea.setPrefRowCount(2);
        interventionArea.setWrapText(true);

        CheckBox parentContactedCb = new CheckBox("Parent Contacted");
        CheckBox adminReferralCb = new CheckBox("Admin Referral Required");

        Button submitBtn = new Button("Submit Incident");
        submitBtn.setStyle("-fx-background-color: #2563EB; -fx-text-fill: white; -fx-font-weight: 600; -fx-padding: 8 24; -fx-background-radius: 6;");

        Label feedbackLabel = new Label();
        feedbackLabel.setWrapText(true);

        // ── Dynamic category filtering ──
        typeCombo.setOnAction(e -> {
            String type = typeCombo.getValue();
            categoryCombo.getItems().clear();
            if ("POSITIVE".equals(type)) {
                categoryCombo.getItems().addAll(POSITIVE_CATEGORIES);
                severityCombo.setDisable(true);
                severityCombo.setValue(null);
            } else if ("NEGATIVE".equals(type)) {
                categoryCombo.getItems().addAll(NEGATIVE_CATEGORIES);
                severityCombo.setDisable(false);
            }
        });

        // ── Layout with labels ──
        GridPane form = new GridPane();
        form.setHgap(12);
        form.setVgap(10);
        int r = 0;
        form.add(formLabel("Type *"), 0, r);
        form.add(typeCombo, 1, r++);
        form.add(formLabel("Category *"), 0, r);
        form.add(categoryCombo, 1, r++);
        form.add(formLabel("Severity"), 0, r);
        form.add(severityCombo, 1, r++);
        form.add(formLabel("Location *"), 0, r);
        form.add(locationCombo, 1, r++);

        // ── Submit handler ──
        submitBtn.setOnAction(e -> {
            // Validation
            if (typeCombo.getValue() == null || categoryCombo.getValue() == null || locationCombo.getValue() == null) {
                feedbackLabel.setText("Please fill in all required fields (Type, Category, Location).");
                feedbackLabel.setStyle("-fx-text-fill: #EF4444; -fx-font-size: 12px;");
                return;
            }
            if (descArea.getText() == null || descArea.getText().isBlank()) {
                feedbackLabel.setText("Description is required.");
                feedbackLabel.setStyle("-fx-text-fill: #EF4444; -fx-font-size: 12px;");
                return;
            }

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("studentId", studentId);
            Long teacherId = apiClient.getTeacherId();
            if (teacherId != null) data.put("reportingTeacherId", teacherId);
            data.put("incidentDate", LocalDate.now().toString());
            data.put("incidentTime", LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm")));
            data.put("behaviorType", typeCombo.getValue());
            data.put("behaviorCategory", categoryCombo.getValue());
            if (severityCombo.getValue() != null) data.put("severityLevel", severityCombo.getValue());
            data.put("incidentLocation", locationCombo.getValue());
            data.put("incidentDescription", descArea.getText().trim());
            if (interventionArea.getText() != null && !interventionArea.getText().isBlank())
                data.put("interventionApplied", interventionArea.getText().trim());
            data.put("parentContacted", parentContactedCb.isSelected());
            data.put("adminReferralRequired", adminReferralCb.isSelected());

            submitBtn.setDisable(true);
            feedbackLabel.setText("Submitting...");
            feedbackLabel.setStyle("-fx-text-fill: #64748B; -fx-font-size: 12px;");

            new Thread(() -> {
                try {
                    apiClient.createBehaviorIncident(data);
                    javafx.application.Platform.runLater(() -> {
                        feedbackLabel.setText("Incident submitted successfully!");
                        feedbackLabel.setStyle("-fx-text-fill: #10B981; -fx-font-size: 12px; -fx-font-weight: 600;");
                        submitBtn.setDisable(false);

                        // Clear form
                        typeCombo.setValue(null);
                        categoryCombo.getItems().clear();
                        severityCombo.setValue(null);
                        severityCombo.setDisable(false);
                        locationCombo.setValue(null);
                        descArea.clear();
                        interventionArea.clear();
                        parentContactedCb.setSelected(false);
                        adminReferralCb.setSelected(false);

                        // Refresh behavior history
                        loadBehaviorIncidents(apiClient, studentId, historyData);
                    });
                } catch (Exception ex) {
                    log.error("Failed to create behavior incident", ex);
                    javafx.application.Platform.runLater(() -> {
                        feedbackLabel.setText("Failed to submit: " + ex.getMessage());
                        feedbackLabel.setStyle("-fx-text-fill: #EF4444; -fx-font-size: 12px;");
                        submitBtn.setDisable(false);
                    });
                }
            }).start();
        });

        HBox checkBoxes = new HBox(16, parentContactedCb, adminReferralCb);
        checkBoxes.setPadding(new Insets(4, 0, 0, 0));

        content.getChildren().addAll(
                title, form,
                formLabel("Description *"), descArea,
                formLabel("Intervention Applied"), interventionArea,
                checkBoxes, submitBtn, feedbackLabel
        );

        ScrollPane sp = new ScrollPane(content);
        sp.setFitToWidth(true);
        sp.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        tab.setContent(sp);
        return tab;
    }

    // ====================================================================
    // HELPERS
    // ====================================================================

    private static VBox summaryCard(String title, String value, String color) {
        VBox card = new VBox(2);
        card.setPadding(new Insets(12));
        card.setAlignment(Pos.CENTER);
        card.setStyle("-fx-background-color: white; -fx-background-radius: 8; -fx-border-color: #E2E8F0; -fx-border-radius: 8;");
        card.setPrefWidth(140);

        Label titleLbl = new Label(title);
        titleLbl.setStyle("-fx-font-size: 11px; -fx-text-fill: #64748B;");

        Label valueLbl = new Label(value);
        valueLbl.setStyle("-fx-font-size: 22px; -fx-font-weight: 700; -fx-text-fill: " + color + ";");

        card.getChildren().addAll(titleLbl, valueLbl);
        return card;
    }

    private static Label flagBadge(String text, String color) {
        Label badge = new Label(text);
        badge.setStyle(String.format(
                "-fx-background-color: %s20; -fx-text-fill: %s; -fx-font-size: 11px; -fx-font-weight: 600; -fx-padding: 3 10; -fx-background-radius: 10;",
                color, color));
        return badge;
    }

    private static void addRow(GridPane grid, int row, String label, String value) {
        Label lbl = new Label(label + ":");
        lbl.setStyle("-fx-font-size: 12px; -fx-text-fill: #64748B;");
        lbl.setMinWidth(100);
        Label val = new Label(value);
        val.setStyle("-fx-font-size: 12px; -fx-text-fill: #0F172A;");
        grid.add(lbl, 0, row);
        grid.add(val, 1, row);
    }

    private static Label formLabel(String text) {
        Label lbl = new Label(text);
        lbl.setStyle("-fx-font-size: 12px; -fx-font-weight: 600; -fx-text-fill: #334155;");
        lbl.setMinWidth(100);
        return lbl;
    }

    private static Label infoLabel(String text) {
        Label lbl = new Label(text);
        lbl.setStyle("-fx-text-fill: #94A3B8; -fx-font-size: 13px; -fx-padding: 24;");
        lbl.setWrapText(true);
        return lbl;
    }

    private static String strVal(Map<String, Object> map, String key) {
        Object v = map.get(key);
        return v != null ? v.toString() : "";
    }

    private static String displayEnum(String raw) {
        if (raw == null || raw.isEmpty()) return "";
        return raw.replace('_', ' ');
    }
}
