package com.heronix.teacher.ui.dialog;

import com.heronix.teacher.model.dto.talk.TalkChannelDTO;
import com.heronix.teacher.model.dto.talk.TalkMessageDTO;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Modality;

import java.util.List;
import java.util.Optional;

/**
 * Dialog for forwarding a message to another channel.
 */
public class ForwardMessageDialog extends Dialog<TalkChannelDTO> {

    private final TalkMessageDTO message;
    private final ListView<TalkChannelDTO> channelListView;
    private final TextField searchField;
    private final List<TalkChannelDTO> allChannels;

    public ForwardMessageDialog(TalkMessageDTO message, List<TalkChannelDTO> channels) {
        this.message = message;
        this.allChannels = channels;

        setTitle("Forward Message");
        setHeaderText("Select a channel to forward this message to:");
        initModality(Modality.APPLICATION_MODAL);

        // Set dialog pane style
        DialogPane dialogPane = getDialogPane();
        dialogPane.getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        dialogPane.setMinWidth(400);
        dialogPane.setMinHeight(450);

        // Main content
        VBox content = new VBox(12);
        content.setPadding(new Insets(10));

        // Message preview
        VBox previewBox = new VBox(4);
        previewBox.setStyle("-fx-background-color: #f5f5f5; -fx-padding: 10; -fx-background-radius: 6;");

        Label senderLabel = new Label("From: " + message.getSenderName());
        senderLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 12px;");

        String preview = message.getContent();
        if (preview != null && preview.length() > 100) {
            preview = preview.substring(0, 97) + "...";
        }
        Label contentLabel = new Label(preview);
        contentLabel.setWrapText(true);
        contentLabel.setStyle("-fx-font-size: 12px;");

        previewBox.getChildren().addAll(senderLabel, contentLabel);

        // Search field
        searchField = new TextField();
        searchField.setPromptText("Search channels...");
        searchField.textProperty().addListener((obs, oldVal, newVal) -> filterChannels(newVal));

        // Channel list
        channelListView = new ListView<>();
        channelListView.setPrefHeight(250);
        channelListView.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(TalkChannelDTO channel, boolean empty) {
                super.updateItem(channel, empty);
                if (empty || channel == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    HBox row = new HBox(10);
                    row.setAlignment(Pos.CENTER_LEFT);

                    String icon = getChannelIcon(channel);
                    Label iconLabel = new Label(icon);
                    iconLabel.setStyle("-fx-font-size: 16px;");

                    VBox info = new VBox(2);
                    Label nameLabel = new Label(channel.getName());
                    nameLabel.setStyle("-fx-font-weight: bold;");

                    String typeText = isDirectMessage(channel) ? "Direct Message" :
                            (isPublic(channel) ? "Public Channel" : "Private Channel");
                    Label typeLabel = new Label(typeText);
                    typeLabel.setStyle("-fx-font-size: 10px; -fx-text-fill: #888;");

                    info.getChildren().addAll(nameLabel, typeLabel);
                    row.getChildren().addAll(iconLabel, info);

                    setGraphic(row);
                    setText(null);
                }
            }
        });

        // Populate channels
        channelListView.getItems().addAll(channels);

        // Enable OK button only when a channel is selected
        Button okButton = (Button) dialogPane.lookupButton(ButtonType.OK);
        okButton.setText("Forward");
        okButton.setDisable(true);
        channelListView.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) ->
                okButton.setDisable(newVal == null));

        content.getChildren().addAll(
                new Label("Message to forward:"),
                previewBox,
                new Separator(),
                new Label("Select destination channel:"),
                searchField,
                channelListView
        );

        dialogPane.setContent(content);

        // Result converter
        setResultConverter(buttonType -> {
            if (buttonType == ButtonType.OK) {
                return channelListView.getSelectionModel().getSelectedItem();
            }
            return null;
        });
    }

    private void filterChannels(String query) {
        channelListView.getItems().clear();
        if (query == null || query.isEmpty()) {
            channelListView.getItems().addAll(allChannels);
        } else {
            String lowerQuery = query.toLowerCase();
            allChannels.stream()
                    .filter(ch -> ch.getName().toLowerCase().contains(lowerQuery))
                    .forEach(channelListView.getItems()::add);
        }
    }

    private String getChannelIcon(TalkChannelDTO channel) {
        if (isDirectMessage(channel)) return "\uD83D\uDC64"; // 👤
        String type = channel.getChannelType();
        if (type != null && type.equalsIgnoreCase("ANNOUNCEMENT")) return "\uD83D\uDCE2"; // 📢
        if (isPublic(channel)) return "#";
        return "\uD83D\uDD12"; // 🔒
    }

    private boolean isDirectMessage(TalkChannelDTO channel) {
        String type = channel.getChannelType();
        return type != null && (type.equalsIgnoreCase("DIRECT_MESSAGE") || type.equalsIgnoreCase("GROUP_MESSAGE"));
    }

    private boolean isPublic(TalkChannelDTO channel) {
        String type = channel.getChannelType();
        return type != null && type.equalsIgnoreCase("PUBLIC");
    }

    /**
     * Get the message being forwarded
     */
    public TalkMessageDTO getMessage() {
        return message;
    }

    /**
     * Show the dialog and return the selected channel
     */
    public Optional<TalkChannelDTO> showAndGetResult() {
        return showAndWait();
    }
}
