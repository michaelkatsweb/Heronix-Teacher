package com.heronix.teacher.ui.dialog;

import com.heronix.teacher.model.dto.talk.TalkChannelDTO;
import com.heronix.teacher.model.dto.talk.TalkUserDTO;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Dialog for viewing and managing channel members
 */
public class ChannelMembersDialog extends Dialog<Void> {

    private final TalkChannelDTO channel;
    private final ListView<TalkUserDTO> membersList;
    private final TextField searchField;
    private final Label memberCountLabel;
    private final Long currentUserId;

    private Consumer<List<Long>> onInviteUsers;
    private Consumer<Long> onRemoveUser;
    private Consumer<TalkUserDTO> onStartDM;
    private Supplier<List<TalkUserDTO>> availableUsersSupplier;
    private List<TalkUserDTO> currentMembers;

    public ChannelMembersDialog(TalkChannelDTO channel, List<TalkUserDTO> members) {
        this(channel, members, null);
    }

    public ChannelMembersDialog(TalkChannelDTO channel, List<TalkUserDTO> members, Long currentUserId) {
        this.channel = channel;
        this.currentUserId = currentUserId;
        this.currentMembers = new ArrayList<>(members);

        setTitle("Channel Members");
        setHeaderText(channel.getName() + " - Members");

        // Dialog button
        getDialogPane().getButtonTypes().addAll(ButtonType.CLOSE);

        // Style the dialog pane
        getDialogPane().getStyleClass().add("dialog-container");

        // Main content
        VBox content = new VBox(15);
        content.getStyleClass().add("dialog-content");
        content.setPadding(new Insets(20));
        content.setPrefWidth(400);
        content.setPrefHeight(500);

        // Header with count
        HBox header = new HBox(10);
        header.setAlignment(Pos.CENTER_LEFT);

        Label titleLabel = new Label("\uD83D\uDC65 Members");
        titleLabel.getStyleClass().add("section-title");

        memberCountLabel = new Label("(" + members.size() + ")");
        memberCountLabel.getStyleClass().add("text-muted");

        header.getChildren().addAll(titleLabel, memberCountLabel);
        content.getChildren().add(header);

        // Search field with icon
        HBox searchContainer = new HBox(8);
        searchContainer.getStyleClass().add("search-container");
        searchContainer.setAlignment(Pos.CENTER_LEFT);
        Label searchIcon = new Label("🔍");
        searchIcon.getStyleClass().add("search-icon");
        searchField = new TextField();
        searchField.setPromptText("Search members...");
        searchField.getStyleClass().add("search-field");
        searchField.textProperty().addListener((obs, old, newVal) -> filterMembers(newVal, members));
        HBox.setHgrow(searchField, Priority.ALWAYS);
        searchContainer.getChildren().addAll(searchIcon, searchField);
        content.getChildren().add(searchContainer);

        // Members list
        membersList = new ListView<>();
        membersList.setPrefHeight(380);
        membersList.getStyleClass().add("user-list");
        membersList.setCellFactory(lv -> new MemberCell());
        membersList.getItems().addAll(members);
        VBox.setVgrow(membersList, Priority.ALWAYS);
        content.getChildren().add(membersList);

        // Invite button (for private channels)
        if ("PRIVATE".equals(channel.getChannelType()) || "GROUP".equals(channel.getChannelType())) {
            Button inviteBtn = new Button("\u2795 Invite Member");
            inviteBtn.getStyleClass().add("button-success");
            inviteBtn.setOnAction(e -> showInviteDialog());
            content.getChildren().add(inviteBtn);
        }

        getDialogPane().setContent(content);
    }

    private void filterMembers(String searchTerm, List<TalkUserDTO> allMembers) {
        membersList.getItems().clear();
        if (searchTerm == null || searchTerm.isEmpty()) {
            membersList.getItems().addAll(allMembers);
        } else {
            String lowerSearch = searchTerm.toLowerCase();
            allMembers.stream()
                    .filter(m -> m.getFullName().toLowerCase().contains(lowerSearch) ||
                            (m.getDepartment() != null && m.getDepartment().toLowerCase().contains(lowerSearch)))
                    .forEach(membersList.getItems()::add);
        }
        memberCountLabel.setText("(" + membersList.getItems().size() + ")");
    }

