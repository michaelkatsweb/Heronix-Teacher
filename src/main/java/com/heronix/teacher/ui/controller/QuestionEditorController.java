package com.heronix.teacher.ui.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.net.URI;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Controller for the Question Editor view.
 * Allows teachers to add, edit, and remove questions from a question set.
 */
public class QuestionEditorController implements Initializable {

    @FXML private Button backBtn;
    @FXML private Button saveBtn;
    @FXML private Label setNameLabel;
    @FXML private Label setInfoLabel;
    @FXML private Label questionCountLabel;
    @FXML private ListView<QuestionItem> questionListView;
    @FXML private Button addQuestionBtn;
    @FXML private Button deleteQuestionBtn;
    @FXML private Button duplicateQuestionBtn;

    // Editor fields
    @FXML private TextArea questionTextArea;
    @FXML private TextField correctAnswerField;
    @FXML private TextField wrongAnswer1Field;
    @FXML private TextField wrongAnswer2Field;
    @FXML private TextField wrongAnswer3Field;
    @FXML private ComboBox<String> difficultyCombo;
    @FXML private Spinner<Integer> orderSpinner;
    @FXML private TextArea explanationArea;
    @FXML private TextField imageUrlField;

    // Preview
    @FXML private VBox previewBox;
    @FXML private Label previewQuestionLabel;
    @FXML private VBox previewAnswersBox;

    // Status
    @FXML private Label statusLabel;
    @FXML private Label lastSavedLabel;

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final String serverUrl = "http://localhost:8081";

