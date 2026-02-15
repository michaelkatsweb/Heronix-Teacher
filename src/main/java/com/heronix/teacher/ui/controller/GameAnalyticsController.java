package com.heronix.teacher.ui.controller;

import com.heronix.teacher.model.domain.Student;
import com.heronix.teacher.repository.StudentRepository;
import com.heronix.teacher.service.EdGamesApiClient;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;

import java.io.File;
import java.io.FileWriter;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Controller for Game Analytics view
 * Displays student play time statistics and allows sharing with parents
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class GameAnalyticsController {

    private final EdGamesApiClient edGamesApiClient;
    private final StudentRepository studentRepository;

    @FXML private DatePicker startDatePicker;
    @FXML private DatePicker endDatePicker;
    @FXML private Button loadReportButton;
    @FXML private Button exportCsvButton;
    @FXML private Label statusLabel;
    @FXML private ProgressIndicator loadingIndicator;

    // Class Summary
    @FXML private Label totalStudentsLabel;
    @FXML private Label activeStudentsLabel;
    @FXML private Label totalPlayTimeLabel;
    @FXML private Label totalSessionsLabel;
    @FXML private Label avgPlayTimeLabel;
    @FXML private Label avgScoreLabel;

    // Student Table
    @FXML private TableView<StudentPlayTimeRow> studentTable;
    @FXML private TableColumn<StudentPlayTimeRow, String> studentNameColumn;
    @FXML private TableColumn<StudentPlayTimeRow, String> gradeColumn;
    @FXML private TableColumn<StudentPlayTimeRow, String> playTimeColumn;
    @FXML private TableColumn<StudentPlayTimeRow, String> sessionsColumn;
    @FXML private TableColumn<StudentPlayTimeRow, String> avgSessionColumn;
    @FXML private TableColumn<StudentPlayTimeRow, String> avgScoreColumn;
    @FXML private TableColumn<StudentPlayTimeRow, String> gamesPlayedColumn;

    // Game Usage Table
    @FXML private TableView<GameUsageRow> gameTable;
    @FXML private TableColumn<GameUsageRow, String> gameNameColumn;
    @FXML private TableColumn<GameUsageRow, String> subjectColumn;
    @FXML private TableColumn<GameUsageRow, String> gamePlayTimeColumn;
    @FXML private TableColumn<GameUsageRow, String> uniquePlayersColumn;
    @FXML private TableColumn<GameUsageRow, String> gameSessionsColumn;
    @FXML private TableColumn<GameUsageRow, String> gameAvgScoreColumn;

    // Student Detail Panel
    @FXML private VBox studentDetailPanel;
    @FXML private Label selectedStudentLabel;
    @FXML private TextArea parentSummaryText;
    @FXML private Button copyToClipboardButton;
    @FXML private Button emailParentButton;

    private final ObservableList<StudentPlayTimeRow> studentData = FXCollections.observableArrayList();
    private final ObservableList<GameUsageRow> gameData = FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        // Set default date range (last 30 days)
        endDatePicker.setValue(LocalDate.now());
        startDatePicker.setValue(LocalDate.now().minusDays(30));

        // Setup student table columns
        studentNameColumn.setCellValueFactory(new PropertyValueFactory<>("studentName"));
        gradeColumn.setCellValueFactory(new PropertyValueFactory<>("grade"));
        playTimeColumn.setCellValueFactory(new PropertyValueFactory<>("playTime"));
        sessionsColumn.setCellValueFactory(new PropertyValueFactory<>("sessions"));
        avgSessionColumn.setCellValueFactory(new PropertyValueFactory<>("avgSession"));
        avgScoreColumn.setCellValueFactory(new PropertyValueFactory<>("avgScore"));
        gamesPlayedColumn.setCellValueFactory(new PropertyValueFactory<>("gamesPlayed"));

        studentTable.setItems(studentData);

        // Setup game table columns
        gameNameColumn.setCellValueFactory(new PropertyValueFactory<>("gameName"));
        subjectColumn.setCellValueFactory(new PropertyValueFactory<>("subject"));
        gamePlayTimeColumn.setCellValueFactory(new PropertyValueFactory<>("playTime"));
        uniquePlayersColumn.setCellValueFactory(new PropertyValueFactory<>("uniquePlayers"));
        gameSessionsColumn.setCellValueFactory(new PropertyValueFactory<>("sessions"));
        gameAvgScoreColumn.setCellValueFactory(new PropertyValueFactory<>("avgScore"));

        gameTable.setItems(gameData);

        // Handle student selection
        studentTable.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                loadStudentDetail(newVal.getStudentId());
            }
        });

        // Initial state
        loadingIndicator.setVisible(false);
        studentDetailPanel.setVisible(false);
        statusLabel.setText("Select a date range and click 'Load Report'");
    }

    @FXML
    public void handleLoadReport() {
        LocalDate startDate = startDatePicker.getValue();
        LocalDate endDate = endDatePicker.getValue();

        if (startDate == null || endDate == null) {
            showAlert("Please select both start and end dates");
            return;
        }

        if (startDate.isAfter(endDate)) {
            showAlert("Start date must be before end date");
            return;
        }

        loadingIndicator.setVisible(true);
        statusLabel.setText("Loading report...");
        loadReportButton.setDisable(true);

        String startStr = startDate.format(DateTimeFormatter.ISO_DATE);
        String endStr = endDate.format(DateTimeFormatter.ISO_DATE);

        CompletableFuture.runAsync(() -> {
            try {
                Map<String, Object> report = edGamesApiClient.getClassPlayTimeReport(startStr, endStr);
                Platform.runLater(() -> displayClassReport(report));
            } catch (Exception e) {
                log.error("Error loading class report", e);
                Platform.runLater(() -> {
                    statusLabel.setText("Error loading report: " + e.getMessage());
                    loadingIndicator.setVisible(false);
                    loadReportButton.setDisable(false);
                });
            }
        });
    }

    private void displayClassReport(Map<String, Object> report) {
        loadingIndicator.setVisible(false);
        loadReportButton.setDisable(false);

        if (report.isEmpty()) {
            statusLabel.setText("No data available for the selected period");
            return;
        }

        // Update summary labels
        totalStudentsLabel.setText(String.valueOf(report.getOrDefault("totalStudents", 0)));
        activeStudentsLabel.setText(String.valueOf(report.getOrDefault("activeStudents", 0)));

        int totalMinutes = ((Number) report.getOrDefault("totalPlayTimeMinutes", 0)).intValue();
        totalPlayTimeLabel.setText(formatPlayTime(totalMinutes));

        totalSessionsLabel.setText(String.valueOf(report.getOrDefault("totalSessions", 0)));

        double avgTime = ((Number) report.getOrDefault("averagePlayTimePerStudent", 0.0)).doubleValue();
        avgPlayTimeLabel.setText(String.format("%.1f min", avgTime));

        double avgScore = ((Number) report.getOrDefault("averageScorePercentage", 0.0)).doubleValue();
        avgScoreLabel.setText(String.format("%.1f%%", avgScore));

        // Populate student table
        studentData.clear();
        List<Map<String, Object>> studentReports = (List<Map<String, Object>>) report.get("studentReports");
        if (studentReports != null) {
            for (Map<String, Object> sr : studentReports) {
                studentData.add(new StudentPlayTimeRow(sr));
            }
        }

        // Populate game table
        gameData.clear();
        List<Map<String, Object>> gameUsage = (List<Map<String, Object>>) report.get("gameUsage");
        if (gameUsage != null) {
            for (Map<String, Object> gu : gameUsage) {
                gameData.add(new GameUsageRow(gu));
            }
        }

        statusLabel.setText("Report loaded successfully");
    }

    private void loadStudentDetail(String studentId) {
        studentDetailPanel.setVisible(true);
        parentSummaryText.setText("Loading...");

        LocalDate startDate = startDatePicker.getValue();
        LocalDate endDate = endDatePicker.getValue();

        CompletableFuture.runAsync(() -> {
            try {
                String summary;
                if (startDate != null && endDate != null) {
                    summary = edGamesApiClient.getParentSummary(
                            studentId,
                            startDate.format(DateTimeFormatter.ISO_DATE),
                            endDate.format(DateTimeFormatter.ISO_DATE)
                    );
                } else {
                    summary = edGamesApiClient.getParentSummary(studentId);
                }

                Platform.runLater(() -> {
                    if (summary != null) {
                        parentSummaryText.setText(summary);
                        StudentPlayTimeRow selected = studentTable.getSelectionModel().getSelectedItem();
                        if (selected != null) {
                            selectedStudentLabel.setText("Report for: " + selected.getStudentName());
                        }
                    } else {
                        parentSummaryText.setText("Unable to load summary");
                    }
                });
            } catch (Exception e) {
                log.error("Error loading student detail", e);
                Platform.runLater(() -> parentSummaryText.setText("Error: " + e.getMessage()));
            }
        });
    }

    @FXML
    public void handleCopyToClipboard() {
        String text = parentSummaryText.getText();
        if (text != null && !text.isEmpty()) {
            javafx.scene.input.Clipboard clipboard = javafx.scene.input.Clipboard.getSystemClipboard();
            javafx.scene.input.ClipboardContent content = new javafx.scene.input.ClipboardContent();
            content.putString(text);
            clipboard.setContent(content);
            statusLabel.setText("Summary copied to clipboard!");
        }
    }

    @FXML
    public void handleEmailParent() {
        StudentPlayTimeRow selected = studentTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showAlert("Please select a student first");
            return;
        }

        // Get student email from repository
        Student student = studentRepository.findByStudentId(selected.getStudentId()).orElse(null);
        if (student == null || student.getParentEmail() == null) {
            showAlert("No parent email found for this student");
            return;
        }

        // Open default email client
        String subject = "Heronix Educational Games - Activity Report";
        String body = parentSummaryText.getText();

        try {
            String mailtoUrl = String.format("mailto:%s?subject=%s&body=%s",
                    student.getParentEmail(),
                    java.net.URLEncoder.encode(subject, "UTF-8"),
                    java.net.URLEncoder.encode(body, "UTF-8"));

            java.awt.Desktop.getDesktop().mail(new java.net.URI(mailtoUrl));
            statusLabel.setText("Opening email client...");
        } catch (Exception e) {
            log.error("Error opening email client", e);
            showAlert("Could not open email client. Summary has been copied to clipboard.");
            handleCopyToClipboard();
        }
    }

    @FXML
    public void handleExportCsv() {
        LocalDate startDate = startDatePicker.getValue();
        LocalDate endDate = endDatePicker.getValue();

        if (startDate == null || endDate == null) {
            showAlert("Please select a date range and load the report first");
            return;
        }

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Save Encrypted Report");
        fileChooser.setInitialFileName("play-time-report-" + startDate + "-to-" + endDate + ".heronix");
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Encrypted Files", "*.heronix")
        );

        File file = fileChooser.showSaveDialog(studentTable.getScene().getWindow());
        if (file == null) {
            return;
        }

        loadingIndicator.setVisible(true);
        statusLabel.setText("Exporting encrypted report...");

        CompletableFuture.runAsync(() -> {
            try {
                String csv = edGamesApiClient.exportClassReportCSV(
                        startDate.format(DateTimeFormatter.ISO_DATE),
                        endDate.format(DateTimeFormatter.ISO_DATE)
                );

                if (csv != null) {
                    String originalName = file.getName().replace(".heronix", ".csv");
                    byte[] encrypted = com.heronix.teacher.security.HeronixEncryptionService.getInstance()
                            .encryptFile(csv.getBytes(java.nio.charset.StandardCharsets.UTF_8), originalName);
                    java.nio.file.Files.write(file.toPath(), encrypted);
                    Platform.runLater(() -> {
                        statusLabel.setText("Exported successfully (encrypted): " + file.getName());
                        loadingIndicator.setVisible(false);
                    });
                } else {
                    Platform.runLater(() -> {
                        statusLabel.setText("Failed to export");
                        loadingIndicator.setVisible(false);
                    });
                }
            } catch (Exception e) {
                log.error("Error exporting report", e);
                Platform.runLater(() -> {
                    statusLabel.setText("Error exporting: " + e.getMessage());
                    loadingIndicator.setVisible(false);
                });
            }
        });
    }

    private String formatPlayTime(int minutes) {
        if (minutes < 60) {
            return minutes + " min";
        }
        int hours = minutes / 60;
        int mins = minutes % 60;
        return String.format("%dh %dm", hours, mins);
    }

    private void showAlert(String message) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle("Warning");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    /**
     * Row class for student table
     */
    public static class StudentPlayTimeRow {
        private final String studentId;
        private final String studentName;
        private final String grade;
        private final String playTime;
        private final String sessions;
        private final String avgSession;
        private final String avgScore;
        private final String gamesPlayed;

        public StudentPlayTimeRow(Map<String, Object> data) {
            this.studentId = (String) data.getOrDefault("studentId", "");
            this.studentName = (String) data.getOrDefault("studentName", "Unknown");
            this.grade = (String) data.getOrDefault("gradeLevel", "");

            int minutes = ((Number) data.getOrDefault("totalPlayTimeMinutes", 0)).intValue();
            this.playTime = formatTime(minutes);

            this.sessions = String.valueOf(data.getOrDefault("totalSessions", 0));

            double avgMin = ((Number) data.getOrDefault("averageSessionMinutes", 0.0)).doubleValue();
            this.avgSession = String.format("%.1f min", avgMin);

            double score = ((Number) data.getOrDefault("averageScore", 0.0)).doubleValue();
            this.avgScore = String.format("%.0f%%", score);

            this.gamesPlayed = String.valueOf(data.getOrDefault("totalGamesPlayed", 0));
        }

        private static String formatTime(int minutes) {
            if (minutes < 60) return minutes + " min";
            return String.format("%dh %dm", minutes / 60, minutes % 60);
        }

        public String getStudentId() { return studentId; }
        public String getStudentName() { return studentName; }
        public String getGrade() { return grade; }
        public String getPlayTime() { return playTime; }
        public String getSessions() { return sessions; }
        public String getAvgSession() { return avgSession; }
        public String getAvgScore() { return avgScore; }
        public String getGamesPlayed() { return gamesPlayed; }
    }

    /**
     * Row class for game usage table
     */
    public static class GameUsageRow {
        private final String gameName;
        private final String subject;
        private final String playTime;
        private final String uniquePlayers;
        private final String sessions;
        private final String avgScore;

        public GameUsageRow(Map<String, Object> data) {
            this.gameName = (String) data.getOrDefault("gameName", "Unknown Game");
            this.subject = (String) data.getOrDefault("subject", "");

            int minutes = ((Number) data.getOrDefault("totalPlayTimeMinutes", 0)).intValue();
            this.playTime = formatTime(minutes);

            this.uniquePlayers = String.valueOf(data.getOrDefault("uniquePlayers", 0));
            this.sessions = String.valueOf(data.getOrDefault("totalSessions", 0));

            double score = ((Number) data.getOrDefault("averageScore", 0.0)).doubleValue();
            this.avgScore = String.format("%.0f%%", score);
        }

        private static String formatTime(int minutes) {
            if (minutes < 60) return minutes + " min";
            return String.format("%dh %dm", minutes / 60, minutes % 60);
        }

        public String getGameName() { return gameName; }
        public String getSubject() { return subject; }
        public String getPlayTime() { return playTime; }
        public String getUniquePlayers() { return uniquePlayers; }
        public String getSessions() { return sessions; }
        public String getAvgScore() { return avgScore; }
    }
}
