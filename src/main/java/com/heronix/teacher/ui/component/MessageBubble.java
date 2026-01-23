package com.heronix.teacher.ui.component;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.heronix.teacher.model.dto.talk.TalkMessageDTO;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import lombok.extern.slf4j.Slf4j;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Enhanced message bubble component with support for:
 * - Reactions
 * - Replies
 * - Edit/Delete
 * - Pin
 * - Context menu
 *
 * Uses CSS classes for theme-aware styling.
 */
@Slf4j
public class MessageBubble extends VBox {

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("h:mm a");
    private static final String[] COMMON_REACTIONS = {"\uD83D\uDC4D", "\u2764\uFE0F", "\uD83D\uDE02", "\uD83D\uDE2E", "\uD83D\uDE22", "\uD83D\uDE4F"};
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final TalkMessageDTO message;
    private final boolean isOwnMessage;
    private final Long currentUserId;

    // Callbacks
    private Consumer<TalkMessageDTO> onReply;
    private BiConsumer<TalkMessageDTO, String> onReaction;
    private Consumer<TalkMessageDTO> onEdit;
    private Consumer<TalkMessageDTO> onDelete;
    private BiConsumer<TalkMessageDTO, Boolean> onPin;
    private Consumer<TalkMessageDTO> onViewReplies;
    private Consumer<TalkMessageDTO> onForward;

    // UI components
    private final HBox reactionsBar;
    private final Label replyPreview;
    private final HBox hoverActionBar;

