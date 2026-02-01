package com.heronix.teacher.ui.controller;

import com.heronix.teacher.service.PollService;
import com.heronix.teacher.service.SessionManager;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Component
@Scope("prototype")
@RequiredArgsConstructor
public class CreatePollDialogController {

    private final PollService pollService;
    private final SessionManager sessionManager;

    @FXML private TextField titleField;
    @FXML private TextArea descriptionField;
    @FXML private ComboBox<String> audienceCombo;
    @FXML private CheckBox anonymousCheck;
    @FXML private ComboBox<String> resultsVisibilityCombo;
    @FXML private VBox questionsContainer;
    @FXML private Label errorLabel;

    private Runnable onSave;
    private final List<QuestionEntry> questionEntries = new ArrayList<>();

    @FXML
    public void initialize() {
        audienceCombo.setItems(FXCollections.observableArrayList("STUDENTS", "TEACHERS", "PARENTS", "STAFF", "ALL"));
        audienceCombo.getSelectionModel().select("ALL");
        resultsVisibilityCombo.setItems(FXCollections.observableArrayList("AFTER_VOTING", "AFTER_CLOSE", "NEVER"));
        resultsVisibilityCombo.getSelectionModel().select("AFTER_CLOSE");
    }

    public void setOnSave(Runnable onSave) { this.onSave = onSave; }

    @FXML private void addMultipleChoice() { addQuestion("MULTIPLE_CHOICE"); }
    @FXML private void addCheckbox() { addQuestion("CHECKBOX"); }
    @FXML private void addYesNo() { addQuestion("YES_NO"); }
    @FXML private void addShortText() { addQuestion("SHORT_TEXT"); }

    private void addQuestion(String type) {
        QuestionEntry entry = new QuestionEntry();
        entry.type = type;

        VBox card = new VBox(5);
        card.setPadding(new Insets(10));
        card.setStyle("-fx-border-color: #ccc; -fx-border-radius: 5; -fx-background-color: #fafafa; -fx-background-radius: 5;");

        int qNum = questionEntries.size() + 1;
        HBox header = new HBox(10);
        header.setAlignment(Pos.CENTER_LEFT);
        Label typeLabel = new Label("Q" + qNum + " [" + type.replace("_", " ") + "]");
        typeLabel.setStyle("-fx-font-weight: bold;");
        CheckBox reqCheck = new CheckBox("Required");
        reqCheck.setSelected(true);
        entry.requiredCheck = reqCheck;
        Button removeBtn = new Button("Remove");
        removeBtn.setStyle("-fx-font-size: 10; -fx-background-color: #f44336; -fx-text-fill: white;");
        removeBtn.setOnAction(e -> { questionsContainer.getChildren().remove(card); questionEntries.remove(entry); });
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        header.getChildren().addAll(typeLabel, reqCheck, spacer, removeBtn);

        TextField questionText = new TextField();
        questionText.setPromptText("Enter question text...");
        entry.questionTextField = questionText;
        card.getChildren().addAll(header, questionText);

        if ("MULTIPLE_CHOICE".equals(type) || "CHECKBOX".equals(type)) {
            TextArea optionsArea = new TextArea();
            optionsArea.setPromptText("Enter options, one per line");
            optionsArea.setPrefRowCount(3);
            entry.optionsArea = optionsArea;
            card.getChildren().addAll(new Label("Options (one per line):"), optionsArea);
        }

        entry.card = card;
        questionEntries.add(entry);
        questionsContainer.getChildren().add(card);
    }

    @FXML
    private void handleSave() {
        String title = titleField.getText();
        if (title == null || title.trim().isEmpty()) { errorLabel.setText("Title is required"); return; }
        if (questionEntries.isEmpty()) { errorLabel.setText("Add at least one question"); return; }

        try {
            Map<String, Object> poll = new LinkedHashMap<>();
            poll.put("title", title.trim());
            poll.put("description", descriptionField.getText());
            poll.put("targetAudience", audienceCombo.getValue());
            poll.put("isAnonymous", anonymousCheck.isSelected());
            poll.put("allowMultipleResponses", false);
            poll.put("resultsVisibility", resultsVisibilityCombo.getValue());
            poll.put("creatorName", sessionManager.getCurrentTeacherName());
            poll.put("creatorType", "TEACHER");

            List<Map<String, Object>> questions = new ArrayList<>();
            for (int i = 0; i < questionEntries.size(); i++) {
                QuestionEntry entry = questionEntries.get(i);
                String qText = entry.questionTextField.getText();
                if (qText == null || qText.trim().isEmpty()) { errorLabel.setText("Question " + (i+1) + " text required"); return; }

                Map<String, Object> q = new LinkedHashMap<>();
                q.put("questionText", qText.trim());
                q.put("questionType", entry.type);
                q.put("displayOrder", i);
                q.put("isRequired", entry.requiredCheck.isSelected());

                if ("MULTIPLE_CHOICE".equals(entry.type) || "CHECKBOX".equals(entry.type)) {
                    String optText = entry.optionsArea != null ? entry.optionsArea.getText() : "";
                    List<String> opts = Arrays.stream(optText.split("\n"))
                            .map(String::trim).filter(s -> !s.isEmpty()).collect(Collectors.toList());
                    if (opts.size() < 2) { errorLabel.setText("Q" + (i+1) + " needs at least 2 options"); return; }
                    q.put("options", opts);
                } else if ("YES_NO".equals(entry.type)) {
                    q.put("options", List.of("Yes", "No"));
                }
                questions.add(q);
            }
            poll.put("questions", questions);

            pollService.createPoll(poll);
            if (onSave != null) onSave.run();
            ((Stage) titleField.getScene().getWindow()).close();
        } catch (Exception e) {
            log.error("Error saving poll", e);
            errorLabel.setText("Error: " + e.getMessage());
        }
    }

    @FXML
    private void handleCancel() {
        ((Stage) titleField.getScene().getWindow()).close();
    }

    private static class QuestionEntry {
        String type;
        TextField questionTextField;
        TextArea optionsArea;
        CheckBox requiredCheck;
        VBox card;
    }
}
