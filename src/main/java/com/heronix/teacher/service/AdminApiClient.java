package com.heronix.teacher.service;

import com.heronix.teacher.model.dto.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

/**
 * REST API Client for EduScheduler-Pro Admin Server
 * Handles all HTTP communication for sync operations
 */
@Slf4j
@Service
public class AdminApiClient {

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String baseUrl;
    private String authToken;

    public AdminApiClient(
            ObjectMapper objectMapper,
            @Value("${sync.admin-server.url:http://localhost:8080}") String baseUrl) {
        this.objectMapper = objectMapper;
        this.baseUrl = baseUrl;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    // ========================================================================
    // AUTHENTICATION
    // ========================================================================

    /**
     * Authenticate teacher and get JWT token
     */
    public boolean authenticate(String employeeId, String password) {
        try {
            String requestBody = objectMapper.writeValueAsString(
                    new AuthRequest(employeeId, password));

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/teacher/auth/login"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .timeout(Duration.ofSeconds(10))
                    .build();

            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                AuthResponse authResponse = objectMapper.readValue(
                        response.body(), AuthResponse.class);
                this.authToken = authResponse.getToken();
                log.info("Authentication successful for teacher: {}", employeeId);
                return true;
            } else {
                log.error("Authentication failed: {}", response.statusCode());
                return false;
            }

        } catch (Exception e) {
            log.error("Error during authentication", e);
            return false;
        }
    }

    // ========================================================================
    // STUDENT DATA SYNC
    // ========================================================================

    /**
     * Get all students assigned to this teacher
     */
    public List<StudentDTO> getStudents() throws Exception {
        HttpRequest request = buildGetRequest("/api/teacher/students");
        HttpResponse<String> response = httpClient.send(request,
                HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 200) {
            return objectMapper.readValue(response.body(),
                    new TypeReference<List<StudentDTO>>() {});
        } else {
            throw new Exception("Failed to fetch students: " + response.statusCode());
        }
    }

    /**
     * Get specific student by ID
     */
    public StudentDTO getStudent(Long studentId) throws Exception {
        HttpRequest request = buildGetRequest("/api/teacher/students/" + studentId);
        HttpResponse<String> response = httpClient.send(request,
                HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 200) {
            return objectMapper.readValue(response.body(), StudentDTO.class);
        } else {
            throw new Exception("Failed to fetch student: " + response.statusCode());
        }
    }

    // ========================================================================
    // ASSIGNMENT DATA SYNC
    // ========================================================================

    /**
     * Get all assignments for this teacher
     */
    public List<AssignmentDTO> getAssignments() throws Exception {
        HttpRequest request = buildGetRequest("/api/teacher/assignments");
        HttpResponse<String> response = httpClient.send(request,
                HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 200) {
            return objectMapper.readValue(response.body(),
                    new TypeReference<List<AssignmentDTO>>() {});
        } else {
            throw new Exception("Failed to fetch assignments: " + response.statusCode());
        }
    }

    /**
     * Create new assignment on server
     */
    public AssignmentDTO createAssignment(AssignmentDTO assignment) throws Exception {
        String requestBody = objectMapper.writeValueAsString(assignment);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/teacher/assignments"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + authToken)
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .timeout(Duration.ofSeconds(10))
                .build();

        HttpResponse<String> response = httpClient.send(request,
                HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 201) {
            return objectMapper.readValue(response.body(), AssignmentDTO.class);
        } else {
            throw new Exception("Failed to create assignment: " + response.statusCode());
        }
    }

    /**
     * Update assignment on server
     */
    public AssignmentDTO updateAssignment(Long assignmentId, AssignmentDTO assignment)
            throws Exception {
        String requestBody = objectMapper.writeValueAsString(assignment);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/teacher/assignments/" + assignmentId))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + authToken)
                .PUT(HttpRequest.BodyPublishers.ofString(requestBody))
                .timeout(Duration.ofSeconds(10))
                .build();

        HttpResponse<String> response = httpClient.send(request,
                HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 200) {
            return objectMapper.readValue(response.body(), AssignmentDTO.class);
        } else {
            throw new Exception("Failed to update assignment: " + response.statusCode());
        }
    }