    public MessageBubble(TalkMessageDTO message, boolean isOwnMessage, Long currentUserId) {
        this.message = message;
        this.isOwnMessage = isOwnMessage;
        this.currentUserId = currentUserId;

        setSpacing(4);
        setPadding(new Insets(10, 14, 10, 14));
        setMaxWidth(500);

        // Apply CSS classes for theme-aware styling
        getStyleClass().add("message-bubble");
        if (isOwnMessage) {
            getStyleClass().add("message-bubble-own");
            setAlignment(Pos.CENTER_RIGHT);
        } else {
            setAlignment(Pos.CENTER_LEFT);
        }

        // Create hover action bar (initially hidden)
        hoverActionBar = createHoverActionBar();
        hoverActionBar.setVisible(false);
        hoverActionBar.setManaged(false);

        // Reply preview (if this is a reply)
        replyPreview = new Label();
        if (message.getReplyToId() != null && message.getReplyToPreview() != null) {
            String preview = truncateText(message.getReplyToPreview(), 80);
            replyPreview.setText("\u21A9 " + message.getReplyToSenderName() + ": " + preview);
            replyPreview.getStyleClass().addAll("reply-preview-content");
            replyPreview.setWrapText(true);
            replyPreview.setMaxWidth(400);

            HBox replyContainer = new HBox(replyPreview);
            replyContainer.getStyleClass().add("thread-indicator");
            replyContainer.setPadding(new Insets(4, 8, 4, 8));
            getChildren().add(replyContainer);
        }

        // Sender name (only for non-own messages)
        if (!isOwnMessage) {
            Label senderLabel = new Label(message.getSenderName());
            senderLabel.getStyleClass().add("message-sender");
            getChildren().add(senderLabel);
        }

        // Message content
        if (message.isDeleted()) {
            Label deletedLabel = new Label("\uD83D\uDEAB This message was deleted");
            deletedLabel.getStyleClass().addAll("message-content", "text-muted");
            deletedLabel.setStyle("-fx-font-style: italic;");
            getChildren().add(deletedLabel);
        } else {
            TextFlow contentFlow = new TextFlow();
            Text contentText = new Text(message.getContent());
            contentText.getStyleClass().add("message-content");
            contentFlow.getChildren().add(contentText);
            contentFlow.setMaxWidth(480);
            getChildren().add(contentFlow);

            // Edited indicator
            if (message.isEdited()) {
                Label editedLabel = new Label("(edited)");
                editedLabel.getStyleClass().add("message-edited-indicator");
                getChildren().add(editedLabel);
            }
        }

        // Attachment indicator
        if (message.getAttachmentName() != null && !message.getAttachmentName().isEmpty()) {
            HBox attachmentBox = createAttachmentIndicator();
            getChildren().add(attachmentBox);
        }

        // Reactions bar
        reactionsBar = new HBox(4);
        reactionsBar.setAlignment(Pos.CENTER_LEFT);
        reactionsBar.getStyleClass().add("reactions-container");
        updateReactionsDisplay();
        getChildren().add(reactionsBar);

        // Time and status row
        HBox statusRow = new HBox(8);
        statusRow.setAlignment(isOwnMessage ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);

        Label timeLabel = new Label(message.getTimestamp() != null ?
                message.getTimestamp().format(TIME_FORMAT) : "");
        timeLabel.getStyleClass().add("message-timestamp");
        statusRow.getChildren().add(timeLabel);

        // Read receipt indicator (only for own messages)
        if (isOwnMessage && !message.isDeleted()) {
            Label receiptLabel = new Label(getReadReceiptIcon(message.getStatus()));
            receiptLabel.getStyleClass().add("read-receipt-indicator");
            // Add status-specific class for color styling
            String statusClass = getReadReceiptStyleClass(message.getStatus());
            if (statusClass != null) {
                receiptLabel.getStyleClass().add(statusClass);
            }
            receiptLabel.setTooltip(new Tooltip(getReadReceiptTooltip(message.getStatus())));
            statusRow.getChildren().add(receiptLabel);
        }

        // Pin indicator
        if (message.isPinned()) {
            Label pinnedLabel = new Label("\uD83D\uDCCC");
            pinnedLabel.getStyleClass().add("pinned-indicator");
            statusRow.getChildren().add(pinnedLabel);
        }

        // Reply count
        if (message.getReplyCount() > 0) {
            Label repliesLabel = new Label(message.getReplyCount() + " replies");
            repliesLabel.getStyleClass().add("thread-indicator-text");
            repliesLabel.setOnMouseClicked(e -> {
                if (onViewReplies != null) onViewReplies.accept(message);
            });
            statusRow.getChildren().add(repliesLabel);
        }

        getChildren().add(statusRow);

        // Add hover action bar at the top
        if (!message.isDeleted()) {
            getChildren().add(0, hoverActionBar);
        }

        // Show/hide action bar on hover
        setOnMouseEntered(e -> {
            if (!message.isDeleted()) {
                hoverActionBar.setVisible(true);
                hoverActionBar.setManaged(true);
            }
        });
        setOnMouseExited(e -> {
            hoverActionBar.setVisible(false);
            hoverActionBar.setManaged(false);
        });

        // Context menu
        setupContextMenu();
    }