    private void showInviteDialog() {
        if (availableUsersSupplier == null) {
            Alert alert = new Alert(Alert.AlertType.WARNING);
            alert.setTitle("Cannot Invite");
            alert.setHeaderText("User list not available");
            alert.setContentText("Unable to load available users. Please try again later.");
            alert.showAndWait();
            return;
        }

        // Get all available users
        List<TalkUserDTO> allUsers = availableUsersSupplier.get();
        if (allUsers == null || allUsers.isEmpty()) {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("No Users Available");
            alert.setHeaderText("No users to invite");
            alert.setContentText("There are no other users available to invite to this channel.");
            alert.showAndWait();
            return;
        }

        // Filter out users who are already members
        Set<Long> memberIds = currentMembers.stream()
                .map(TalkUserDTO::getId)
                .collect(Collectors.toSet());

        List<TalkUserDTO> availableToInvite = allUsers.stream()
                .filter(u -> !memberIds.contains(u.getId()))
                .collect(Collectors.toList());

        if (availableToInvite.isEmpty()) {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("No Users Available");
            alert.setHeaderText("All users are already members");
            alert.setContentText("All available users are already members of this channel.");
            alert.showAndWait();
            return;
        }

        // Create invite dialog
        Dialog<List<Long>> inviteDialog = new Dialog<>();
        inviteDialog.setTitle("Invite Members");
        inviteDialog.setHeaderText("Select users to invite to " + channel.getName());

        inviteDialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        Button okButton = (Button) inviteDialog.getDialogPane().lookupButton(ButtonType.OK);
        okButton.setText("Invite");
        okButton.setDisable(true);

        VBox content = new VBox(12);
        content.setPadding(new Insets(15));
        content.setPrefWidth(350);
        content.setPrefHeight(400);

        // Search field
        TextField searchField = new TextField();
        searchField.setPromptText("Search users...");

        // User list with checkboxes
        ListView<TalkUserDTO> userListView = new ListView<>();
        userListView.setPrefHeight(300);
        userListView.getItems().addAll(availableToInvite);

        List<Long> selectedUserIds = new ArrayList<>();

        userListView.setCellFactory(lv -> new ListCell<>() {
            private final CheckBox checkBox = new CheckBox();
            private final VBox container = new VBox(2);
            private final Label nameLabel = new Label();
            private final Label roleLabel = new Label();

            {
                container.getChildren().addAll(nameLabel, roleLabel);
                nameLabel.setStyle("-fx-font-weight: bold;");
                roleLabel.setStyle("-fx-font-size: 10px; -fx-text-fill: #888;");

                checkBox.selectedProperty().addListener((obs, wasSelected, isSelected) -> {
                    TalkUserDTO user = getItem();
                    if (user != null) {
                        if (isSelected) {
                            if (!selectedUserIds.contains(user.getId())) {
                                selectedUserIds.add(user.getId());
                            }
                        } else {
                            selectedUserIds.remove(user.getId());
                        }
                        okButton.setDisable(selectedUserIds.isEmpty());
                    }
                });
            }

            @Override
            protected void updateItem(TalkUserDTO user, boolean empty) {
                super.updateItem(user, empty);
                if (empty || user == null) {
                    setGraphic(null);
                } else {
                    nameLabel.setText(user.getFullName());
                    String roleText = user.getRole() != null ? user.getRole() : "";
                    if (user.getDepartment() != null) {
                        roleText += (roleText.isEmpty() ? "" : " • ") + user.getDepartment();
                    }
                    roleLabel.setText(roleText);
                    checkBox.setSelected(selectedUserIds.contains(user.getId()));

                    HBox row = new HBox(10);
                    row.setAlignment(Pos.CENTER_LEFT);
                    row.getChildren().addAll(checkBox, container);
                    setGraphic(row);
                }
            }
        });

        // Search filter
        searchField.textProperty().addListener((obs, oldVal, newVal) -> {
            userListView.getItems().clear();
            if (newVal == null || newVal.isEmpty()) {
                userListView.getItems().addAll(availableToInvite);
            } else {
                String lowerSearch = newVal.toLowerCase();
                availableToInvite.stream()
                        .filter(u -> u.getFullName().toLowerCase().contains(lowerSearch) ||
                                (u.getDepartment() != null && u.getDepartment().toLowerCase().contains(lowerSearch)))
                        .forEach(userListView.getItems()::add);
            }
        });

        Label countLabel = new Label(availableToInvite.size() + " users available");
        countLabel.setStyle("-fx-text-fill: #888; -fx-font-size: 11px;");

        content.getChildren().addAll(searchField, userListView, countLabel);
        inviteDialog.getDialogPane().setContent(content);

        inviteDialog.setResultConverter(buttonType -> {
            if (buttonType == ButtonType.OK && !selectedUserIds.isEmpty()) {
                return new ArrayList<>(selectedUserIds);
            }
            return null;
        });

        inviteDialog.showAndWait().ifPresent(userIds -> {
            if (onInviteUsers != null && !userIds.isEmpty()) {
                onInviteUsers.accept(userIds);
            }
        });
    }

