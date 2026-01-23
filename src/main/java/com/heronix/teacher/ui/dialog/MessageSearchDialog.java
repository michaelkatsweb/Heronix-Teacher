package com.heronix.teacher.ui.dialog;

import com.heronix.teacher.model.dto.talk.TalkMessageDTO;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import lombok.Getter;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.function.Consumer;

/**
 * Dialog for searching messages across channels
 */
public class MessageSearchDialog extends Dialog<TalkMessageDTO> {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("MMM d, yyyy h:mm a");

    private final TextField searchField;
    private final ListView<TalkMessageDTO> resultsList;
    private final Label statusLabel;

    @Getter
    private Consumer<String> onSearch;

    public MessageSearchDialog() {
        setTitle("Search Messages");
        setHeaderText("Search through message history");

        // Dialog buttons
        ButtonType selectButtonType = new ButtonType("Go to Message", ButtonBar.ButtonData.OK_DONE);
        getDialogPane().getButtonTypes().addAll(selectButtonType, ButtonType.CANCEL);

        // Style the dialog pane
        getDialogPane().getStyleClass().add("dialog-container");

        // Main content
        VBox content = new VBox(15);
        content.getStyleClass().add("dialog-content");
        content.setPadding(new Insets(20));
        content.setPrefWidth(550);
        content.setPrefHeight(450);

        // Search input with icon
        HBox searchBox = new HBox(10);
        searchBox.setAlignment(Pos.CENTER_LEFT);
        searchBox.getStyleClass().add("search-container");
        searchBox.setPadding(new Insets(8, 12, 8, 12));

        Label searchIcon = new Label("🔍");
        searchIcon.getStyleClass().add("search-icon");

        searchField = new TextField();
        searchField.setPromptText("Enter search term...");
        searchField.getStyleClass().add("search-field");
        HBox.setHgrow(searchField, Priority.ALWAYS);

        Button searchBtn = new Button("Search");
        searchBtn.getStyleClass().add("button-primary");
        searchBtn.setOnAction(e -> performSearch());

        searchField.setOnAction(e -> performSearch());

        searchBox.getChildren().addAll(searchIcon, searchField, searchBtn);
        content.getChildren().add(searchBox);

        // Status label
        statusLabel = new Label("Enter a search term to find messages");
        statusLabel.getStyleClass().add("text-muted");
        content.getChildren().add(statusLabel);

        // Results list
        resultsList = new ListView<>();
        resultsList.setPrefHeight(350);
        resultsList.getStyleClass().add("channel-list");
        resultsList.setCellFactory(lv -> new MessageSearchResultCell());
        VBox.setVgrow(resultsList, Priority.ALWAYS);
        content.getChildren().add(resultsList);

        getDialogPane().setContent(content);

        // Enable/disable select button based on selection
        Button selectButton = (Button) getDialogPane().lookupButton(selectButtonType);
        selectButton.getStyleClass().add("button-primary");
        selectButton.setDisable(true);
        resultsList.getSelectionModel().selectedItemProperty().addListener((obs, old, newVal) -> {
            selectButton.setDisable(newVal == null);
        });

        // Double-click to select
        resultsList.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2 && resultsList.getSelectionModel().getSelectedItem() != null) {
                setResult(resultsList.getSelectionModel().getSelectedItem());
                close();
            }
        });

        // Convert result
        setResultConverter(dialogButton -> {
            if (dialogButton == selectButtonType) {
                return resultsList.getSelectionModel().getSelectedItem();
            }
            return null;
        });

        // Focus search field
        searchField.requestFocus();
    }

    private void performSearch() {
        String query = searchField.getText().trim();
        if (query.isEmpty()) {
            statusLabel.setText("Please enter a search term");
            return;
        }

        statusLabel.setText("Searching...");
        resultsList.getItems().clear();

        if (onSearch != null) {
            onSearch.accept(query);
        }
    }

    /**
     * Set the search callback
     */
    public void setOnSearch(Consumer<String> callback) {
        this.onSearch = callback;
    }

    /**
     * Update the results list
     */
    public void updateResults(List<TalkMessageDTO> results) {
        resultsList.getItems().clear();
        if (results == null || results.isEmpty()) {
            statusLabel.setText("No messages found");
        } else {
            resultsList.getItems().addAll(results);
            statusLabel.setText("Found " + results.size() + " messages");
        }
    }

    /**
     * Custom cell for search results
     */
    private static class MessageSearchResultCell extends ListCell<TalkMessageDTO> {
        @Override
        protected void updateItem(TalkMessageDTO message, boolean empty) {
            super.updateItem(message, empty);

            if (empty || message == null) {
                setGraphic(null);
                setText(null);
            } else {
                VBox container = new VBox(6);
                container.setPadding(new Insets(12));
                container.getStyleClass().add("search-result-item");

                // Header with sender and channel
                HBox header = new HBox(10);
                header.setAlignment(Pos.CENTER_LEFT);

                Label senderLabel = new Label(message.getSenderName());
                senderLabel.getStyleClass().add("message-sender");

                Label channelLabel = new Label("in #" + (message.getChannelName() != null ?
                        message.getChannelName() : "Unknown"));
                channelLabel.getStyleClass().add("text-muted");

                Label timeLabel = new Label(message.getTimestamp() != null ?
                        message.getTimestamp().format(DATE_FORMAT) : "");
                timeLabel.getStyleClass().add("message-timestamp");

                header.getChildren().addAll(senderLabel, channelLabel, timeLabel);

                // Content preview
                String preview = message.getContent();
                if (preview != null && preview.length() > 150) {
                    preview = preview.substring(0, 147) + "...";
                }
                Label contentLabel = new Label(preview);
                contentLabel.getStyleClass().add("message-content");
                contentLabel.setWrapText(true);

                container.getChildren().addAll(header, contentLabel);
                setGraphic(container);
            }
        }
    }
}