    private final ObservableList<QuestionItem> questions = FXCollections.observableArrayList();
    private QuestionSetData currentSet;
    private QuestionItem selectedQuestion;
    private Runnable onBackCallback;
    private boolean hasUnsavedChanges = false;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        setupDifficultyCombo();
        setupOrderSpinner();
        setupListView();
        setupFieldListeners();
        clearEditor();
    }

    /**
     * Initialize with a question set to edit.
     */
    public void initializeWithSet(QuestionSetData set, Runnable onBack) {
        this.currentSet = set;
        this.onBackCallback = onBack;

        setNameLabel.setText(set.getName());
        setInfoLabel.setText(set.getSubject() + " - " + set.getGradeLevel());

        loadQuestions();
    }

    private void setupDifficultyCombo() {
        difficultyCombo.setItems(FXCollections.observableArrayList(
            "1 - Easy",
            "2 - Medium",
            "3 - Hard",
            "4 - Very Hard",
            "5 - Expert"
        ));
        difficultyCombo.getSelectionModel().selectFirst();
    }

    private void setupOrderSpinner() {
        SpinnerValueFactory<Integer> factory = new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 999, 1);
        orderSpinner.setValueFactory(factory);
    }

    private void setupListView() {
        questionListView.setItems(questions);
        questionListView.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(QuestionItem item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    String text = item.getOrderIndex() + ". " + truncate(item.getQuestionText(), 35);
                    setText(text);

                    // Style based on validation
                    if (item.isValid()) {
                        setStyle("-fx-text-fill: #333;");
                    } else {
                        setStyle("-fx-text-fill: #f44336;");
                    }
                }
            }
        });

        questionListView.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (oldVal != null && hasUnsavedChanges) {
                saveCurrentQuestionToMemory();
            }
            selectedQuestion = newVal;
            if (newVal != null) {
                loadQuestionToEditor(newVal);
            } else {
                clearEditor();
            }
        });
    }

    private void setupFieldListeners() {
        // Update preview on field changes
        questionTextArea.textProperty().addListener((obs, old, newVal) -> {
            updatePreview();
            markAsChanged();
        });
        correctAnswerField.textProperty().addListener((obs, old, newVal) -> {
            updatePreview();
            markAsChanged();
        });
        wrongAnswer1Field.textProperty().addListener((obs, old, newVal) -> {
            updatePreview();
            markAsChanged();
        });
        wrongAnswer2Field.textProperty().addListener((obs, old, newVal) -> {
            updatePreview();
            markAsChanged();
        });
        wrongAnswer3Field.textProperty().addListener((obs, old, newVal) -> {
            updatePreview();
            markAsChanged();
        });
    }

    private void markAsChanged() {
        hasUnsavedChanges = true;
        statusLabel.setText("Unsaved changes");
        statusLabel.setStyle("-fx-text-fill: #ff9800;");
    }

    private void loadQuestions() {
        statusLabel.setText("Loading questions...");

        new Thread(() -> {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(serverUrl + "/api/game/question-sets/" + currentSet.getSetId()))
                    .GET()
                    .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200) {
                    Map<String, Object> data = objectMapper.readValue(response.body(), new TypeReference<>() {});
                    List<Map<String, Object>> questionList = (List<Map<String, Object>>) data.get("questions");

                    List<QuestionItem> items = new ArrayList<>();
                    if (questionList != null) {
                        for (Map<String, Object> q : questionList) {
                            QuestionItem item = new QuestionItem();
                            item.setQuestionId((String) q.get("questionId"));
                            item.setQuestionText((String) q.get("questionText"));
                            item.setCorrectAnswer((String) q.get("correctAnswer"));
                            item.setWrongAnswer1((String) q.get("wrongAnswer1"));
                            item.setWrongAnswer2((String) q.get("wrongAnswer2"));
                            item.setWrongAnswer3((String) q.get("wrongAnswer3"));
                            item.setDifficulty(q.get("difficulty") != null ? ((Number) q.get("difficulty")).intValue() : 1);
                            item.setOrderIndex(q.get("orderIndex") != null ? ((Number) q.get("orderIndex")).intValue() : items.size() + 1);
                            item.setExplanation((String) q.get("explanation"));
                            item.setImageUrl((String) q.get("imageUrl"));
                            item.setNew(false);
                            items.add(item);
                        }
                    }

                    // Sort by order index
                    items.sort(Comparator.comparingInt(QuestionItem::getOrderIndex));

                    Platform.runLater(() -> {
                        questions.clear();
                        questions.addAll(items);
                        updateQuestionCount();
                        statusLabel.setText("Loaded " + items.size() + " questions");
                        statusLabel.setStyle("-fx-text-fill: #4caf50;");

                        if (!items.isEmpty()) {
                            questionListView.getSelectionModel().selectFirst();
                        }
                    });
                } else {
                    Platform.runLater(() -> {
                        statusLabel.setText("Failed to load questions");
                        statusLabel.setStyle("-fx-text-fill: #f44336;");
                    });
                }
            } catch (Exception e) {
                e.printStackTrace();
                Platform.runLater(() -> {
                    statusLabel.setText("Error: " + e.getMessage());
                    statusLabel.setStyle("-fx-text-fill: #f44336;");
                });
            }
        }).start();
    }

    private void loadQuestionToEditor(QuestionItem item) {
        questionTextArea.setText(item.getQuestionText());
        correctAnswerField.setText(item.getCorrectAnswer());
        wrongAnswer1Field.setText(item.getWrongAnswer1());
        wrongAnswer2Field.setText(item.getWrongAnswer2());
        wrongAnswer3Field.setText(item.getWrongAnswer3());

        int diff = item.getDifficulty();
        if (diff >= 1 && diff <= 5) {
            difficultyCombo.getSelectionModel().select(diff - 1);
        }

        orderSpinner.getValueFactory().setValue(item.getOrderIndex());
        explanationArea.setText(item.getExplanation());
        imageUrlField.setText(item.getImageUrl());

        hasUnsavedChanges = false;
        updatePreview();
    }

    private void saveCurrentQuestionToMemory() {
        if (selectedQuestion == null) return;

        selectedQuestion.setQuestionText(questionTextArea.getText());
        selectedQuestion.setCorrectAnswer(correctAnswerField.getText());
        selectedQuestion.setWrongAnswer1(wrongAnswer1Field.getText());
        selectedQuestion.setWrongAnswer2(wrongAnswer2Field.getText());
        selectedQuestion.setWrongAnswer3(wrongAnswer3Field.getText());

        int selectedDiff = difficultyCombo.getSelectionModel().getSelectedIndex() + 1;
        selectedQuestion.setDifficulty(selectedDiff);

        selectedQuestion.setOrderIndex(orderSpinner.getValue());
        selectedQuestion.setExplanation(explanationArea.getText());
        selectedQuestion.setImageUrl(imageUrlField.getText());
        selectedQuestion.setModified(true);

        questionListView.refresh();
    }

    private void clearEditor() {
        questionTextArea.clear();
        correctAnswerField.clear();
        wrongAnswer1Field.clear();
        wrongAnswer2Field.clear();
        wrongAnswer3Field.clear();
        difficultyCombo.getSelectionModel().selectFirst();
        orderSpinner.getValueFactory().setValue(1);
        explanationArea.clear();
        imageUrlField.clear();
        previewQuestionLabel.setText("");
        previewAnswersBox.getChildren().clear();
        hasUnsavedChanges = false;
    }

    private void updatePreview() {
        String question = questionTextArea.getText();
        previewQuestionLabel.setText(question != null && !question.isBlank() ? question : "Enter a question...");

        previewAnswersBox.getChildren().clear();

        List<String> answers = new ArrayList<>();
        if (correctAnswerField.getText() != null && !correctAnswerField.getText().isBlank()) {
            answers.add(correctAnswerField.getText());
        }
        if (wrongAnswer1Field.getText() != null && !wrongAnswer1Field.getText().isBlank()) {
            answers.add(wrongAnswer1Field.getText());
        }
        if (wrongAnswer2Field.getText() != null && !wrongAnswer2Field.getText().isBlank()) {
            answers.add(wrongAnswer2Field.getText());
        }
        if (wrongAnswer3Field.getText() != null && !wrongAnswer3Field.getText().isBlank()) {
            answers.add(wrongAnswer3Field.getText());
        }

        Collections.shuffle(answers);

        char letter = 'A';
        for (String answer : answers) {
            Label answerLabel = new Label(letter + ") " + answer);
            answerLabel.getStyleClass().add("preview-answer");
            previewAnswersBox.getChildren().add(answerLabel);
            letter++;
        }
    }

    private void updateQuestionCount() {
        questionCountLabel.setText(String.valueOf(questions.size()));
    }

    @FXML
    public void handleAddQuestion() {
        QuestionItem newItem = new QuestionItem();
        newItem.setQuestionId(UUID.randomUUID().toString());
        newItem.setOrderIndex(questions.size() + 1);
        newItem.setDifficulty(1);
        newItem.setNew(true);
        newItem.setQuestionText("New Question");

        questions.add(newItem);
        updateQuestionCount();
        questionListView.getSelectionModel().select(newItem);
        questionListView.scrollTo(newItem);

        questionTextArea.requestFocus();
        questionTextArea.selectAll();
    }

    @FXML
    public void handleDeleteQuestion() {
        if (selectedQuestion == null) {
            showAlert("No Selection", "Please select a question to delete.");
            return;
        }

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Delete Question");
        confirm.setHeaderText("Delete this question?");
        confirm.setContentText("This action cannot be undone.");

        if (confirm.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK) {
            if (!selectedQuestion.isNew()) {
                // Delete from server
                deleteQuestionFromServer(selectedQuestion.getQuestionId());
            }
            questions.remove(selectedQuestion);
            updateQuestionCount();
            clearEditor();
        }
    }

    @FXML
    public void handleDuplicateQuestion() {
        if (selectedQuestion == null) {
            showAlert("No Selection", "Please select a question to duplicate.");
            return;
        }

        saveCurrentQuestionToMemory();

        QuestionItem duplicate = new QuestionItem();
        duplicate.setQuestionId(UUID.randomUUID().toString());
        duplicate.setQuestionText(selectedQuestion.getQuestionText() + " (Copy)");
        duplicate.setCorrectAnswer(selectedQuestion.getCorrectAnswer());
        duplicate.setWrongAnswer1(selectedQuestion.getWrongAnswer1());
        duplicate.setWrongAnswer2(selectedQuestion.getWrongAnswer2());
        duplicate.setWrongAnswer3(selectedQuestion.getWrongAnswer3());
        duplicate.setDifficulty(selectedQuestion.getDifficulty());
        duplicate.setOrderIndex(questions.size() + 1);
        duplicate.setExplanation(selectedQuestion.getExplanation());
        duplicate.setImageUrl(selectedQuestion.getImageUrl());
        duplicate.setNew(true);

        questions.add(duplicate);
        updateQuestionCount();
        questionListView.getSelectionModel().select(duplicate);
    }

    @FXML
    public void handleSave() {
        saveCurrentQuestionToMemory();

        statusLabel.setText("Saving...");
        statusLabel.setStyle("-fx-text-fill: #2196f3;");

        new Thread(() -> {
            try {
                int saved = 0;
                int errors = 0;

                for (QuestionItem item : questions) {
                    if (!item.isValid()) {
                        errors++;
                        continue;
                    }

                    if (item.isNew()) {
                        // Create new question
                        if (createQuestion(item)) {
                            item.setNew(false);
                            item.setModified(false);
                            saved++;
                        } else {
                            errors++;
                        }
                    } else if (item.isModified()) {
                        // Update existing question
                        if (updateQuestion(item)) {
                            item.setModified(false);
                            saved++;
                        } else {
                            errors++;
                        }
                    }
                }

                final int finalSaved = saved;
                final int finalErrors = errors;

                Platform.runLater(() -> {
                    if (finalErrors > 0) {
                        statusLabel.setText("Saved " + finalSaved + " questions, " + finalErrors + " errors");
                        statusLabel.setStyle("-fx-text-fill: #ff9800;");
                    } else {
                        statusLabel.setText("All changes saved");
                        statusLabel.setStyle("-fx-text-fill: #4caf50;");
                    }
                    lastSavedLabel.setText("Last saved: " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")));
                    hasUnsavedChanges = false;
                    questionListView.refresh();
                });

            } catch (Exception e) {
                e.printStackTrace();
                Platform.runLater(() -> {
                    statusLabel.setText("Save failed: " + e.getMessage());
                    statusLabel.setStyle("-fx-text-fill: #f44336;");
                });
            }
        }).start();
    }

    private boolean createQuestion(QuestionItem item) {
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("questionText", item.getQuestionText());
            body.put("correctAnswer", item.getCorrectAnswer());
            body.put("wrongAnswer1", item.getWrongAnswer1());
            body.put("wrongAnswer2", item.getWrongAnswer2());
            body.put("wrongAnswer3", item.getWrongAnswer3());
            body.put("difficulty", item.getDifficulty());
            body.put("orderIndex", item.getOrderIndex());
            body.put("explanation", item.getExplanation());
            body.put("imageUrl", item.getImageUrl());

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(serverUrl + "/api/game/question-sets/" + currentSet.getSetId() + "/questions"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return response.statusCode() == 200;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    private boolean updateQuestion(QuestionItem item) {
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("questionText", item.getQuestionText());
            body.put("correctAnswer", item.getCorrectAnswer());
            body.put("wrongAnswer1", item.getWrongAnswer1());
            body.put("wrongAnswer2", item.getWrongAnswer2());
            body.put("wrongAnswer3", item.getWrongAnswer3());
            body.put("difficulty", item.getDifficulty());
            body.put("orderIndex", item.getOrderIndex());
            body.put("explanation", item.getExplanation());
            body.put("imageUrl", item.getImageUrl());

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(serverUrl + "/api/game/questions/" + item.getQuestionId()))
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return response.statusCode() == 200;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    private void deleteQuestionFromServer(String questionId) {
        new Thread(() -> {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(serverUrl + "/api/game/questions/" + questionId))
                    .DELETE()
                    .build();

                httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    @FXML
    public void handleBack() {
        if (hasUnsavedChanges) {
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
            confirm.setTitle("Unsaved Changes");
            confirm.setHeaderText("You have unsaved changes");
            confirm.setContentText("Do you want to save before leaving?");

            ButtonType saveBtn = new ButtonType("Save");
            ButtonType discardBtn = new ButtonType("Discard");
            ButtonType cancelBtn = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);

            confirm.getButtonTypes().setAll(saveBtn, discardBtn, cancelBtn);

            Optional<ButtonType> result = confirm.showAndWait();
            if (result.isPresent()) {
                if (result.get() == saveBtn) {
                    handleSave();
                    // Wait a bit for save to complete
                    try { Thread.sleep(500); } catch (Exception ignored) {}
                } else if (result.get() == cancelBtn) {
                    return;
                }
            }
        }

        if (onBackCallback != null) {
            onBackCallback.run();
        }
    }

    private String truncate(String text, int maxLength) {
        if (text == null) return "";
        if (text.length() <= maxLength) return text;
        return text.substring(0, maxLength - 3) + "...";
    }

    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    // ========== Inner Classes ==========

    /**
     * Represents a question item in the editor.
     */
    public static class QuestionItem {
        private String questionId;
        private String questionText;
        private String correctAnswer;
        private String wrongAnswer1;
        private String wrongAnswer2;
        private String wrongAnswer3;
        private int difficulty = 1;
        private int orderIndex = 1;
        private String explanation;
        private String imageUrl;
        private boolean isNew = false;
        private boolean isModified = false;

        public String getQuestionId() { return questionId; }
        public void setQuestionId(String questionId) { this.questionId = questionId; }

        public String getQuestionText() { return questionText; }
        public void setQuestionText(String questionText) { this.questionText = questionText; }

        public String getCorrectAnswer() { return correctAnswer; }
        public void setCorrectAnswer(String correctAnswer) { this.correctAnswer = correctAnswer; }

        public String getWrongAnswer1() { return wrongAnswer1; }
        public void setWrongAnswer1(String wrongAnswer1) { this.wrongAnswer1 = wrongAnswer1; }

        public String getWrongAnswer2() { return wrongAnswer2; }
        public void setWrongAnswer2(String wrongAnswer2) { this.wrongAnswer2 = wrongAnswer2; }

        public String getWrongAnswer3() { return wrongAnswer3; }
        public void setWrongAnswer3(String wrongAnswer3) { this.wrongAnswer3 = wrongAnswer3; }

        public int getDifficulty() { return difficulty; }
        public void setDifficulty(int difficulty) { this.difficulty = difficulty; }

        public int getOrderIndex() { return orderIndex; }
        public void setOrderIndex(int orderIndex) { this.orderIndex = orderIndex; }

        public String getExplanation() { return explanation; }
        public void setExplanation(String explanation) { this.explanation = explanation; }

        public String getImageUrl() { return imageUrl; }
        public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }

        public boolean isNew() { return isNew; }
        public void setNew(boolean isNew) { this.isNew = isNew; }

        public boolean isModified() { return isModified; }
        public void setModified(boolean isModified) { this.isModified = isModified; }

        public boolean isValid() {
            return questionText != null && !questionText.isBlank()
                && correctAnswer != null && !correctAnswer.isBlank();
        }
    }

    /**
     * Data holder for question set info.
     */
    public static class QuestionSetData {
        private String setId;
        private String name;
        private String subject;
        private String gradeLevel;
        private boolean isPublic;

        public String getSetId() { return setId; }
        public void setSetId(String setId) { this.setId = setId; }

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public String getSubject() { return subject; }
        public void setSubject(String subject) { this.subject = subject; }

        public String getGradeLevel() { return gradeLevel; }
        public void setGradeLevel(String gradeLevel) { this.gradeLevel = gradeLevel; }

        public boolean isPublic() { return isPublic; }
        public void setPublic(boolean isPublic) { this.isPublic = isPublic; }
    }
}