    public void setOnInviteUsers(Consumer<List<Long>> callback) {
        this.onInviteUsers = callback;
    }

    public void setAvailableUsersSupplier(Supplier<List<TalkUserDTO>> supplier) {
        this.availableUsersSupplier = supplier;
    }

    public void setOnRemoveUser(Consumer<Long> callback) {
        this.onRemoveUser = callback;
    }

    public void setOnStartDM(Consumer<TalkUserDTO> callback) {
        this.onStartDM = callback;
    }

    /**
     * Custom cell for member list
     */
    private class MemberCell extends ListCell<TalkUserDTO> {
        @Override
        protected void updateItem(TalkUserDTO user, boolean empty) {
            super.updateItem(user, empty);

            if (empty || user == null) {
                setGraphic(null);
                setText(null);
            } else {
                HBox container = new HBox(10);
                container.setAlignment(Pos.CENTER_LEFT);
                container.getStyleClass().add("member-card");
                container.setPadding(new Insets(10));

                // Status indicator
                javafx.scene.layout.Region statusDot = new javafx.scene.layout.Region();
                statusDot.getStyleClass().addAll("user-status-indicator", getStatusClass(user.getStatus()));
                statusDot.setMinSize(10, 10);
                statusDot.setMaxSize(10, 10);

                // Name and role
                VBox nameBox = new VBox(4);
                HBox.setHgrow(nameBox, Priority.ALWAYS);

                Label nameLabel = new Label(user.getFullName());
                nameLabel.getStyleClass().add("user-name");

                // Show "(You)" indicator for current user
                if (currentUserId != null && currentUserId.equals(user.getId())) {
                    nameLabel.setText(user.getFullName() + " (You)");
                }

                HBox roleBox = new HBox(8);
                if (user.getRole() != null) {
                    Label roleLabel = new Label(user.getRole());
                    roleLabel.getStyleClass().add("user-role-badge-text");
                    roleBox.getChildren().add(roleLabel);
                }
                if (user.getDepartment() != null) {
                    Label deptLabel = new Label(user.getDepartment());
                    deptLabel.getStyleClass().add("user-role-badge-text");
                    roleBox.getChildren().add(deptLabel);
                }

                nameBox.getChildren().addAll(nameLabel, roleBox);

                // Action buttons
                HBox actionBox = new HBox(6);

                // DM button (not for self)
                if (currentUserId == null || !currentUserId.equals(user.getId())) {
                    Button dmBtn = new Button("\uD83D\uDCAC");
                    dmBtn.getStyleClass().add("member-action-button");
                    dmBtn.setTooltip(new Tooltip("Send Direct Message"));
                    dmBtn.setOnAction(e -> {
                        if (onStartDM != null) onStartDM.accept(user);
                    });
                    actionBox.getChildren().add(dmBtn);

                    // Remove button (for private/group channels, not for self)
                    if (onRemoveUser != null &&
                            ("PRIVATE".equals(channel.getChannelType()) || "GROUP".equals(channel.getChannelType()))) {
                        Button removeBtn = new Button("\u2715");
                        removeBtn.getStyleClass().addAll("member-action-button", "member-action-button-danger");
                        removeBtn.setTooltip(new Tooltip("Remove from channel"));
                        removeBtn.setOnAction(e -> {
                            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
                            confirm.setTitle("Remove Member");
                            confirm.setHeaderText("Remove " + user.getFullName() + "?");
                            confirm.setContentText("This will remove them from the channel.");
                            confirm.showAndWait().ifPresent(response -> {
                                if (response == ButtonType.OK && onRemoveUser != null) {
                                    onRemoveUser.accept(user.getId());
                                }
                            });
                        });
                        actionBox.getChildren().add(removeBtn);
                    }
                }

                container.getChildren().addAll(statusDot, nameBox, actionBox);
                setGraphic(container);
            }
        }

        private String getStatusClass(String status) {
            if (status == null) return "user-status-offline";
            return switch (status) {
                case "ONLINE" -> "user-status-online";
                case "AWAY" -> "user-status-away";
                case "BUSY", "IN_CLASS", "IN_MEETING" -> "user-status-busy";
                default -> "user-status-offline";
            };
        }
    }
}
