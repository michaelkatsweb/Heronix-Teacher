package com.heronix.teacher.ui.dialog;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import lombok.Getter;

/**
 * Dialog for configuring chat and notification settings.
 * Allows users to customize their communication experience.
 */
public class ChatSettingsDialog extends Dialog<ChatSettingsDialog.ChatSettings> {

    // Notification settings
    private final CheckBox enableSoundCheckBox;
    private final CheckBox enableDesktopNotificationsCheckBox;
    private final CheckBox notifyOnMentionCheckBox;
    private final CheckBox notifyOnDirectMessageCheckBox;
    private final CheckBox notifyOnChannelMessageCheckBox;

    // Display settings
    private final ComboBox<String> messageDensityComboBox;
    private final ComboBox<String> timestampFormatComboBox;
    private final CheckBox showAvatarsCheckBox;
    private final CheckBox showTypingIndicatorCheckBox;
    private final CheckBox showReadReceiptsCheckBox;

    // Behavior settings
    private final CheckBox enterToSendCheckBox;
    private final CheckBox markAsReadOnScrollCheckBox;
    private final Spinner<Integer> messageLoadCountSpinner;

    public ChatSettingsDialog(ChatSettings currentSettings) {
        setTitle("Chat Settings");
        setHeaderText("Customize your chat experience");

        // Dialog buttons
        ButtonType saveButtonType = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);
        getDialogPane().getButtonTypes().addAll(saveButtonType, ButtonType.CANCEL);

        // Style the dialog
        getDialogPane().getStyleClass().add("dialog-container");

        // Main content with tabs
        TabPane tabPane = new TabPane();
        tabPane.getStyleClass().add("settings-tab-pane");
        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

        // ===== Notifications Tab =====
        Tab notificationsTab = new Tab("Notifications");
        notificationsTab.setGraphic(new Label("\uD83D\uDD14")); // Bell icon

        VBox notificationsContent = new VBox(16);
        notificationsContent.setPadding(new Insets(20));
        notificationsContent.getStyleClass().add("settings-tab-content");

        // Sound section
        Label soundSectionLabel = new Label("Sound Notifications");
        soundSectionLabel.getStyleClass().add("settings-section-title");

        enableSoundCheckBox = new CheckBox("Enable notification sounds");
        enableSoundCheckBox.setSelected(currentSettings != null && currentSettings.isSoundEnabled());
        enableSoundCheckBox.getStyleClass().add("settings-checkbox");

        VBox soundSection = new VBox(8);
        soundSection.getChildren().addAll(soundSectionLabel, enableSoundCheckBox);

        // Desktop notifications section
        Label desktopSectionLabel = new Label("Desktop Notifications");
        desktopSectionLabel.getStyleClass().add("settings-section-title");

        enableDesktopNotificationsCheckBox = new CheckBox("Show desktop notifications");
        enableDesktopNotificationsCheckBox.setSelected(currentSettings != null && currentSettings.isDesktopNotificationsEnabled());
        enableDesktopNotificationsCheckBox.getStyleClass().add("settings-checkbox");

        VBox desktopSection = new VBox(8);
        desktopSection.getChildren().addAll(desktopSectionLabel, enableDesktopNotificationsCheckBox);

        // Notify when section
        Label notifyWhenLabel = new Label("Notify me when...");
        notifyWhenLabel.getStyleClass().add("settings-section-title");

        notifyOnMentionCheckBox = new CheckBox("Someone mentions me (@mention)");
        notifyOnMentionCheckBox.setSelected(currentSettings == null || currentSettings.isNotifyOnMention());
        notifyOnMentionCheckBox.getStyleClass().add("settings-checkbox");

        notifyOnDirectMessageCheckBox = new CheckBox("I receive a direct message");
        notifyOnDirectMessageCheckBox.setSelected(currentSettings == null || currentSettings.isNotifyOnDirectMessage());
        notifyOnDirectMessageCheckBox.getStyleClass().add("settings-checkbox");

        notifyOnChannelMessageCheckBox = new CheckBox("New message in subscribed channels");
        notifyOnChannelMessageCheckBox.setSelected(currentSettings != null && currentSettings.isNotifyOnChannelMessage());
        notifyOnChannelMessageCheckBox.getStyleClass().add("settings-checkbox");

        VBox notifyWhenSection = new VBox(8);
        notifyWhenSection.getChildren().addAll(
                notifyWhenLabel,
                notifyOnMentionCheckBox,
                notifyOnDirectMessageCheckBox,
                notifyOnChannelMessageCheckBox
        );

