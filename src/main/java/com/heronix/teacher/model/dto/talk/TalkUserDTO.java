package com.heronix.teacher.model.dto.talk;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * User DTO from Heronix-Talk server
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TalkUserDTO {
    private Long id;
    private String username;
    private String employeeId;
    private String firstName;
    private String lastName;
    private String email;
    private String department;
    private String phoneNumber;
    private String role;          // ADMIN, PRINCIPAL, TEACHER, STAFF, COUNSELOR, DEPARTMENT_HEAD
    private String status;        // ONLINE, AWAY, BUSY, IN_CLASS, IN_MEETING, OFFLINE
    private String statusMessage;
    private String avatarPath;
    private boolean active;
    private boolean notificationsEnabled;
    private LocalDateTime lastSeen;
    private LocalDateTime lastActivity;

    public String getFullName() {
        return (firstName != null ? firstName : "") + " " + (lastName != null ? lastName : "");
    }

    public boolean isOnline() {
        return "ONLINE".equals(status) || "AWAY".equals(status) || "BUSY".equals(status)
                || "IN_CLASS".equals(status) || "IN_MEETING".equals(status);
    }
}