    /**
     * Create the hover action bar with quick action buttons
     */
    private HBox createHoverActionBar() {
        HBox actionBar = new HBox(2);
        actionBar.setAlignment(isOwnMessage ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
        actionBar.getStyleClass().add("message-hover-actions");
        actionBar.setPadding(new Insets(0, 0, 4, 0));

        // Reply button
        Button replyBtn = new Button("\u21A9");
        replyBtn.getStyleClass().add("message-action-button");
        replyBtn.setTooltip(new Tooltip("Reply"));
        replyBtn.setOnAction(e -> {
            if (onReply != null) onReply.accept(message);
        });

        // React button
        Button reactBtn = new Button("\uD83D\uDE00");
        reactBtn.getStyleClass().add("message-action-button");
        reactBtn.setTooltip(new Tooltip("Add reaction"));
        reactBtn.setOnAction(e -> showReactionPicker());

        // Add buttons in order
        actionBar.getChildren().addAll(replyBtn, reactBtn);

        // Edit button (only for own messages)
        if (isOwnMessage) {
            Button editBtn = new Button("\u270F");
            editBtn.getStyleClass().add("message-action-button");
            editBtn.setTooltip(new Tooltip("Edit message"));
            editBtn.setOnAction(e -> {
                if (onEdit != null) onEdit.accept(message);
            });
            actionBar.getChildren().add(editBtn);

            Button deleteBtn = new Button("\uD83D\uDDD1");
            deleteBtn.getStyleClass().add("message-action-button");
            deleteBtn.getStyleClass().add("message-action-delete");
            deleteBtn.setTooltip(new Tooltip("Delete message"));
            deleteBtn.setOnAction(e -> {
                if (onDelete != null) onDelete.accept(message);
            });
            actionBar.getChildren().add(deleteBtn);
        }

        // More options button
        Button moreBtn = new Button("\u22EE");
        moreBtn.getStyleClass().add("message-action-button");
        moreBtn.setTooltip(new Tooltip("More options"));
        moreBtn.setOnAction(e -> {
            // Trigger context menu
            ContextMenu contextMenu = buildContextMenu();
            contextMenu.show(moreBtn, javafx.geometry.Side.BOTTOM, 0, 0);
        });
        actionBar.getChildren().add(moreBtn);

        return actionBar;
    }

    /**
     * Build context menu for the more options button
     */
    private ContextMenu buildContextMenu() {
        ContextMenu contextMenu = new ContextMenu();

        // Pin/Unpin
        MenuItem pinItem = new MenuItem(message.isPinned() ? "\uD83D\uDCCC Unpin" : "\uD83D\uDCCC Pin");
        pinItem.setOnAction(e -> {
            if (onPin != null) onPin.accept(message, !message.isPinned());
        });
        contextMenu.getItems().add(pinItem);

        // Copy
        MenuItem copyItem = new MenuItem("\uD83D\uDCCB Copy");
        copyItem.setOnAction(e -> {
            javafx.scene.input.Clipboard clipboard = javafx.scene.input.Clipboard.getSystemClipboard();
            javafx.scene.input.ClipboardContent content = new javafx.scene.input.ClipboardContent();
            content.putString(message.getContent());
            clipboard.setContent(content);
        });
        contextMenu.getItems().add(copyItem);

        // Forward
        MenuItem forwardItem = new MenuItem("\u27A1 Forward");
        forwardItem.setOnAction(e -> {
            if (onForward != null) onForward.accept(message);
        });
        contextMenu.getItems().add(forwardItem);

        return contextMenu;
    }

    private HBox createAttachmentIndicator() {
        HBox box = new HBox(6);
        box.setAlignment(Pos.CENTER_LEFT);
        box.getStyleClass().add("attachment-container");
        box.setPadding(new Insets(6));

        String icon = getAttachmentIcon(message.getAttachmentType());
        Label iconLabel = new Label(icon);
        iconLabel.getStyleClass().add("attachment-icon");

        VBox info = new VBox(2);
        Label nameLabel = new Label(message.getAttachmentName());
        nameLabel.getStyleClass().add("attachment-name");

        String sizeStr = message.getAttachmentSize() != null ?
                formatFileSize(message.getAttachmentSize()) : "";
        Label sizeLabel = new Label(sizeStr);
        sizeLabel.getStyleClass().add("attachment-size");

        info.getChildren().addAll(nameLabel, sizeLabel);
        box.getChildren().addAll(iconLabel, info);

        return box;
    }

    private String getAttachmentIcon(String type) {
        if (type == null) return "\uD83D\uDCC4";
        if (type.startsWith("image/")) return "\uD83D\uDDBC";
        if (type.startsWith("video/")) return "\uD83C\uDFA5";
        if (type.startsWith("audio/")) return "\uD83C\uDFB5";
        if (type.contains("pdf")) return "\uD83D\uDCC4";
        if (type.contains("word") || type.contains("document")) return "\uD83D\uDCC4";
        if (type.contains("excel") || type.contains("spreadsheet")) return "\uD83D\uDCCA";
        return "\uD83D\uDCC1";
    }

    private String formatFileSize(Long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return (bytes / 1024) + " KB";
        return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
    }

    private void updateReactionsDisplay() {
        reactionsBar.getChildren().clear();

        String reactionsJson = message.getReactions();
        if (reactionsJson == null || reactionsJson.isEmpty()) return;

        // Parse reactions using Jackson (format: {"emoji": [userId1, userId2], ...})
        try {
            Map<String, List<Long>> reactions = objectMapper.readValue(
                    reactionsJson,
                    new TypeReference<Map<String, List<Long>>>() {}
            );

            for (Map.Entry<String, List<Long>> entry : reactions.entrySet()) {
                String emoji = entry.getKey();
                int count = entry.getValue() != null ? entry.getValue().size() : 0;
                if (count > 0 && !emoji.isEmpty()) {
                    addReactionBadge(emoji, count);
                }
            }
        } catch (Exception e) {
            log.debug("Failed to parse reactions JSON: {}", e.getMessage());
        }

        // Add reaction button
        if (!message.isDeleted()) {
            Button addReactionBtn = new Button("+");
            addReactionBtn.getStyleClass().add("message-action-button");
            addReactionBtn.setOnAction(e -> showReactionPicker());
            reactionsBar.getChildren().add(addReactionBtn);
        }
    }

    private void addReactionBadge(String emoji, int count) {
        HBox badge = new HBox(2);
        badge.setAlignment(Pos.CENTER);
        badge.getStyleClass().add("reaction-chip");
        badge.setPadding(new Insets(2, 6, 2, 6));

        Label emojiLabel = new Label(emoji);
        emojiLabel.getStyleClass().add("reaction-emoji");

        Label countLabel = new Label(String.valueOf(count));
        countLabel.getStyleClass().add("reaction-count");

        badge.getChildren().addAll(emojiLabel, countLabel);

        // Click to toggle reaction
        badge.setOnMouseClicked(e -> {
            if (onReaction != null) {
                onReaction.accept(message, emoji);
            }
        });

        reactionsBar.getChildren().add(badge);
    }

    private void showReactionPicker() {
        ContextMenu picker = new ContextMenu();
        picker.getStyleClass().add("mention-autocomplete");
        HBox emojiBox = new HBox(4);
        emojiBox.setPadding(new Insets(8));

        for (String emoji : COMMON_REACTIONS) {
            Button btn = new Button(emoji);
            btn.getStyleClass().add("message-action-button");
            btn.setStyle("-fx-font-size: 18px;");
            btn.setOnAction(e -> {
                picker.hide();
                if (onReaction != null) {
                    onReaction.accept(message, emoji);
                }
            });
            emojiBox.getChildren().add(btn);
        }

        CustomMenuItem item = new CustomMenuItem(emojiBox);
        item.setHideOnClick(false);
        picker.getItems().add(item);

        picker.show(this, javafx.geometry.Side.BOTTOM, 0, 0);
    }

    private void setupContextMenu() {
        ContextMenu contextMenu = new ContextMenu();

        // Reply
        MenuItem replyItem = new MenuItem("\u21A9 Reply");
        replyItem.setOnAction(e -> {
            if (onReply != null) onReply.accept(message);
        });
        contextMenu.getItems().add(replyItem);

        // Reaction submenu
        Menu reactionMenu = new Menu("\uD83D\uDE00 React");
        for (String emoji : COMMON_REACTIONS) {
            MenuItem emojiItem = new MenuItem(emoji);
            emojiItem.setOnAction(e -> {
                if (onReaction != null) onReaction.accept(message, emoji);
            });
            reactionMenu.getItems().add(emojiItem);
        }
        contextMenu.getItems().add(reactionMenu);

        contextMenu.getItems().add(new SeparatorMenuItem());

        // Pin/Unpin
        MenuItem pinItem = new MenuItem(message.isPinned() ? "\uD83D\uDCCC Unpin" : "\uD83D\uDCCC Pin");
        pinItem.setOnAction(e -> {
            if (onPin != null) onPin.accept(message, !message.isPinned());
        });
        contextMenu.getItems().add(pinItem);

        // Copy
        MenuItem copyItem = new MenuItem("\uD83D\uDCCB Copy");
        copyItem.setOnAction(e -> {
            javafx.scene.input.Clipboard clipboard = javafx.scene.input.Clipboard.getSystemClipboard();
            javafx.scene.input.ClipboardContent content = new javafx.scene.input.ClipboardContent();
            content.putString(message.getContent());
            clipboard.setContent(content);
        });
        contextMenu.getItems().add(copyItem);

        // Forward
        MenuItem forwardItem = new MenuItem("\u27A1 Forward");
        forwardItem.setOnAction(e -> {
            if (onForward != null) onForward.accept(message);
        });
        contextMenu.getItems().add(forwardItem);

        // Edit/Delete only for own messages
        if (isOwnMessage && !message.isDeleted()) {
            contextMenu.getItems().add(new SeparatorMenuItem());

            MenuItem editItem = new MenuItem("\u270F Edit");
            editItem.setOnAction(e -> {
                if (onEdit != null) onEdit.accept(message);
            });
            contextMenu.getItems().add(editItem);

            MenuItem deleteItem = new MenuItem("\uD83D\uDDD1 Delete");
            deleteItem.setStyle("-fx-text-fill: #F44336;");
            deleteItem.setOnAction(e -> {
                if (onDelete != null) onDelete.accept(message);
            });
            contextMenu.getItems().add(deleteItem);
        }

        setOnContextMenuRequested(e -> contextMenu.show(this, e.getScreenX(), e.getScreenY()));
    }

    // Setters for callbacks
    public void setOnReply(Consumer<TalkMessageDTO> callback) {
        this.onReply = callback;
    }

    public void setOnReaction(BiConsumer<TalkMessageDTO, String> callback) {
        this.onReaction = callback;
    }

    public void setOnEdit(Consumer<TalkMessageDTO> callback) {
        this.onEdit = callback;
    }

    public void setOnDelete(Consumer<TalkMessageDTO> callback) {
        this.onDelete = callback;
    }

    public void setOnPin(BiConsumer<TalkMessageDTO, Boolean> callback) {
        this.onPin = callback;
    }

    public void setOnViewReplies(Consumer<TalkMessageDTO> callback) {
        this.onViewReplies = callback;
    }

    public void setOnForward(Consumer<TalkMessageDTO> callback) {
        this.onForward = callback;
    }

    public TalkMessageDTO getMessage() {
        return message;
    }

    /**
     * Update reactions display when reactions change.
     * Serializes the reactions map to JSON and updates the message object.
     */
    public void updateReactions(Map<String, List<Long>> reactions) {
        if (reactions == null || reactions.isEmpty()) {
            message.setReactions(null);
        } else {
            try {
                String reactionsJson = objectMapper.writeValueAsString(reactions);
                message.setReactions(reactionsJson);
            } catch (Exception e) {
                log.error("Failed to serialize reactions to JSON", e);
            }
        }
        updateReactionsDisplay();
    }

    /**
     * Get the read receipt icon based on message status
     */
    private String getReadReceiptIcon(String status) {
        if (status == null) return "✓"; // Default to sent
        return switch (status.toUpperCase()) {
            case "READ" -> "✓✓"; // Double checkmark for read
            case "DELIVERED" -> "✓✓"; // Double checkmark for delivered
            case "SENT" -> "✓"; // Single checkmark for sent
            case "PENDING" -> "○"; // Circle for pending
            case "FAILED" -> "✗"; // X for failed
            default -> "✓";
        };
    }

    /**
     * Get tooltip text for read receipt status
     */
    private String getReadReceiptTooltip(String status) {
        if (status == null) return "Sent";
        return switch (status.toUpperCase()) {
            case "READ" -> "Read";
            case "DELIVERED" -> "Delivered";
            case "SENT" -> "Sent";
            case "PENDING" -> "Sending...";
            case "FAILED" -> "Failed to send";
            default -> "Sent";
        };
    }

    /**
     * Get CSS class for read receipt status coloring
     */
    private String getReadReceiptStyleClass(String status) {
        if (status == null) return null;
        return switch (status.toUpperCase()) {
            case "READ" -> "read";
            case "DELIVERED" -> "delivered";
            case "FAILED" -> "failed";
            default -> null;
        };
    }

    /**
     * Truncate text to a maximum length with ellipsis
     */
    private String truncateText(String text, int maxLength) {
        if (text == null) return "";
        if (text.length() <= maxLength) return text;
        return text.substring(0, maxLength - 3) + "...";
    }
}
