package com.heronix.teacher.ui.controller;

import com.heronix.teacher.service.EdGamesApiClient;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.LineChart;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.layout.VBox;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;

/**
 * Controller for the learning analytics dashboard.
 */
@Slf4j
@Component
public class GameLearningAnalyticsController implements Initializable {

    private final EdGamesApiClient edGamesApiClient;

    @FXML private Label studentNameLabel;
    @FXML private Label averageScoreLabel;
    @FXML private Label totalGamesLabel;
    @FXML private Label accuracyLabel;
    @FXML private VBox analyticsPane;
    @FXML private ListView<String> strugglingAreasList;
    @FXML private ListView<String> recommendationsList;

    public GameLearningAnalyticsController(EdGamesApiClient edGamesApiClient) {
        this.edGamesApiClient = edGamesApiClient;
    }

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        log.info("Initializing Game Learning Analytics Controller");
    }

    @FXML
    @SuppressWarnings("unchecked")
    public void loadStudentAnalytics(String studentId) {
        new Thread(() -> {
            Map<String, Object> analytics = edGamesApiClient.getLearningAnalytics(studentId);
            Platform.runLater(() -> {
                if (analytics.isEmpty()) {
                    studentNameLabel.setText("No data available");
                    return;
                }

                studentNameLabel.setText("Student: " + analytics.getOrDefault("studentName", studentId));
                averageScoreLabel.setText(String.format("Avg Score: %.1f%%",
                        ((Number) analytics.getOrDefault("averageScore", 0)).doubleValue()));
                totalGamesLabel.setText("Games Played: " + analytics.getOrDefault("totalGamesPlayed", 0));
                accuracyLabel.setText(String.format("Accuracy: %.1f%%",
                        ((Number) analytics.getOrDefault("overallAccuracy", 0)).doubleValue()));

                // Struggling areas
                strugglingAreasList.getItems().clear();
                Object struggling = analytics.get("strugglingAreas");
                if (struggling instanceof List) {
                    strugglingAreasList.getItems().addAll((List<String>) struggling);
                }

                // Recommendations
                recommendationsList.getItems().clear();
                Object recs = analytics.get("recommendations");
                if (recs instanceof List) {
                    recommendationsList.getItems().addAll((List<String>) recs);
                }

                log.info("Loaded analytics for student {}", studentId);
            });
        }).start();
    }
}
