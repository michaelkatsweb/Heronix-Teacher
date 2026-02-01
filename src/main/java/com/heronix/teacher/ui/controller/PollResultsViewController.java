package com.heronix.teacher.ui.controller;

import com.heronix.teacher.service.PollService;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Slf4j
@Component
@Scope("prototype")
@RequiredArgsConstructor
public class PollResultsViewController {

    private final PollService pollService;

    @FXML private Label pollTitle;
    @FXML private Label totalResponsesLabel;
    @FXML private VBox resultsContainer;

    @SuppressWarnings("unchecked")
    public void loadResults(Long pollId) {
        try {
            Map<String, Object> results = pollService.getResults(pollId);
            pollTitle.setText(String.valueOf(results.getOrDefault("title", "Poll Results")));
            totalResponsesLabel.setText("Total responses: " + results.getOrDefault("totalResponses", 0));

            List<Map<String, Object>> questions = (List<Map<String, Object>>) results.get("questions");
            if (questions == null) return;

            for (int i = 0; i < questions.size(); i++) {
                Map<String, Object> q = questions.get(i);
                VBox qCard = new VBox(5);
                qCard.setPadding(new Insets(10));
                qCard.setStyle("-fx-border-color: #ddd; -fx-border-radius: 5; -fx-background-color: #fafafa; -fx-background-radius: 5;");

                Label qLabel = new Label("Q" + (i + 1) + ": " + q.getOrDefault("questionText", ""));
                qLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 13;");
                qLabel.setWrapText(true);
                qCard.getChildren().add(qLabel);

                String type = String.valueOf(q.getOrDefault("questionType", ""));
                if ("SHORT_TEXT".equals(type)) {
                    List<String> texts = (List<String>) q.get("textAnswers");
                    if (texts != null) {
                        for (String t : texts) {
                            Label tl = new Label("• " + t);
                            tl.setWrapText(true);
                            qCard.getChildren().add(tl);
                        }
                    }
                } else {
                    List<Map<String, Object>> options = (List<Map<String, Object>>) q.get("options");
                    if (options != null) {
                        for (Map<String, Object> opt : options) {
                            String optName = String.valueOf(opt.getOrDefault("option", ""));
                            int count = opt.get("count") instanceof Number ? ((Number) opt.get("count")).intValue() : 0;
                            long pct = opt.get("percentage") instanceof Number ? ((Number) opt.get("percentage")).longValue() : 0;

                            HBox row = new HBox(8);
                            row.setAlignment(Pos.CENTER_LEFT);
                            Label optLabel = new Label(optName);
                            optLabel.setPrefWidth(120);
                            ProgressBar bar = new ProgressBar(pct / 100.0);
                            bar.setPrefWidth(200);
                            bar.setPrefHeight(16);
                            Label pctLabel = new Label(count + " (" + pct + "%)");
                            pctLabel.setStyle("-fx-font-size: 11;");
                            row.getChildren().addAll(optLabel, bar, pctLabel);
                            qCard.getChildren().add(row);
                        }
                    }
                }
                resultsContainer.getChildren().add(qCard);
            }
        } catch (Exception e) {
            log.error("Error loading results", e);
            resultsContainer.getChildren().add(new Label("Error: " + e.getMessage()));
        }
    }

    @FXML
    private void handleClose() {
        ((Stage) resultsContainer.getScene().getWindow()).close();
    }
}
