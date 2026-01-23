package com.heronix.teacher.ui.dialog;

import com.heronix.teacher.model.dto.talk.TalkUserDTO;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.util.Callback;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Dialog for creating a new channel with optional member selection
 */
public class CreateChannelDialog extends Dialog<CreateChannelDialog.CreateChannelResult> {

    private final TextField nameField;
    private final TextArea descriptionArea;
    private final ComboBox<String> typeComboBox;
    private final ListView<UserSelectionItem> userListView;
    private final CheckBox sendInvitesCheckBox;
    private final TextField searchField;
    private final ObservableList<UserSelectionItem> allUsers;
    private final ObservableList<UserSelectionItem> filteredUsers;

    public CreateChannelDialog(List<TalkUserDTO> availableUsers) {
        setTitle("Create Channel");
        setHeaderText("Create a new communication channel");

        // Dialog buttons
        ButtonType createButtonType = new ButtonType("Create", ButtonBar.ButtonData.OK_DONE);
        getDialogPane().getButtonTypes().addAll(createButtonType, ButtonType.CANCEL);

        // Style the dialog pane
        getDialogPane().getStyleClass().add("dialog-container");

        // Main content
        VBox content = new VBox(15);
        content.getStyleClass().add("dialog-content");
        content.setPadding(new Insets(20));
        content.setPrefWidth(500);
        content.setPrefHeight(550);

        // Channel details section
        GridPane detailsGrid = new GridPane();
        detailsGrid.setHgap(12);
        detailsGrid.setVgap(12);

        // Name field
        Label nameLabel = new Label("Channel Name:");
        nameLabel.getStyleClass().add("text-bold");
        nameField = new TextField();
        nameField.setPromptText("e.g., Math Department, Project Team");
        nameField.setPrefWidth(300);
        detailsGrid.add(nameLabel, 0, 0);
        detailsGrid.add(nameField, 1, 0);

        // Description field
        Label descLabel = new Label("Description:");
        descLabel.getStyleClass().add("text-bold");
        descriptionArea = new TextArea();
        descriptionArea.setPromptText("Brief description of the channel's purpose");
        descriptionArea.setPrefRowCount(2);
        descriptionArea.setWrapText(true);
        detailsGrid.add(descLabel, 0, 1);
        detailsGrid.add(descriptionArea, 1, 1);

        // Channel type
        Label typeLabel = new Label("Channel Type:");
        typeLabel.getStyleClass().add("text-bold");
        typeComboBox = new ComboBox<>();
        typeComboBox.getItems().addAll(
                "PRIVATE - Only invited members can see and join",
                "GROUP - Group chat for selected members",
                "PUBLIC - Visible to all, anyone can join",
                "DEPARTMENT - Department-specific channel"
        );
        typeComboBox.getSelectionModel().selectFirst();
        typeComboBox.setPrefWidth(300);
        detailsGrid.add(typeLabel, 0, 2);
        detailsGrid.add(typeComboBox, 1, 2);

        content.getChildren().add(detailsGrid);

        // Member selection section
        Label membersLabel = new Label("Add Members:");
        membersLabel.getStyleClass().addAll("section-title");
        content.getChildren().add(membersLabel);

        // Search field with icon
        HBox searchContainer = new HBox(8);
        searchContainer.getStyleClass().add("search-container");
        searchContainer.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        Label searchIcon = new Label("🔍");
        searchIcon.getStyleClass().add("search-icon");
        searchField = new TextField();
        searchField.setPromptText("Search users...");
        searchField.getStyleClass().add("search-field");
        searchField.textProperty().addListener((obs, old, newVal) -> filterUserList(newVal));
        javafx.scene.layout.HBox.setHgrow(searchField, javafx.scene.layout.Priority.ALWAYS);
        searchContainer.getChildren().addAll(searchIcon, searchField);
        content.getChildren().add(searchContainer);

        // User list with checkboxes
        allUsers = FXCollections.observableArrayList();
        filteredUsers = FXCollections.observableArrayList();

        for (TalkUserDTO user : availableUsers) {
            allUsers.add(new UserSelectionItem(user));
        }
        filteredUsers.addAll(allUsers);

        userListView = new ListView<>(filteredUsers);
        userListView.setPrefHeight(200);
        userListView.getStyleClass().add("user-list");
        userListView.setCellFactory(createUserCellFactory());
        content.getChildren().add(userListView);

        // Selected count and send invites option
        HBox optionsBox = new HBox(20);
        optionsBox.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        optionsBox.setPadding(new Insets(8, 0, 8, 0));

        sendInvitesCheckBox = new CheckBox("Send invite notifications to selected members");
        sendInvitesCheckBox.setSelected(true);

        optionsBox.getChildren().add(sendInvitesCheckBox);
        content.getChildren().add(optionsBox);

        // Help text
        Label helpText = new Label("Tip: Double-click a user in the online list to start a direct message.");
        helpText.getStyleClass().add("text-muted");
        content.getChildren().add(helpText);

        getDialogPane().setContent(content);

        // Enable/disable create button based on name field
        Button createButton = (Button) getDialogPane().lookupButton(createButtonType);
        createButton.getStyleClass().add("button-primary");
        createButton.setDisable(true);
        nameField.textProperty().addListener((obs, old, newVal) -> {
            createButton.setDisable(newVal.trim().isEmpty());
        });

        // Convert the result
        setResultConverter(dialogButton -> {
            if (dialogButton == createButtonType) {
                return new CreateChannelResult(
                        nameField.getText().trim(),
                        descriptionArea.getText().trim(),
                        getSelectedChannelType(),
                        getSelectedUserIds(),
                        sendInvitesCheckBox.isSelected()
                );
            }
            return null;
        });

        // Focus on name field
        nameField.requestFocus();
    }

