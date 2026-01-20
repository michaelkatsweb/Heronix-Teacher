package com.heronix.teacher.model.dto.talk;

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
public class TalkAuthResponse {
    private boolean success;
    private String message;
    private String sessionToken;
    private TalkUserDTO user;
    private LocalDateTime expiresAt;
}
