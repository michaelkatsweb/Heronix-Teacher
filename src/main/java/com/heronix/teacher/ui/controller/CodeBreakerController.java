package com.heronix.teacher.ui.controller;

import com.heronix.teacher.service.EdGamesApiClient;
import javafx.application.Platform;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URL;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Controller for Code Breaker multiplayer game session management.
 * Allows teachers to create sessions, monitor real-time metrics, and manage question sets.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CodeBreakerController implements Initializable {

    private final EdGamesApiClient edGamesApiClient;
    private final org.springframework.context.ApplicationContext applicationContext;

    // Session Setup Panel
    @FXML private ComboBox<QuestionSetItem> questionSetCombo;
    @FXML private Spinner<Integer> timeLimitSpinner;
    @FXML private Spinner<Integer> targetCreditsSpinner;
    @FXML private Button createSessionBtn;
    @FXML private VBox sessionInfoBox;
    @FXML private Label sessionCodeLabel;
    @FXML private Label playerCountLabel;
    @FXML private Label sessionStatusLabel;
    @FXML private Button startGameBtn;
    @FXML private Button endGameBtn;

    // Metrics Dashboard
    @FXML private Label totalPlayersMetric;
    @FXML private Label questionsAnsweredMetric;
    @FXML private Label avgAccuracyMetric;
    @FXML private Label totalHacksMetric;

    // Leaderboard Table
    @FXML private TableView<PlayerMetrics> leaderboardTable;
    @FXML private TableColumn<PlayerMetrics, Integer> rankColumn;
    @FXML private TableColumn<PlayerMetrics, String> studentColumn;
    @FXML private TableColumn<PlayerMetrics, Integer> creditsColumn;
    @FXML private TableColumn<PlayerMetrics, Integer> correctColumn;
    @FXML private TableColumn<PlayerMetrics, Integer> wrongColumn;
    @FXML private TableColumn<PlayerMetrics, String> accuracyColumn;
    @FXML private TableColumn<PlayerMetrics, Integer> hacksColumn;

    // Activity Feed
    @FXML private ListView<String> activityFeed;

    // Student Detail Panel
    @FXML private VBox studentDetailPanel;
    @FXML private Label selectedStudentName;
    @FXML private Label detailCredits;
    @FXML private Label detailCorrect;
    @FXML private Label detailWrong;
    @FXML private Label detailAccuracy;
    @FXML private Label detailHackAttempts;
    @FXML private Label detailSuccessfulHacks;
    @FXML private Label detailTimesHacked;

    // Question Set Management
    @FXML private TextField newSetNameField;
    @FXML private TextField newSetSubjectField;
    @FXML private ComboBox<String> newSetGradeCombo;
    @FXML private Button createSetBtn;
    @FXML private Button editQuestionsBtn;
    @FXML private Button browsePresetsBtn;
    @FXML private TableView<QuestionSetItem> questionSetsTable;

    // Session History
    @FXML private TableView<SessionHistoryItem> sessionHistoryTable;

    private final ObservableList<PlayerMetrics> playerMetrics = FXCollections.observableArrayList();
    private final ObservableList<String> activities = FXCollections.observableArrayList();
    private final ObservableList<QuestionSetItem> questionSets = FXCollections.observableArrayList();

    private String currentSessionCode;
    private ScheduledExecutorService refreshScheduler;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        setupLeaderboardTable();
        setupQuestionSetCombo();
        setupSpinners();
        setupGradeCombo();
        loadQuestionSets();

        if (activityFeed != null) {
            activityFeed.setItems(activities);
        }

        if (leaderboardTable != null) {
            leaderboardTable.setItems(playerMetrics);
            leaderboardTable.getSelectionModel().selectedItemProperty().addListener(
                (obs, oldVal, newVal) -> showPlayerDetails(newVal));
        }

        // Initially hide session info
        if (sessionInfoBox != null) {
            sessionInfoBox.setVisible(false);
        }
    }

    private void setupGradeCombo() {
        if (newSetGradeCombo != null) {
            newSetGradeCombo.setItems(FXCollections.observableArrayList(
                "K-2", "3-5", "6-8", "9-12"
            ));
        }
    }

    private void setupLeaderboardTable() {
        if (rankColumn != null) {
            rankColumn.setCellValueFactory(cellData ->
                new SimpleIntegerProperty(cellData.getValue().getRank()).asObject());
        }
        if (studentColumn != null) {
            studentColumn.setCellValueFactory(cellData ->
                new SimpleStringProperty(cellData.getValue().getStudentName()));
        }
        if (creditsColumn != null) {
            creditsColumn.setCellValueFactory(cellData ->
                new SimpleIntegerProperty(cellData.getValue().getCredits()).asObject());
        }
        if (correctColumn != null) {
            correctColumn.setCellValueFactory(cellData ->
                new SimpleIntegerProperty(cellData.getValue().getCorrectAnswers()).asObject());
        }
        if (wrongColumn != null) {
            wrongColumn.setCellValueFactory(cellData ->
                new SimpleIntegerProperty(cellData.getValue().getIncorrectAnswers()).asObject());
        }
        if (accuracyColumn != null) {
            accuracyColumn.setCellValueFactory(cellData ->
                new SimpleStringProperty(String.format("%.1f%%", cellData.getValue().getAccuracy())));
        }
        if (hacksColumn != null) {
            hacksColumn.setCellValueFactory(cellData ->
                new SimpleIntegerProperty(cellData.getValue().getSuccessfulHacks()).asObject());
        }
    }

    private void setupQuestionSetCombo() {
        if (questionSetCombo != null) {
            questionSetCombo.setItems(questionSets);
            questionSetCombo.setCellFactory(lv -> new ListCell<>() {
                @Override
                protected void updateItem(QuestionSetItem item, boolean empty) {
                    super.updateItem(item, empty);
                    setText(empty || item == null ? null : item.getName() + " (" + item.getQuestionCount() + " questions)");
                }
            });
            questionSetCombo.setButtonCell(new ListCell<>() {
                @Override
                protected void updateItem(QuestionSetItem item, boolean empty) {
                    super.updateItem(item, empty);
                    setText(empty || item == null ? "Select Question Set" : item.getName());
                }
            });
        }
    }

    private void setupSpinners() {
        if (timeLimitSpinner != null) {
            timeLimitSpinner.setValueFactory(
                new SpinnerValueFactory.IntegerSpinnerValueFactory(5, 30, 10));
        }
        if (targetCreditsSpinner != null) {
            targetCreditsSpinner.setValueFactory(
                new SpinnerValueFactory.IntegerSpinnerValueFactory(500, 5000, 1000, 100));
        }
    }

    private void loadQuestionSets() {
        new Thread(() -> {
            List<Map<String, Object>> sets = edGamesApiClient.getQuestionSets();
            List<QuestionSetItem> items = sets.stream()
                .map(s -> new QuestionSetItem(
                    (String) s.get("setId"),
                    (String) s.get("name"),
                    (String) s.get("subject"),
                    (String) s.get("gradeLevel"),
                    s.get("questionCount") != null ? ((Number) s.get("questionCount")).intValue() : 0
                ))
                .toList();

            Platform.runLater(() -> {
                questionSets.clear();
                questionSets.addAll(items);
            });
        }).start();
    }

    @FXML
    public void handleCreateSession() {
        QuestionSetItem selectedSet = questionSetCombo.getValue();
        if (selectedSet == null) {
            showAlert("Error", "Please select a question set");
            return;
        }

        int timeLimit = timeLimitSpinner.getValue();
        int targetCredits = targetCreditsSpinner.getValue();

        createSessionBtn.setDisable(true);

        new Thread(() -> {
            Map<String, Object> result = edGamesApiClient.createGameSession(
                selectedSet.getSetId(),
                "CODE_BREAKER",
                timeLimit,
                targetCredits
            );

            Platform.runLater(() -> {
                if (result.containsKey("error")) {
                    showAlert("Error", "Failed to create session: " + result.get("error"));
                    createSessionBtn.setDisable(false);
                } else {
                    currentSessionCode = (String) result.get("sessionCode");
                    if (currentSessionCode == null) {
                        currentSessionCode = (String) result.get("sessionId");
                    }

                    sessionCodeLabel.setText(currentSessionCode);
                    sessionStatusLabel.setText("WAITING FOR PLAYERS");
                    playerCountLabel.setText("0 players");
                    sessionInfoBox.setVisible(true);
                    startGameBtn.setDisable(false);
                    endGameBtn.setDisable(true);

                    addActivity("Session created: " + currentSessionCode);

                    // Start polling for updates
                    startSessionPolling();
                }
            });
        }).start();
    }

    @FXML
    public void handleStartGame() {
        if (currentSessionCode == null) return;

        startGameBtn.setDisable(true);
        addActivity("Starting game...");

        // In a real implementation, this would use WebSocket
        // For now, we'll just update the UI
        Platform.runLater(() -> {
            sessionStatusLabel.setText("ACTIVE");
            endGameBtn.setDisable(false);
            addActivity("Game started!");
        });
    }

    @FXML
    public void handleEndGame() {
        if (currentSessionCode == null) return;

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("End Game");
        confirm.setHeaderText("Are you sure you want to end the game?");
        confirm.setContentText("This will end the game for all players.");

        confirm.showAndWait().ifPresent(result -> {
            if (result == ButtonType.OK) {
                endGame();
            }
        });
    }

    private void endGame() {
        stopSessionPolling();

        Platform.runLater(() -> {
            sessionStatusLabel.setText("ENDED");
            startGameBtn.setDisable(true);
            endGameBtn.setDisable(true);
            createSessionBtn.setDisable(false);
            addActivity("Game ended!");
        });

        currentSessionCode = null;
    }

    @FXML
    public void handleCreateQuestionSet() {
        String name = newSetNameField.getText().trim();
        String subject = newSetSubjectField.getText().trim();
        String gradeLevel = newSetGradeCombo.getValue();

        if (name.isEmpty()) {
            showAlert("Error", "Please enter a name for the question set");
            return;
        }

        new Thread(() -> {
            Map<String, Object> result = edGamesApiClient.createQuestionSet(name, null, subject, gradeLevel);

            Platform.runLater(() -> {
                if (result.containsKey("error")) {
                    showAlert("Error", "Failed to create question set: " + result.get("error"));
                } else {
                    showAlert("Success", "Question set created successfully!");
                    newSetNameField.clear();
                    newSetSubjectField.clear();
                    loadQuestionSets();
                }
            });
        }).start();
    }

    @FXML
    public void handleRefreshSets() {
        loadQuestionSets();
    }

    @FXML
    public void handleEditQuestions() {
        QuestionSetItem selectedSet = questionSetCombo.getValue();
        if (selectedSet == null) {
            showAlert("No Selection", "Please select a question set to edit.");
            return;
        }

        openQuestionEditor(selectedSet);
    }

    @FXML
    public void handleBrowsePresets() {
        // Show dialog with preset question sets
        Dialog<QuestionSetItem> dialog = new Dialog<>();
        dialog.setTitle("Preset Question Sets");
        dialog.setHeaderText("Browse and use preset question sets created by Heronix");

        // Create content
        VBox content = new VBox(10);
        content.setStyle("-fx-padding: 20;");

        ListView<QuestionSetItem> presetList = new ListView<>();
        presetList.setPrefHeight(300);
        presetList.setPrefWidth(400);

        presetList.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(QuestionSetItem item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    VBox cell = new VBox(3);
                    Label nameLabel = new Label(item.getName());
                    nameLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 14px;");
                    Label infoLabel = new Label(item.getSubject() + " | " + item.getGradeLevel() + " | " + item.getQuestionCount() + " questions");
                    infoLabel.setStyle("-fx-text-fill: #666; -fx-font-size: 12px;");
                    cell.getChildren().addAll(nameLabel, infoLabel);
                    setGraphic(cell);
                }
            }
        });

        // Load presets
        new Thread(() -> {
            List<Map<String, Object>> presets = edGamesApiClient.getPresetQuestionSets();
            List<QuestionSetItem> items = presets.stream()
                .map(s -> new QuestionSetItem(
                    (String) s.get("setId"),
                    (String) s.get("name"),
                    (String) s.get("subject"),
                    (String) s.get("gradeLevel"),
                    s.get("questionCount") != null ? ((Number) s.get("questionCount")).intValue() : 0
                ))
                .toList();

            Platform.runLater(() -> presetList.getItems().addAll(items));
        }).start();

        content.getChildren().add(new Label("Select a preset to use or copy:"));
        content.getChildren().add(presetList);

        dialog.getDialogPane().setContent(content);

        ButtonType useButton = new ButtonType("Use", ButtonBar.ButtonData.OK_DONE);
        ButtonType copyButton = new ButtonType("Copy & Edit", ButtonBar.ButtonData.OTHER);
        ButtonType cancelButton = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);

        dialog.getDialogPane().getButtonTypes().addAll(useButton, copyButton, cancelButton);

        dialog.setResultConverter(buttonType -> {
            if (buttonType == useButton || buttonType == copyButton) {
                return presetList.getSelectionModel().getSelectedItem();
            }
            return null;
        });

        dialog.showAndWait().ifPresent(selected -> {
            if (selected != null) {
                // Check which button was pressed
                ButtonType pressedButton = dialog.getResult() != null ? useButton : null;

                // Add to combo and select
                if (!questionSets.contains(selected)) {
                    questionSets.add(selected);
                }
                questionSetCombo.setValue(selected);

                // If copy was pressed, clone and open editor
                // For simplicity, just refresh the sets
                loadQuestionSets();
            }
        });
    }

    private void openQuestionEditor(QuestionSetItem setItem) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/QuestionEditor.fxml"));
            loader.setControllerFactory(applicationContext::getBean);
            Parent editorRoot = loader.load();

            QuestionEditorController editorController = loader.getController();

            // Create data object
            QuestionEditorController.QuestionSetData setData = new QuestionEditorController.QuestionSetData();
            setData.setSetId(setItem.getSetId());
            setData.setName(setItem.getName());
            setData.setSubject(setItem.getSubject());
            setData.setGradeLevel(setItem.getGradeLevel());

            Stage editorStage = new Stage();
            editorStage.initModality(Modality.APPLICATION_MODAL);
            editorStage.setTitle("Question Editor - " + setItem.getName());
            editorStage.setScene(new Scene(editorRoot, 900, 700));

            editorController.initializeWithSet(setData, () -> {
                editorStage.close();
                loadQuestionSets(); // Refresh after editing
            });

            editorStage.showAndWait();

        } catch (Exception e) {
            log.error("Failed to open question editor", e);
            showAlert("Error", "Failed to open question editor: " + e.getMessage());
        }
    }

    private void startSessionPolling() {
        stopSessionPolling();

        refreshScheduler = Executors.newSingleThreadScheduledExecutor();
        refreshScheduler.scheduleAtFixedRate(() -> {
            if (currentSessionCode != null) {
                refreshSessionData();
            }
        }, 0, 3, TimeUnit.SECONDS);
    }

    private void stopSessionPolling() {
        if (refreshScheduler != null && !refreshScheduler.isShutdown()) {
            refreshScheduler.shutdown();
        }
    }

    private void refreshSessionData() {
        try {
            // Get session info
            Map<String, Object> session = edGamesApiClient.getGameSession(currentSessionCode);
            if (session.isEmpty()) return;

            // Get leaderboard
            List<Map<String, Object>> leaderboard = edGamesApiClient.getSessionLeaderboard(currentSessionCode);

            Platform.runLater(() -> {
                // Update player count
                int playerCount = leaderboard.size();
                playerCountLabel.setText(playerCount + " player" + (playerCount != 1 ? "s" : ""));
                totalPlayersMetric.setText(String.valueOf(playerCount));

                // Update leaderboard
                playerMetrics.clear();
                int rank = 1;
                int totalQuestions = 0;
                int totalCorrect = 0;
                int totalHacks = 0;

                for (Map<String, Object> player : leaderboard) {
                    PlayerMetrics pm = new PlayerMetrics();
                    pm.setRank(rank++);
                    pm.setPlayerId((String) player.get("playerId"));
                    pm.setStudentName((String) player.get("studentName"));
                    pm.setCredits(getInt(player, "credits"));
                    pm.setCorrectAnswers(getInt(player, "correctAnswers"));
                    pm.setIncorrectAnswers(getInt(player, "incorrectAnswers"));
                    pm.setHackAttempts(getInt(player, "hackAttempts"));
                    pm.setSuccessfulHacks(getInt(player, "successfulHacks"));
                    pm.setTimesHacked(getInt(player, "timesHacked"));

                    int total = pm.getCorrectAnswers() + pm.getIncorrectAnswers();
                    pm.setAccuracy(total > 0 ? (double) pm.getCorrectAnswers() / total * 100 : 0);

                    playerMetrics.add(pm);

                    totalQuestions += total;
                    totalCorrect += pm.getCorrectAnswers();
                    totalHacks += pm.getSuccessfulHacks();
                }

                // Update summary metrics
                questionsAnsweredMetric.setText(String.valueOf(totalQuestions));
                totalHacksMetric.setText(String.valueOf(totalHacks));

                double avgAcc = totalQuestions > 0 ? (double) totalCorrect / totalQuestions * 100 : 0;
                avgAccuracyMetric.setText(String.format("%.1f%%", avgAcc));
            });

        } catch (Exception e) {
            log.error("Error refreshing session data", e);
        }
    }

    private int getInt(Map<String, Object> map, String key) {
        Object val = map.get(key);
        if (val instanceof Number) {
            return ((Number) val).intValue();
        }
        return 0;
    }

    private void showPlayerDetails(PlayerMetrics player) {
        if (player == null) {
            studentDetailPanel.setVisible(false);
            return;
        }

        studentDetailPanel.setVisible(true);
        selectedStudentName.setText(player.getStudentName());
        detailCredits.setText(String.valueOf(player.getCredits()));
        detailCorrect.setText(String.valueOf(player.getCorrectAnswers()));
        detailWrong.setText(String.valueOf(player.getIncorrectAnswers()));
        detailAccuracy.setText(String.format("%.1f%%", player.getAccuracy()));
        detailHackAttempts.setText(String.valueOf(player.getHackAttempts()));
        detailSuccessfulHacks.setText(String.valueOf(player.getSuccessfulHacks()));
        detailTimesHacked.setText(String.valueOf(player.getTimesHacked()));
    }

    private void addActivity(String message) {
        String timestamp = java.time.LocalTime.now().format(
            java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss"));
        activities.add(0, "[" + timestamp + "] " + message);

        // Keep only last 50 activities
        while (activities.size() > 50) {
            activities.remove(activities.size() - 1);
        }
    }

    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    public void cleanup() {
        stopSessionPolling();
    }

    // Inner classes for table data

    public static class PlayerMetrics {
        private int rank;
        private String playerId;
        private String studentName;
        private int credits;
        private int correctAnswers;
        private int incorrectAnswers;
        private double accuracy;
        private int hackAttempts;
        private int successfulHacks;
        private int timesHacked;

        public int getRank() { return rank; }
        public void setRank(int rank) { this.rank = rank; }
        public String getPlayerId() { return playerId; }
        public void setPlayerId(String playerId) { this.playerId = playerId; }
        public String getStudentName() { return studentName; }
        public void setStudentName(String studentName) { this.studentName = studentName; }
        public int getCredits() { return credits; }
        public void setCredits(int credits) { this.credits = credits; }
        public int getCorrectAnswers() { return correctAnswers; }
        public void setCorrectAnswers(int correctAnswers) { this.correctAnswers = correctAnswers; }
        public int getIncorrectAnswers() { return incorrectAnswers; }
        public void setIncorrectAnswers(int incorrectAnswers) { this.incorrectAnswers = incorrectAnswers; }
        public double getAccuracy() { return accuracy; }
        public void setAccuracy(double accuracy) { this.accuracy = accuracy; }
        public int getHackAttempts() { return hackAttempts; }
        public void setHackAttempts(int hackAttempts) { this.hackAttempts = hackAttempts; }
        public int getSuccessfulHacks() { return successfulHacks; }
        public void setSuccessfulHacks(int successfulHacks) { this.successfulHacks = successfulHacks; }
        public int getTimesHacked() { return timesHacked; }
        public void setTimesHacked(int timesHacked) { this.timesHacked = timesHacked; }
    }

    public static class QuestionSetItem {
        private final String setId;
        private final String name;
        private final String subject;
        private final String gradeLevel;
        private final int questionCount;

        public QuestionSetItem(String setId, String name, String subject, String gradeLevel, int questionCount) {
            this.setId = setId;
            this.name = name;
            this.subject = subject;
            this.gradeLevel = gradeLevel;
            this.questionCount = questionCount;
        }

        public String getSetId() { return setId; }
        public String getName() { return name; }
        public String getSubject() { return subject; }
        public String getGradeLevel() { return gradeLevel; }
        public int getQuestionCount() { return questionCount; }

        @Override
        public String toString() { return name; }
    }

    public static class SessionHistoryItem {
        private String sessionCode;
        private String date;
        private int playerCount;
        private String winner;
        private int duration;

        public String getSessionCode() { return sessionCode; }
        public void setSessionCode(String sessionCode) { this.sessionCode = sessionCode; }
        public String getDate() { return date; }
        public void setDate(String date) { this.date = date; }
        public int getPlayerCount() { return playerCount; }
        public void setPlayerCount(int playerCount) { this.playerCount = playerCount; }
        public String getWinner() { return winner; }
        public void setWinner(String winner) { this.winner = winner; }
        public int getDuration() { return duration; }
        public void setDuration(int duration) { this.duration = duration; }
    }
}
