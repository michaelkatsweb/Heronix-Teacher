package com.heronix.teacher.ui.dialog;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.VBox;
import javafx.stage.Popup;
import javafx.stage.Window;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Simple emoji picker popup with basic commonly-used emojis.
 * Organized by category for easy selection.
 */
public class EmojiPickerPopup extends Popup {

    private Consumer<String> onEmojiSelected;

    // Basic emoji categories with common emojis
    private static final Map<String, String[]> EMOJI_CATEGORIES = new LinkedHashMap<>();

    static {
        EMOJI_CATEGORIES.put("😊 Smileys", new String[]{
                "😀", "😃", "😄", "😁", "😊", "🙂", "😉", "😍",
                "🥰", "😘", "😂", "🤣", "😅", "😆", "😎", "🤗",
                "🤔", "🤨", "😐", "😑", "😶", "🙄", "😏", "😒",
                "😔", "😢", "😭", "😤", "😠", "😡", "🤯", "😱"
        });

        EMOJI_CATEGORIES.put("👍 Gestures", new String[]{
                "👍", "👎", "👌", "✌️", "🤞", "🤝", "👏", "🙌",
                "👋", "🤚", "✋", "🖐️", "👆", "👇", "👈", "👉",
                "💪", "🙏", "✍️", "🤳", "💅", "🖖", "🤟", "🤙"
        });

        EMOJI_CATEGORIES.put("❤️ Symbols", new String[]{
                "❤️", "🧡", "💛", "💚", "💙", "💜", "🖤", "💔",
                "❣️", "💕", "💖", "💗", "💘", "💝", "💞", "💟",
                "⭐", "🌟", "✨", "💫", "🔥", "💯", "✅", "❌",
                "⚠️", "❗", "❓", "💡", "🔔", "📌", "🎯", "🏆"
        });

        EMOJI_CATEGORIES.put("🎉 Objects", new String[]{
                "🎉", "🎊", "🎁", "🎈", "🎂", "🍰", "☕", "🍕",
                "📚", "📝", "✏️", "📎", "📅", "🗓️", "⏰", "📧",
                "💻", "📱", "🖥️", "⌨️", "🖨️", "📷", "🎵", "🎶"
        });

        EMOJI_CATEGORIES.put("🏫 School", new String[]{
                "🏫", "📖", "📕", "📗", "📘", "📙", "📓", "📔",
                "🎒", "🔬", "🔭", "🧪", "🧮", "📐", "📏", "✂️",
                "🖍️", "🖌️", "🎨", "🎭", "🎪", "🏅", "🥇", "🥈"
        });
    }

    public EmojiPickerPopup() {
        setAutoHide(true);
        setAutoFix(true);

        VBox container = new VBox(8);
        container.setPadding(new Insets(12));
        container.getStyleClass().add("emoji-picker-container");
        container.setPrefWidth(320);
        container.setMaxHeight(350);

        // Title
        Label titleLabel = new Label("Select Emoji");
        titleLabel.getStyleClass().add("emoji-picker-title");

        // Scrollable content
        ScrollPane scrollPane = new ScrollPane();
        scrollPane.setFitToWidth(true);
        scrollPane.setPrefHeight(280);
        scrollPane.getStyleClass().add("emoji-picker-scroll");

        VBox categoriesBox = new VBox(10);
        categoriesBox.setPadding(new Insets(5));

        // Build each category
        for (Map.Entry<String, String[]> category : EMOJI_CATEGORIES.entrySet()) {
            VBox categoryBox = new VBox(4);

            Label categoryLabel = new Label(category.getKey());
            categoryLabel.getStyleClass().add("emoji-picker-category");

            FlowPane emojiGrid = new FlowPane(4, 4);
            emojiGrid.setAlignment(Pos.CENTER_LEFT);

            for (String emoji : category.getValue()) {
                Button emojiBtn = createEmojiButton(emoji);
                emojiGrid.getChildren().add(emojiBtn);
            }

            categoryBox.getChildren().addAll(categoryLabel, emojiGrid);
            categoriesBox.getChildren().add(categoryBox);
        }

        scrollPane.setContent(categoriesBox);
        container.getChildren().addAll(titleLabel, scrollPane);
        getContent().add(container);
    }

    private Button createEmojiButton(String emoji) {
        Button btn = new Button(emoji);
        btn.getStyleClass().add("emoji-button");
        btn.setPrefSize(32, 32);
        btn.setMinSize(32, 32);
        btn.setMaxSize(32, 32);

        btn.setOnAction(e -> {
            if (onEmojiSelected != null) {
                onEmojiSelected.accept(emoji);
            }
            hide();
        });

        btn.setTooltip(new Tooltip(emoji));
        return btn;
    }

    /**
     * Set the callback for when an emoji is selected
     */
    public void setOnEmojiSelected(Consumer<String> handler) {
        this.onEmojiSelected = handler;
    }

    /**
     * Show the popup near the given window at specified coordinates
     */
    public void showNear(Window owner, double x, double y) {
        show(owner, x, y);
    }
}