        notificationsContent.getChildren().addAll(soundSection, new Separator(), desktopSection, new Separator(), notifyWhenSection);
        notificationsTab.setContent(notificationsContent);

        // ===== Display Tab =====
        Tab displayTab = new Tab("Display");
        displayTab.setGraphic(new Label("\uD83D\uDDA5")); // Desktop icon

        VBox displayContent = new VBox(16);
        displayContent.setPadding(new Insets(20));
        displayContent.getStyleClass().add("settings-tab-content");

        // Message display section
        Label displaySectionLabel = new Label("Message Display");
        displaySectionLabel.getStyleClass().add("settings-section-title");

        GridPane displayGrid = new GridPane();
        displayGrid.setHgap(12);
        displayGrid.setVgap(12);

        Label densityLabel = new Label("Message density:");
        messageDensityComboBox = new ComboBox<>();
        messageDensityComboBox.getItems().addAll("Compact", "Cozy", "Comfortable");
        messageDensityComboBox.setValue(currentSettings != null ? currentSettings.getMessageDensity() : "Cozy");
        messageDensityComboBox.setPrefWidth(150);
        displayGrid.add(densityLabel, 0, 0);
        displayGrid.add(messageDensityComboBox, 1, 0);

        Label timestampLabel = new Label("Timestamp format:");
        timestampFormatComboBox = new ComboBox<>();
        timestampFormatComboBox.getItems().addAll("12-hour (3:45 PM)", "24-hour (15:45)", "Relative (2 mins ago)");
        timestampFormatComboBox.setValue(currentSettings != null ? currentSettings.getTimestampFormat() : "12-hour (3:45 PM)");
        timestampFormatComboBox.setPrefWidth(150);
        displayGrid.add(timestampLabel, 0, 1);
        displayGrid.add(timestampFormatComboBox, 1, 1);

        VBox displaySection = new VBox(12);
        displaySection.getChildren().addAll(displaySectionLabel, displayGrid);

        // Visual elements section
        Label visualSectionLabel = new Label("Visual Elements");
        visualSectionLabel.getStyleClass().add("settings-section-title");

        showAvatarsCheckBox = new CheckBox("Show user avatars");
        showAvatarsCheckBox.setSelected(currentSettings == null || currentSettings.isShowAvatars());
        showAvatarsCheckBox.getStyleClass().add("settings-checkbox");

        showTypingIndicatorCheckBox = new CheckBox("Show typing indicators");
        showTypingIndicatorCheckBox.setSelected(currentSettings == null || currentSettings.isShowTypingIndicator());
        showTypingIndicatorCheckBox.getStyleClass().add("settings-checkbox");

        showReadReceiptsCheckBox = new CheckBox("Show read receipts");
        showReadReceiptsCheckBox.setSelected(currentSettings == null || currentSettings.isShowReadReceipts());
        showReadReceiptsCheckBox.getStyleClass().add("settings-checkbox");

        VBox visualSection = new VBox(8);
        visualSection.getChildren().addAll(
                visualSectionLabel,
                showAvatarsCheckBox,
                showTypingIndicatorCheckBox,
                showReadReceiptsCheckBox
        );

        displayContent.getChildren().addAll(displaySection, new Separator(), visualSection);
        displayTab.setContent(displayContent);

        // ===== Behavior Tab =====
        Tab behaviorTab = new Tab("Behavior");
        behaviorTab.setGraphic(new Label("\u2699")); // Gear icon

        VBox behaviorContent = new VBox(16);
        behaviorContent.setPadding(new Insets(20));
        behaviorContent.getStyleClass().add("settings-tab-content");

        // Input behavior section
        Label inputSectionLabel = new Label("Input Behavior");
        inputSectionLabel.getStyleClass().add("settings-section-title");

        enterToSendCheckBox = new CheckBox("Press Enter to send message (Shift+Enter for new line)");
        enterToSendCheckBox.setSelected(currentSettings == null || currentSettings.isEnterToSend());
        enterToSendCheckBox.getStyleClass().add("settings-checkbox");

        VBox inputSection = new VBox(8);
        inputSection.getChildren().addAll(inputSectionLabel, enterToSendCheckBox);

        // Reading behavior section
        Label readingSectionLabel = new Label("Reading Behavior");
        readingSectionLabel.getStyleClass().add("settings-section-title");

        markAsReadOnScrollCheckBox = new CheckBox("Mark messages as read when scrolled past");
        markAsReadOnScrollCheckBox.setSelected(currentSettings == null || currentSettings.isMarkAsReadOnScroll());
        markAsReadOnScrollCheckBox.getStyleClass().add("settings-checkbox");

