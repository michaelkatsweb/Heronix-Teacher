package com.heronix.teacher.model.dto.talk;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Authentication request DTO for Heronix-Talk server
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TalkAuthRequest {
    private String username;
    private String password;
    private String clientType;
    private String clientVersion;
    private String deviceName;
    private boolean rememberMe;
}