    // ========================================================================
    // GRADE DATA SYNC
    // ========================================================================

    /**
     * Submit grades to server (batch operation)
     */
    public void submitGrades(List<GradeDTO> grades) throws Exception {
        String requestBody = objectMapper.writeValueAsString(grades);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/teacher/grades/batch"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + authToken)
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .timeout(Duration.ofSeconds(30))
                .build();

        HttpResponse<String> response = httpClient.send(request,
                HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200 && response.statusCode() != 201) {
            throw new Exception("Failed to submit grades: " + response.statusCode());
        }

        log.info("Successfully submitted {} grades to server", grades.size());
    }

    /**
     * Submit single grade to server
     */
    public GradeDTO submitGrade(GradeDTO grade) throws Exception {
        String requestBody = objectMapper.writeValueAsString(grade);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/teacher/grades"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + authToken)
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .timeout(Duration.ofSeconds(10))
                .build();

        HttpResponse<String> response = httpClient.send(request,
                HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 200 || response.statusCode() == 201) {
            return objectMapper.readValue(response.body(), GradeDTO.class);
        } else {
            throw new Exception("Failed to submit grade: " + response.statusCode());
        }
    }

    /**
     * Check for grade conflicts
     */
    public List<ConflictDTO> getGradeConflicts() throws Exception {
        HttpRequest request = buildGetRequest("/api/teacher/grades/conflicts");
        HttpResponse<String> response = httpClient.send(request,
                HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 200) {
            return objectMapper.readValue(response.body(),
                    new TypeReference<List<ConflictDTO>>() {});
        } else {
            throw new Exception("Failed to fetch conflicts: " + response.statusCode());
        }
    }

    // ========================================================================
    // ATTENDANCE DATA SYNC
    // ========================================================================

    /**
     * Submit attendance records (batch operation)
     */
    public void submitAttendance(List<AttendanceDTO> attendanceRecords) throws Exception {
        String requestBody = objectMapper.writeValueAsString(attendanceRecords);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/teacher/attendance/batch"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + authToken)
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .timeout(Duration.ofSeconds(30))
                .build();

        HttpResponse<String> response = httpClient.send(request,
                HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200 && response.statusCode() != 201) {
            throw new Exception("Failed to submit attendance: " + response.statusCode());
        }

        log.info("Successfully submitted {} attendance records", attendanceRecords.size());
    }

    /**
     * Get attendance for specific student
     */
    public List<AttendanceDTO> getStudentAttendance(Long studentId) throws Exception {
        HttpRequest request = buildGetRequest("/api/teacher/attendance/student/" + studentId);
        HttpResponse<String> response = httpClient.send(request,
                HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 200) {
            return objectMapper.readValue(response.body(),
                    new TypeReference<List<AttendanceDTO>>() {});
        } else {
            throw new Exception("Failed to fetch attendance: " + response.statusCode());
        }
    }

    // ========================================================================
    // SYNC STATUS
    // ========================================================================

    /**
     * Get sync status from server
     */
    public SyncStatusDTO getSyncStatus() throws Exception {
        HttpRequest request = buildGetRequest("/api/teacher/sync/status");
        HttpResponse<String> response = httpClient.send(request,
                HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 200) {
            return objectMapper.readValue(response.body(), SyncStatusDTO.class);
        } else {
            throw new Exception("Failed to fetch sync status: " + response.statusCode());
        }
    }

    /**
     * Mark items as successfully synced
     */
    public void markSynced(List<Long> gradeIds, List<Long> attendanceIds) throws Exception {
        SyncCompleteRequest request = new SyncCompleteRequest(gradeIds, attendanceIds);
        String requestBody = objectMapper.writeValueAsString(request);

        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/teacher/sync/complete"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + authToken)
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .timeout(Duration.ofSeconds(10))
                .build();

        HttpResponse<String> response = httpClient.send(httpRequest,
                HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new Exception("Failed to mark items synced: " + response.statusCode());
        }
    }