        HBox loadCountBox = new HBox(10);
        loadCountBox.setAlignment(Pos.CENTER_LEFT);
        Label loadCountLabel = new Label("Messages to load per page:");
        messageLoadCountSpinner = new Spinner<>(10, 100, currentSettings != null ? currentSettings.getMessageLoadCount() : 50, 10);
        messageLoadCountSpinner.setPrefWidth(80);
        messageLoadCountSpinner.setEditable(false);
        loadCountBox.getChildren().addAll(loadCountLabel, messageLoadCountSpinner);

        VBox readingSection = new VBox(12);
        readingSection.getChildren().addAll(readingSectionLabel, markAsReadOnScrollCheckBox, loadCountBox);

        behaviorContent.getChildren().addAll(inputSection, new Separator(), readingSection);
        behaviorTab.setContent(behaviorContent);

        // Add tabs
        tabPane.getTabs().addAll(notificationsTab, displayTab, behaviorTab);
        tabPane.setPrefWidth(450);
        tabPane.setPrefHeight(400);

        getDialogPane().setContent(tabPane);

        // Style the save button
        Button saveButton = (Button) getDialogPane().lookupButton(saveButtonType);
        saveButton.getStyleClass().add("button-primary");

        // Convert result
        setResultConverter(dialogButton -> {
            if (dialogButton == saveButtonType) {
                return new ChatSettings(
                        enableSoundCheckBox.isSelected(),
                        enableDesktopNotificationsCheckBox.isSelected(),
                        notifyOnMentionCheckBox.isSelected(),
                        notifyOnDirectMessageCheckBox.isSelected(),
                        notifyOnChannelMessageCheckBox.isSelected(),
                        messageDensityComboBox.getValue(),
                        timestampFormatComboBox.getValue(),
                        showAvatarsCheckBox.isSelected(),
                        showTypingIndicatorCheckBox.isSelected(),
                        showReadReceiptsCheckBox.isSelected(),
                        enterToSendCheckBox.isSelected(),
                        markAsReadOnScrollCheckBox.isSelected(),
                        messageLoadCountSpinner.getValue()
                );
            }
            return null;
        });
    }

    /**
     * Chat settings data class
     */
    @Getter
    public static class ChatSettings {
        private final boolean soundEnabled;
        private final boolean desktopNotificationsEnabled;
        private final boolean notifyOnMention;
        private final boolean notifyOnDirectMessage;
        private final boolean notifyOnChannelMessage;
        private final String messageDensity;
        private final String timestampFormat;
        private final boolean showAvatars;
        private final boolean showTypingIndicator;
        private final boolean showReadReceipts;
        private final boolean enterToSend;
        private final boolean markAsReadOnScroll;
        private final int messageLoadCount;

        public ChatSettings(
                boolean soundEnabled,
                boolean desktopNotificationsEnabled,
                boolean notifyOnMention,
                boolean notifyOnDirectMessage,
                boolean notifyOnChannelMessage,
                String messageDensity,
                String timestampFormat,
                boolean showAvatars,
                boolean showTypingIndicator,
                boolean showReadReceipts,
                boolean enterToSend,
                boolean markAsReadOnScroll,
                int messageLoadCount
        ) {
            this.soundEnabled = soundEnabled;
            this.desktopNotificationsEnabled = desktopNotificationsEnabled;
            this.notifyOnMention = notifyOnMention;
            this.notifyOnDirectMessage = notifyOnDirectMessage;
            this.notifyOnChannelMessage = notifyOnChannelMessage;
            this.messageDensity = messageDensity;
            this.timestampFormat = timestampFormat;
            this.showAvatars = showAvatars;
            this.showTypingIndicator = showTypingIndicator;
            this.showReadReceipts = showReadReceipts;
            this.enterToSend = enterToSend;
            this.markAsReadOnScroll = markAsReadOnScroll;
            this.messageLoadCount = messageLoadCount;
        }

        /**
         * Create default settings
         */
        public static ChatSettings getDefault() {
            return new ChatSettings(
                    true,   // soundEnabled
                    true,   // desktopNotificationsEnabled
                    true,   // notifyOnMention
                    true,   // notifyOnDirectMessage
                    false,  // notifyOnChannelMessage
                    "Cozy", // messageDensity
                    "12-hour (3:45 PM)", // timestampFormat
                    true,   // showAvatars
                    true,   // showTypingIndicator
                    true,   // showReadReceipts
                    true,   // enterToSend
                    true,   // markAsReadOnScroll
                    50      // messageLoadCount
            );
        }
    }
}
