package com.heronix.teacher.ui.dialog;

import com.heronix.teacher.model.dto.talk.TalkChannelInvitationDTO;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import lombok.Getter;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Dialog for displaying and managing pending channel invitations
 */
public class InvitationDialog extends Dialog<Void> {

    private final VBox invitationContainer;
    private final Label emptyLabel;
    private final List<TalkChannelInvitationDTO> invitations;

    @Getter
    private Consumer<TalkChannelInvitationDTO> onAccept;
    @Getter
    private Consumer<TalkChannelInvitationDTO> onDecline;

    public InvitationDialog(List<TalkChannelInvitationDTO> pendingInvitations) {
        this.invitations = new ArrayList<>(pendingInvitations);

        setTitle("Channel Invitations");
        setHeaderText("You have pending channel invitations");

        // Dialog button
        getDialogPane().getButtonTypes().addAll(ButtonType.CLOSE);

        // Style the dialog pane
        getDialogPane().getStyleClass().add("dialog-container");

        // Main content
        VBox content = new VBox(15);
        content.getStyleClass().add("dialog-content");
        content.setPadding(new Insets(20));
        content.setPrefWidth(500);
        content.setMinHeight(200);
        content.setMaxHeight(500);

        // Scrollable invitation list
        ScrollPane scrollPane = new ScrollPane();
        scrollPane.setFitToWidth(true);
        scrollPane.setPrefHeight(400);
        scrollPane.getStyleClass().add("chat-scroll-pane");

        invitationContainer = new VBox(12);
        invitationContainer.setPadding(new Insets(8));

        emptyLabel = new Label("No pending invitations");
        emptyLabel.getStyleClass().addAll("empty-state-message");
        emptyLabel.setAlignment(Pos.CENTER);

        scrollPane.setContent(invitationContainer);
        content.getChildren().add(scrollPane);
        VBox.setVgrow(scrollPane, Priority.ALWAYS);

        getDialogPane().setContent(content);

        // Populate invitations
        refreshInvitations();
    }

    /**
     * Set the accept callback
     */
    public void setOnAccept(Consumer<TalkChannelInvitationDTO> callback) {
        this.onAccept = callback;
        refreshInvitations();
    }

    /**
     * Set the decline callback
     */
    public void setOnDecline(Consumer<TalkChannelInvitationDTO> callback) {
        this.onDecline = callback;
        refreshInvitations();
    }

    /**
     * Update the invitations list
     */
    public void updateInvitations(List<TalkChannelInvitationDTO> newInvitations) {
        this.invitations.clear();
        this.invitations.addAll(newInvitations);
        refreshInvitations();
    }

    /**
     * Remove an invitation from the list (after accept/decline)
     */
    public void removeInvitation(Long invitationId) {
        invitations.removeIf(inv -> inv.getId().equals(invitationId));
        refreshInvitations();
    }

    private void refreshInvitations() {
        invitationContainer.getChildren().clear();

        if (invitations.isEmpty()) {
            invitationContainer.getChildren().add(emptyLabel);
            return;
        }

        for (TalkChannelInvitationDTO invitation : invitations) {
            invitationContainer.getChildren().add(createInvitationCard(invitation));
        }
    }

    private VBox createInvitationCard(TalkChannelInvitationDTO invitation) {
        VBox card = new VBox(8);
        card.setPadding(new Insets(16));
        card.getStyleClass().add("invitation-card");

        // Header with channel icon and name
        HBox header = new HBox(10);
        header.setAlignment(Pos.CENTER_LEFT);

        Label iconLabel = new Label(invitation.getDisplayIcon());
        iconLabel.setStyle("-fx-font-size: 20px;");

        Label channelLabel = new Label(invitation.getChannelName());
        channelLabel.getStyleClass().add("invitation-channel-name");

        Label typeLabel = new Label("(" + formatChannelType(invitation.getChannelType()) + ")");
        typeLabel.getStyleClass().add("text-muted");

        header.getChildren().addAll(iconLabel, channelLabel, typeLabel);

        // Description if present
        VBox details = new VBox(6);
        if (invitation.getChannelDescription() != null && !invitation.getChannelDescription().isEmpty()) {
            Label descLabel = new Label(invitation.getChannelDescription());
            descLabel.getStyleClass().add("message-content");
            descLabel.setWrapText(true);
            details.getChildren().add(descLabel);
        }

        // Inviter info
        Label inviterLabel = new Label("Invited by: " + invitation.getInviterName());
        inviterLabel.getStyleClass().add("invitation-inviter");
        details.getChildren().add(inviterLabel);

        // Custom message if present
        if (invitation.getMessage() != null && !invitation.getMessage().isEmpty()) {
            Label messageLabel = new Label("\"" + invitation.getMessage() + "\"");
            messageLabel.getStyleClass().add("invitation-message");
            messageLabel.setWrapText(true);
            details.getChildren().add(messageLabel);
        }

        // Time info
        String timeStr = invitation.getCreatedAt() != null
                ? invitation.getCreatedAt().format(DateTimeFormatter.ofPattern("MMM d, h:mm a"))
                : "Unknown";
        Label timeLabel = new Label("Received: " + timeStr);
        timeLabel.getStyleClass().add("message-timestamp");
        details.getChildren().add(timeLabel);

        // Member count
        Label memberCountLabel = new Label(invitation.getChannelMemberCount() + " members");
        memberCountLabel.getStyleClass().add("message-timestamp");
        details.getChildren().add(memberCountLabel);

        // Action buttons
        HBox buttons = new HBox(10);
        buttons.setAlignment(Pos.CENTER_RIGHT);
        buttons.setPadding(new Insets(10, 0, 0, 0));

        Button declineBtn = new Button("Decline");
        declineBtn.getStyleClass().add("invitation-decline-button");
        declineBtn.setOnAction(e -> {
            if (onDecline != null) {
                onDecline.accept(invitation);
            }
        });

        Button acceptBtn = new Button("Accept");
        acceptBtn.getStyleClass().add("invitation-accept-button");
        acceptBtn.setOnAction(e -> {
            if (onAccept != null) {
                onAccept.accept(invitation);
            }
        });

        buttons.getChildren().addAll(declineBtn, acceptBtn);

        card.getChildren().addAll(header, details, buttons);
        return card;
    }

    private String formatChannelType(String type) {
        if (type == null) return "Channel";
        return switch (type) {
            case "PRIVATE" -> "Private";
            case "GROUP", "GROUP_MESSAGE" -> "Group";
            case "DEPARTMENT" -> "Department";
            case "ANNOUNCEMENT" -> "Announcements";
            case "PUBLIC" -> "Public";
            default -> "Channel";
        };
    }

    /**
     * Check if there are any pending invitations
     */
    public boolean hasInvitations() {
        return !invitations.isEmpty();
    }

    /**
     * Get the count of pending invitations
     */
    public int getInvitationCount() {
        return invitations.size();
    }
}
