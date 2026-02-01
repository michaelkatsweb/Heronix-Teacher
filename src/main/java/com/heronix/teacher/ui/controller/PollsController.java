package com.heronix.teacher.ui.controller;

import com.heronix.teacher.service.PollService;
import com.heronix.teacher.service.SessionManager;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class PollsController {

    private final PollService pollService;
    private final SessionManager sessionManager;
    private final ApplicationContext applicationContext;

    // My Polls tab
    @FXML private TableView<Map<String, Object>> myPollsTable;
    @FXML private TableColumn<Map<String, Object>, String> myTitleCol;
    @FXML private TableColumn<Map<String, Object>, String> myAudienceCol;
    @FXML private TableColumn<Map<String, Object>, String> myStatusCol;
    @FXML private TableColumn<Map<String, Object>, String> myResponsesCol;
    @FXML private TableColumn<Map<String, Object>, String> myCreatedCol;
    @FXML private TableColumn<Map<String, Object>, Void> myActionsCol;
    @FXML private Label myPollsCount;

    // Available Polls tab
    @FXML private TableView<Map<String, Object>> availablePollsTable;
    @FXML private TableColumn<Map<String, Object>, String> avTitleCol;
    @FXML private TableColumn<Map<String, Object>, String> avCreatorCol;
    @FXML private TableColumn<Map<String, Object>, String> avQuestionsCol;
    @FXML private TableColumn<Map<String, Object>, String> avAnonymousCol;
    @FXML private TableColumn<Map<String, Object>, Void> avActionsCol;
    @FXML private Label availablePollsCount;

    @FXML private TabPane tabPane;

    private final ObservableList<Map<String, Object>> myPolls = FXCollections.observableArrayList();
    private final ObservableList<Map<String, Object>> availablePolls = FXCollections.observableArrayList();


    @FXML
    public void initialize() {
        setupMyPollsColumns();
        setupAvailablePollsColumns();
        myPollsTable.setItems(myPolls);
        availablePollsTable.setItems(availablePolls);
        refreshMyPolls();
        refreshAvailablePolls();
    }

    private void setupMyPollsColumns() {
        myTitleCol.setCellValueFactory(cd -> new SimpleStringProperty(getStr(cd.getValue(), "title")));
        myAudienceCol.setCellValueFactory(cd -> new SimpleStringProperty(getStr(cd.getValue(), "targetAudience")));
        myStatusCol.setCellValueFactory(cd -> new SimpleStringProperty(getStr(cd.getValue(), "status")));
        myResponsesCol.setCellValueFactory(cd -> new SimpleStringProperty(getStr(cd.getValue(), "totalResponses")));
        myCreatedCol.setCellValueFactory(cd -> new SimpleStringProperty(getStr(cd.getValue(), "createdAt")));

        myActionsCol.setCellFactory(col -> new TableCell<>() {
            private final Button publishBtn = new Button("Publish");
            private final Button closeBtn = new Button("Close");
            private final Button resultsBtn = new Button("Results");
            private final Button deleteBtn = new Button("Delete");

            {
                publishBtn.setStyle("-fx-font-size: 10; -fx-background-color: #4CAF50; -fx-text-fill: white;");
                closeBtn.setStyle("-fx-font-size: 10; -fx-background-color: #FF9800; -fx-text-fill: white;");
                resultsBtn.setStyle("-fx-font-size: 10; -fx-background-color: #2196F3; -fx-text-fill: white;");
                deleteBtn.setStyle("-fx-font-size: 10; -fx-background-color: #f44336; -fx-text-fill: white;");

                publishBtn.setOnAction(e -> {
                    Map<String, Object> poll = getTableView().getItems().get(getIndex());
                    Long id = toLong(poll.get("id"));
                    if (id != null) { pollService.publishPoll(id); refreshMyPolls(); }
                });
                closeBtn.setOnAction(e -> {
                    Map<String, Object> poll = getTableView().getItems().get(getIndex());
                    Long id = toLong(poll.get("id"));
                    if (id != null) { pollService.closePoll(id); refreshMyPolls(); }
                });
                resultsBtn.setOnAction(e -> {
                    Map<String, Object> poll = getTableView().getItems().get(getIndex());
                    Long id = toLong(poll.get("id"));
                    if (id != null) openResults(id, getStr(poll, "title"));
                });
                deleteBtn.setOnAction(e -> {
                    Map<String, Object> poll = getTableView().getItems().get(getIndex());
                    Long id = toLong(poll.get("id"));
                    if (id != null) {
                        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, "Delete this poll?");
                        alert.showAndWait().ifPresent(btn -> {
                            if (btn == ButtonType.OK) { pollService.deletePoll(id); refreshMyPolls(); }
                        });
                    }
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) { setGraphic(null); return; }
                Map<String, Object> poll = getTableView().getItems().get(getIndex());
                String status = getStr(poll, "status");
                HBox box = new HBox(3);
                if ("DRAFT".equals(status)) {
                    box.getChildren().addAll(publishBtn, deleteBtn);
                } else if ("PUBLISHED".equals(status)) {
                    box.getChildren().addAll(closeBtn, resultsBtn);
                } else {
                    box.getChildren().addAll(resultsBtn, deleteBtn);
                }
                setGraphic(box);
            }
        });
    }

    private void setupAvailablePollsColumns() {
        avTitleCol.setCellValueFactory(cd -> new SimpleStringProperty(getStr(cd.getValue(), "title")));
        avCreatorCol.setCellValueFactory(cd -> new SimpleStringProperty(getStr(cd.getValue(), "creatorName")));
        avQuestionsCol.setCellValueFactory(cd -> {
            Object questions = cd.getValue().get("questions");
            if (questions instanceof List) return new SimpleStringProperty(String.valueOf(((List<?>) questions).size()));
            return new SimpleStringProperty("0");
        });
        avAnonymousCol.setCellValueFactory(cd -> new SimpleStringProperty(
                Boolean.TRUE.equals(cd.getValue().get("isAnonymous")) ? "Yes" : "No"));

        avActionsCol.setCellFactory(col -> new TableCell<>() {
            private final Button takeBtn = new Button("Take Poll");
            private final Button resultsBtn = new Button("Results");

            {
                takeBtn.setStyle("-fx-font-size: 10; -fx-background-color: #4CAF50; -fx-text-fill: white;");
                resultsBtn.setStyle("-fx-font-size: 10; -fx-background-color: #2196F3; -fx-text-fill: white;");

                takeBtn.setOnAction(e -> {
                    Map<String, Object> poll = getTableView().getItems().get(getIndex());
                    Long id = toLong(poll.get("id"));
                    if (id != null) openTakePoll(id);
                });
                resultsBtn.setOnAction(e -> {
                    Map<String, Object> poll = getTableView().getItems().get(getIndex());
                    Long id = toLong(poll.get("id"));
                    if (id != null) openResults(id, getStr(poll, "title"));
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) { setGraphic(null); return; }
                HBox box = new HBox(3);
                box.getChildren().addAll(takeBtn, resultsBtn);
                setGraphic(box);
            }
        });
    }

    @FXML
    public void handleCreatePoll() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/CreatePollDialog.fxml"));
            loader.setControllerFactory(applicationContext::getBean);
            Parent root = loader.load();
            CreatePollDialogController controller = loader.getController();
            controller.setOnSave(() -> refreshMyPolls());

            Stage stage = new Stage();
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.setTitle("Create New Poll");
            stage.setScene(new Scene(root, 700, 600));
            stage.showAndWait();
        } catch (Exception e) {
            log.error("Error opening create poll dialog", e);
        }
    }

    private void openTakePoll(Long pollId) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/TakePollDialog.fxml"));
            loader.setControllerFactory(applicationContext::getBean);
            Parent root = loader.load();
            TakePollDialogController controller = loader.getController();
            controller.loadPoll(pollId);

            Stage stage = new Stage();
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.setTitle("Take Poll");
            stage.setScene(new Scene(root, 600, 550));
            stage.showAndWait();
            refreshAvailablePolls();
        } catch (Exception e) {
            log.error("Error opening take poll dialog", e);
        }
    }

    private void openResults(Long pollId, String title) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/PollResultsView.fxml"));
            loader.setControllerFactory(applicationContext::getBean);
            Parent root = loader.load();
            PollResultsViewController controller = loader.getController();
            controller.loadResults(pollId);

            Stage stage = new Stage();
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.setTitle("Results: " + title);
            stage.setScene(new Scene(root, 600, 500));
            stage.showAndWait();
        } catch (Exception e) {
            log.error("Error opening results", e);
        }
    }

    @FXML
    public void refreshMyPolls() {
        List<Map<String, Object>> polls = pollService.getMyPolls(sessionManager.getCurrentTeacherName());
        myPolls.setAll(polls);
        myPollsCount.setText(polls.size() + " poll" + (polls.size() != 1 ? "s" : ""));
    }

    @FXML
    public void refreshAvailablePolls() {
        List<Map<String, Object>> polls = pollService.getActivePolls("TEACHERS");
        availablePolls.setAll(polls);
        availablePollsCount.setText(polls.size() + " poll" + (polls.size() != 1 ? "s" : ""));
    }

    private String getStr(Map<String, Object> map, String key) {
        Object val = map.get(key);
        return val != null ? val.toString() : "";
    }

    private Long toLong(Object val) {
        if (val instanceof Number) return ((Number) val).longValue();
        try { return Long.parseLong(String.valueOf(val)); } catch (Exception e) { return null; }
    }
}
