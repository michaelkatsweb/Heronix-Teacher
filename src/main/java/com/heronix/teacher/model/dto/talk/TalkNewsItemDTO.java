package com.heronix.teacher.model.dto.talk;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * News Item DTO from Heronix-Talk server
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class TalkNewsItemDTO {
    private Long id;
    private String headline;
    private String content;
    private String category;
    private Long authorId;
    private String authorName;
    private int priority;
    private boolean active;
    private boolean pinned;
    private boolean urgent;
    private String linkUrl;
    private String imagePath;
    private int viewCount;
    private LocalDateTime publishedAt;
    private LocalDateTime expiresAt;

    /**
     * Get display icon based on category/urgency
     */
    public String getDisplayIcon() {
        if (urgent) {
            return "\u26A0\uFE0F"; // Warning sign
        }
        if (pinned) {
            return "\uD83D\uDCCC"; // Pin
        }
        if (category == null) {
            return "\uD83D\uDCF0"; // Newspaper
        }
        return switch (category.toLowerCase()) {
            case "system" -> "\u2699\uFE0F";       // Gear
            case "events" -> "\uD83D\uDCC5";       // Calendar
            case "sports" -> "\u26BD";             // Soccer ball
            case "academic" -> "\uD83D\uDCDA";     // Books
            case "announcement" -> "\uD83D\uDCE2"; // Loudspeaker
            case "alert" -> "\uD83D\uDEA8";        // Rotating light
            default -> "\uD83D\uDCF0";             // Newspaper
        };
    }
}
