package com.heronix.teacher.model.dto.talk;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * DTO for emergency alerts received from Heronix-Talk server.
 * Used for campus-wide emergency notifications.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class TalkAlertDTO {

    private Long id;
    private String alertUuid;
    private String title;
    private String message;
    private String instructions;
    private String alertLevel;      // EMERGENCY, URGENT, HIGH, NORMAL, LOW
    private String alertType;       // LOCKDOWN, FIRE, WEATHER, MEDICAL, etc.
    private Long issuedById;
    private String issuedByName;
    private boolean active;
    private boolean requiresAcknowledgment;
    private boolean playSound;
    private boolean campusWide;
    private String targetRoles;
    private String targetDepartments;
    private int acknowledgmentCount;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime issuedAt;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime expiresAt;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime cancelledAt;

    /**
     * Check if this is a critical alert (EMERGENCY or URGENT).
     */
    public boolean isCritical() {
        return "EMERGENCY".equals(alertLevel) || "URGENT".equals(alertLevel);
    }

    /**
     * Check if the alert is still valid (not expired or cancelled).
     */
    public boolean isValid() {
        if (!active) return false;
        if (cancelledAt != null) return false;
        if (expiresAt != null && LocalDateTime.now().isAfter(expiresAt)) return false;
        return true;
    }

    /**
     * Get display color based on alert level.
     */
    public String getDisplayColor() {
        return switch (alertLevel != null ? alertLevel : "NORMAL") {
            case "EMERGENCY" -> "#D32F2F";  // Red
            case "URGENT" -> "#F57C00";     // Orange
            case "HIGH" -> "#FBC02D";       // Yellow
            case "NORMAL" -> "#1976D2";     // Blue
            case "LOW" -> "#757575";        // Gray
            default -> "#1976D2";
        };
    }

    /**
     * Get icon based on alert type.
     */
    public String getIcon() {
        return switch (alertType != null ? alertType : "ANNOUNCEMENT") {
            case "LOCKDOWN" -> "\uD83D\uDD12";      // Lock
            case "FIRE" -> "\uD83D\uDD25";          // Fire
            case "WEATHER" -> "\u26C8";             // Storm
            case "MEDICAL" -> "\uD83D\uDC8A";       // Medical
            case "EVACUATION" -> "\uD83D\uDEAA";    // Door
            case "SHELTER" -> "\uD83C\uDFE0";       // House
            case "ALL_CLEAR" -> "\u2705";           // Check mark
            case "SCHEDULE_CHANGE" -> "\uD83D\uDCC5"; // Calendar
            default -> "\u26A0";                     // Warning
        };
    }
}