    private Callback<ListView<UserSelectionItem>, ListCell<UserSelectionItem>> createUserCellFactory() {
        return listView -> new ListCell<>() {
            private final CheckBox checkBox = new CheckBox();
            private final Label nameLabel = new Label();
            private final Label roleLabel = new Label();
            private final HBox container = new HBox(10);

            {
                container.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
                container.getStyleClass().add("user-item");
                container.setPadding(new Insets(4, 8, 4, 8));
                nameLabel.getStyleClass().add("user-name");
                roleLabel.getStyleClass().add("user-role-badge-text");
                container.getChildren().addAll(checkBox, nameLabel, roleLabel);

                checkBox.setOnAction(e -> {
                    UserSelectionItem item = getItem();
                    if (item != null) {
                        item.setSelected(checkBox.isSelected());
                    }
                });

                setOnMouseClicked(e -> {
                    if (e.getClickCount() == 1 && getItem() != null) {
                        checkBox.setSelected(!checkBox.isSelected());
                        getItem().setSelected(checkBox.isSelected());
                    }
                });
            }

            @Override
            protected void updateItem(UserSelectionItem item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                } else {
                    checkBox.setSelected(item.isSelected());
                    nameLabel.setText(item.getUser().getFullName().trim());
                    String role = item.getUser().getRole();
                    roleLabel.setText(role != null ? "(" + role + ")" : "");
                    setGraphic(container);
                }
            }
        };
    }

    private void filterUserList(String searchTerm) {
        filteredUsers.clear();
        if (searchTerm == null || searchTerm.isEmpty()) {
            filteredUsers.addAll(allUsers);
        } else {
            String lowerSearch = searchTerm.toLowerCase();
            filteredUsers.addAll(
                    allUsers.stream()
                            .filter(item -> {
                                TalkUserDTO user = item.getUser();
                                return user.getFullName().toLowerCase().contains(lowerSearch) ||
                                        (user.getDepartment() != null && user.getDepartment().toLowerCase().contains(lowerSearch)) ||
                                        (user.getRole() != null && user.getRole().toLowerCase().contains(lowerSearch));
                            })
                            .collect(Collectors.toList())
            );
        }
    }

    private String getSelectedChannelType() {
        String selected = typeComboBox.getValue();
        if (selected == null) return "PRIVATE";
        if (selected.startsWith("PRIVATE")) return "PRIVATE";
        if (selected.startsWith("GROUP")) return "GROUP";
        if (selected.startsWith("PUBLIC")) return "PUBLIC";
        if (selected.startsWith("DEPARTMENT")) return "DEPARTMENT";
        return "PRIVATE";
    }

    private List<Long> getSelectedUserIds() {
        return allUsers.stream()
                .filter(UserSelectionItem::isSelected)
                .map(item -> item.getUser().getId())
                .collect(Collectors.toList());
    }

    /**
     * Helper class for user selection with checkbox state
     */
    public static class UserSelectionItem {
        @Getter
        private final TalkUserDTO user;
        @Getter
        private boolean selected;

        public UserSelectionItem(TalkUserDTO user) {
            this.user = user;
            this.selected = false;
        }

        public void setSelected(boolean selected) {
            this.selected = selected;
        }
    }

    /**
     * Result returned from the dialog
     */
    @Getter
    public static class CreateChannelResult {
        private final String name;
        private final String description;
        private final String channelType;
        private final List<Long> memberIds;
        private final boolean sendInvites;

        public CreateChannelResult(String name, String description, String channelType,
                                   List<Long> memberIds, boolean sendInvites) {
            this.name = name;
            this.description = description;
            this.channelType = channelType;
            this.memberIds = memberIds != null ? memberIds : new ArrayList<>();
            this.sendInvites = sendInvites;
        }
    }
}