    // ========================================================================
    // SCHEDULE AND ROSTER SYNC
    // ========================================================================

    /**
     * Get teacher's complete schedule with all period assignments
     */
    public TeacherScheduleDTO getTeacherSchedule(String employeeId) throws Exception {
        HttpRequest request = buildGetRequest("/api/teacher/schedule?employeeId=" + employeeId);
        HttpResponse<String> response = httpClient.send(request,
                HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 200) {
            return objectMapper.readValue(response.body(), TeacherScheduleDTO.class);
        } else if (response.statusCode() == 404) {
            log.warn("No schedule found for teacher: {}", employeeId);
            return null;
        } else {
            throw new Exception("Failed to fetch teacher schedule: " + response.statusCode());
        }
    }

    /**
     * Get class roster for a specific period
     */
    public ClassRosterDTO getClassRoster(String employeeId, Integer period) throws Exception {
        HttpRequest request = buildGetRequest(
                "/api/teacher/roster?employeeId=" + employeeId + "&period=" + period);
        HttpResponse<String> response = httpClient.send(request,
                HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 200) {
            String body = response.body();
            if (body == null || body.trim().equals("null") || body.trim().isEmpty()) {
                log.debug("No class scheduled for period {} (planning period or lunch)", period);
                return null;
            }
            return objectMapper.readValue(body, ClassRosterDTO.class);
        } else if (response.statusCode() == 404) {
            log.warn("No roster found for teacher {} period {}", employeeId, period);
            return null;
        } else {
            throw new Exception("Failed to fetch class roster: " + response.statusCode());
        }
    }

    /**
     * Get all class rosters for all periods (0-7)
     */
    public java.util.Map<Integer, ClassRosterDTO> getAllRosters(String employeeId) throws Exception {
        HttpRequest request = buildGetRequest("/api/teacher/rosters?employeeId=" + employeeId);
        HttpResponse<String> response = httpClient.send(request,
                HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 200) {
            return objectMapper.readValue(response.body(),
                    new TypeReference<java.util.Map<Integer, ClassRosterDTO>>() {});
        } else if (response.statusCode() == 404) {
            log.warn("No rosters found for teacher: {}", employeeId);
            return new java.util.HashMap<>();
        } else {
            throw new Exception("Failed to fetch rosters: " + response.statusCode());
        }
    }

    // ========================================================================
    // HEALTH CHECK
    // ========================================================================

    /**
     * Check if admin server is reachable
     */
    public boolean isServerReachable() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/health"))
                    .GET()
                    .timeout(Duration.ofSeconds(5))
                    .build();

            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString());

            return response.statusCode() == 200;

        } catch (Exception e) {
            log.debug("Server not reachable: {}", e.getMessage());
            return false;
        }
    }

    // ========================================================================
    // HELPER METHODS
    // ========================================================================

    private HttpRequest buildGetRequest(String endpoint) {
        return HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + endpoint))
                .header("Authorization", "Bearer " + authToken)
                .GET()
                .timeout(Duration.ofSeconds(10))
                .build();
    }

    public void setAuthToken(String token) {
        this.authToken = token;
    }

    public boolean isAuthenticated() {
        return authToken != null && !authToken.isEmpty();
    }

    // ========================================================================
    // INNER CLASSES FOR REQUESTS/RESPONSES
    // ========================================================================

    private static class AuthRequest {
        public String employeeId;
        public String password;

        public AuthRequest(String employeeId, String password) {
            this.employeeId = employeeId;
            this.password = password;
        }
    }

    private static class AuthResponse {
        public String token;
        public String teacherName;
        public Long teacherId;

        public String getToken() {
            return token;
        }
    }

    private static class SyncCompleteRequest {
        public List<Long> gradeIds;
        public List<Long> attendanceIds;

        public SyncCompleteRequest(List<Long> gradeIds, List<Long> attendanceIds) {
            this.gradeIds = gradeIds;
            this.attendanceIds = attendanceIds;
        }
    }
}
