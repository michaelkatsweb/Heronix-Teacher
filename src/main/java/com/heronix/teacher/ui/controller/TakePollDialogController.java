package com.heronix.teacher.ui.controller;

import com.heronix.teacher.service.PollService;
import com.heronix.teacher.service.SessionManager;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.*;

@Slf4j
@Component
@Scope("prototype")
@RequiredArgsConstructor
public class TakePollDialogController {

    private final PollService pollService;
    private final SessionManager sessionManager;

    @FXML private Label pollTitleLabel;
    @FXML private Label pollDescLabel;
    @FXML private VBox questionsContainer;
    @FXML private Label errorLabel;

    private Long pollId;
    private final List<AnswerEntry> answerEntries = new ArrayList<>();

    @SuppressWarnings("unchecked")
    public void loadPoll(Long pollId) {
        this.pollId = pollId;
        Map<String, Object> poll = pollService.getPoll(pollId);
        pollTitleLabel.setText(String.valueOf(poll.getOrDefault("title", "")));
        pollDescLabel.setText(String.valueOf(poll.getOrDefault("description", "")));

        List<Map<String, Object>> questions = (List<Map<String, Object>>) poll.get("questions");
        if (questions == null) return;

        for (int i = 0; i < questions.size(); i++) {
            Map<String, Object> q = questions.get(i);
            AnswerEntry entry = new AnswerEntry();
            entry.questionId = toLong(q.get("id"));
            entry.type = String.valueOf(q.getOrDefault("questionType", ""));

            VBox card = new VBox(5);
            card.setPadding(new Insets(10));
            card.setStyle("-fx-border-color: #ddd; -fx-border-radius: 5; -fx-background-color: #fafafa; -fx-background-radius: 5;");

            Label qLabel = new Label("Q" + (i + 1) + ": " + q.getOrDefault("questionText", ""));
            qLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 13;");
            qLabel.setWrapText(true);
            card.getChildren().add(qLabel);

            List<String> options = (List<String>) q.get("options");

            switch (entry.type) {
                case "MULTIPLE_CHOICE", "YES_NO" -> {
                    ToggleGroup group = new ToggleGroup();
                    List<RadioButton> radios = new ArrayList<>();
                    List<String> opts = options != null ? options :
                            ("YES_NO".equals(entry.type) ? List.of("Yes", "No") : List.of());
                    for (String opt : opts) {
                        RadioButton rb = new RadioButton(opt);
                        rb.setToggleGroup(group);
                        radios.add(rb);
                        card.getChildren().add(rb);
                    }
                    entry.radioButtons = radios;
                }
                case "CHECKBOX" -> {
                    List<CheckBox> checks = new ArrayList<>();
                    if (options != null) {
                        for (String opt : options) {
                            CheckBox cb = new CheckBox(opt);
                            checks.add(cb);
                            card.getChildren().add(cb);
                        }
                    }
                    entry.checkBoxes = checks;
                }
                case "SHORT_TEXT" -> {
                    TextArea ta = new TextArea();
                    ta.setPromptText("Your answer...");
                    ta.setPrefRowCount(2);
                    entry.textArea = ta;
                    card.getChildren().add(ta);
                }
            }

            answerEntries.add(entry);
            questionsContainer.getChildren().add(card);
        }
    }

    @FXML
    private void handleSubmit() {
        try {
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("respondentId", sessionManager.getCurrentTeacherId());
            response.put("respondentType", "TEACHER");
            response.put("respondentName", sessionManager.getCurrentTeacherName());

            List<Map<String, Object>> answers = new ArrayList<>();
            for (AnswerEntry entry : answerEntries) {
                Map<String, Object> answer = new LinkedHashMap<>();
                Map<String, Object> questionRef = new LinkedHashMap<>();
                questionRef.put("id", entry.questionId);
                answer.put("pollQuestion", questionRef);

                switch (entry.type) {
                    case "MULTIPLE_CHOICE", "YES_NO" -> {
                        String selected = null;
                        if (entry.radioButtons != null) {
                            for (RadioButton rb : entry.radioButtons) {
                                if (rb.isSelected()) { selected = rb.getText(); break; }
                            }
                        }
                        answer.put("selectedOptions", selected != null ? List.of(selected) : List.of());
                    }
                    case "CHECKBOX" -> {
                        List<String> selected = new ArrayList<>();
                        if (entry.checkBoxes != null) {
                            for (CheckBox cb : entry.checkBoxes) {
                                if (cb.isSelected()) selected.add(cb.getText());
                            }
                        }
                        answer.put("selectedOptions", selected);
                    }
                    case "SHORT_TEXT" -> {
                        answer.put("textAnswer", entry.textArea != null ? entry.textArea.getText() : "");
                        answer.put("selectedOptions", List.of());
                    }
                }
                answers.add(answer);
            }
            response.put("answers", answers);

            pollService.submitResponse(pollId, response);
            ((Stage) questionsContainer.getScene().getWindow()).close();
        } catch (Exception e) {
            log.error("Error submitting poll response", e);
            errorLabel.setText("Error: " + e.getMessage());
        }
    }

    @FXML
    private void handleCancel() {
        ((Stage) questionsContainer.getScene().getWindow()).close();
    }

    private Long toLong(Object val) {
        if (val instanceof Number) return ((Number) val).longValue();
        try { return Long.parseLong(String.valueOf(val)); } catch (Exception e) { return null; }
    }

    private static class AnswerEntry {
        Long questionId;
        String type;
        List<RadioButton> radioButtons;
        List<CheckBox> checkBoxes;
        TextArea textArea;
    }
}
