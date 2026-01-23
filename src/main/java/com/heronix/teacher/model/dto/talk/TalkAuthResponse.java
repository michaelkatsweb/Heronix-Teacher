package com.heronix.teacher.model.dto.talk;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Authentication response DTO from Heronix-Talk server
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class TalkAuthResponse {
    private boolean success;
    private String message;
    private String sessionToken;
    private TalkUserDTO user;
    private LocalDateTime expiresAt;
}
