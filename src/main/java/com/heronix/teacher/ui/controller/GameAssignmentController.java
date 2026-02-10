package com.heronix.teacher.ui.controller;

import com.heronix.teacher.service.EdGamesApiClient;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;

/**
 * Controller for the game assignments management view.
 */
@Slf4j
@Component
public class GameAssignmentController implements Initializable {

    private final EdGamesApiClient edGamesApiClient;

    @FXML private TableView<Map<String, Object>> assignmentsTable;
    @FXML private TableColumn<Map<String, Object>, String> titleColumn;
    @FXML private TableColumn<Map<String, Object>, String> gameColumn;
    @FXML private TableColumn<Map<String, Object>, String> statusColumn;
    @FXML private TableColumn<Map<String, Object>, String> completionColumn;
    @FXML private VBox progressDetailPane;
    @FXML private Label selectedAssignmentLabel;

    public GameAssignmentController(EdGamesApiClient edGamesApiClient) {
        this.edGamesApiClient = edGamesApiClient;
    }

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        log.info("Initializing Game Assignment Controller");
    }

    @FXML
    public void loadAssignments(String teacherId) {
        new Thread(() -> {
            List<Map<String, Object>> assignments = edGamesApiClient.getAssignments(teacherId);
            Platform.runLater(() -> {
                assignmentsTable.getItems().clear();
                assignmentsTable.getItems().addAll(assignments);
                log.info("Loaded {} assignments", assignments.size());
            });
        }).start();
    }

    @FXML
    public void onCreateAssignment() {
        log.info("Create assignment dialog requested");
        // Would open a dialog to create an assignment
    }

    @FXML
    public void onViewProgress() {
        Map<String, Object> selected = assignmentsTable.getSelectionModel().getSelectedItem();
        if (selected == null) return;

        String assignmentId = (String) selected.get("assignmentId");
        new Thread(() -> {
            List<Map<String, Object>> progress = edGamesApiClient.getAssignmentProgress(assignmentId);
            Platform.runLater(() -> {
                selectedAssignmentLabel.setText("Progress for: " + selected.get("title"));
                log.info("Loaded {} progress entries for assignment {}", progress.size(), assignmentId);
            });
        }).start();
    }
}
