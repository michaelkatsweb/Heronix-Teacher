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
import java.util.Map;
import java.util.function.Supplier;

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
    private String refreshToken;
    private String storedEmployeeId;
    private String storedPassword;
    private Long teacherId;  // Stored from authentication response
    private String teacherName;  // Stored from authentication response

    public AdminApiClient(
            ObjectMapper objectMapper,
            @Value("${sync.admin-server.url:http://localhost:9590}") String baseUrl) {
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
     * Authenticate teacher and get JWT token from SIS Server
     */
    public boolean authenticate(String employeeId, String password) {
        try {
            String requestBody = objectMapper.writeValueAsString(
                    new AuthRequest(employeeId, password));

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/auth/login"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .timeout(Duration.ofSeconds(10))
                    .build();

            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                // Server returns: {"data":{"accessToken":"...","refreshToken":"...","userId":"T101",...},"success":true}
                var root = objectMapper.readTree(response.body());
                var data = root.path("data");
                this.authToken = data.path("accessToken").asText(null);
                this.refreshToken = data.path("refreshToken").asText(null);
                String userId = data.path("userId").asText(null);
                this.teacherName = userId; // employeeId as fallback name
                this.storedEmployeeId = employeeId;
                this.storedPassword = password;
                log.info("Server authentication successful for teacher: {}", employeeId);
                return this.authToken != null;
            } else {
                log.error("Server authentication failed: {}", response.statusCode());
                return false;
            }

        } catch (Exception e) {
            log.error("Error during server authentication", e);
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
        HttpResponse<String> response = sendAuthenticated(
                () -> buildGetRequest("/api/teacher/students"));

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
        HttpResponse<String> response = sendAuthenticated(
                () -> buildGetRequest("/api/teacher/students/" + studentId));

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
        HttpResponse<String> response = sendAuthenticated(
                () -> buildGetRequest("/api/teacher/assignments"));

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

        HttpResponse<String> response = sendAuthenticated(() -> HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/teacher/assignments"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + authToken)
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .timeout(Duration.ofSeconds(10))
                .build());

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

        HttpResponse<String> response = sendAuthenticated(() -> HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/teacher/assignments/" + assignmentId))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + authToken)
                .PUT(HttpRequest.BodyPublishers.ofString(requestBody))
                .timeout(Duration.ofSeconds(10))
                .build());

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

        HttpResponse<String> response = sendAuthenticated(() -> HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/teacher/grades/batch"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + authToken)
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .timeout(Duration.ofSeconds(30))
                .build());

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

        HttpResponse<String> response = sendAuthenticated(() -> HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/teacher/grades"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + authToken)
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .timeout(Duration.ofSeconds(10))
                .build());

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
        HttpResponse<String> response = sendAuthenticated(
                () -> buildGetRequest("/api/teacher/grades/conflicts"));

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
     * Submit attendance records (batch operation) - Legacy endpoint
     */
    public void submitAttendance(List<AttendanceDTO> attendanceRecords) throws Exception {
        String requestBody = objectMapper.writeValueAsString(attendanceRecords);

        HttpResponse<String> response = sendAuthenticated(() -> HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/teacher/attendance/batch"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + authToken)
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .timeout(Duration.ofSeconds(30))
                .build());

        if (response.statusCode() != 200 && response.statusCode() != 201) {
            throw new Exception("Failed to submit attendance: " + response.statusCode());
        }

        log.info("Successfully submitted {} attendance records", attendanceRecords.size());
    }

    /**
     * Submit bulk attendance for a class section (New Heronix-SIS API)
     * @param sectionId The course section ID
     * @param date The attendance date (yyyy-MM-dd format)
     * @param periodNumber The period number
     * @param recordedBy Username of the teacher recording
     * @param records List of student attendance records
     * @return Response map with success status and details
     */
    public java.util.Map<String, Object> submitBulkAttendance(
            Long sectionId,
            String date,
            Integer periodNumber,
            String recordedBy,
            List<java.util.Map<String, Object>> records) throws Exception {

        java.util.Map<String, Object> requestData = new java.util.HashMap<>();
        requestData.put("sectionId", sectionId);
        requestData.put("date", date);
        requestData.put("periodNumber", periodNumber);
        requestData.put("recordedBy", recordedBy);
        requestData.put("records", records);

        String requestBody = objectMapper.writeValueAsString(requestData);

        HttpResponse<String> response = sendAuthenticated(() -> HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/teacher/attendance/bulk"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + authToken)
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .timeout(Duration.ofSeconds(30))
                .build());

        java.util.Map<String, Object> result = objectMapper.readValue(response.body(),
                new TypeReference<java.util.Map<String, Object>>() {});

        if (response.statusCode() == 200) {
            log.info("Successfully submitted bulk attendance for section {}", sectionId);
        } else {
            log.error("Failed to submit bulk attendance: {}", result.get("message"));
        }

        return result;
    }

    /**
     * Get attendance for specific student
     */
    public List<AttendanceDTO> getStudentAttendance(Long studentId) throws Exception {
        HttpResponse<String> response = sendAuthenticated(
                () -> buildGetRequest("/api/teacher/attendance/student/" + studentId));

        if (response.statusCode() == 200) {
            return objectMapper.readValue(response.body(),
                    new TypeReference<List<AttendanceDTO>>() {});
        } else {
            throw new Exception("Failed to fetch attendance: " + response.statusCode());
        }
    }

    /**
     * Get attendance for a class section on a specific date (New Heronix-SIS API)
     * @param sectionId The course section ID
     * @param date The date in yyyy-MM-dd format
     * @return Map containing attendance records and summary
     */
    public java.util.Map<String, Object> getClassAttendance(Long sectionId, String date) throws Exception {
        HttpResponse<String> response = sendAuthenticated(
                () -> buildGetRequest("/api/teacher/attendance/class/" + sectionId + "/date/" + date));

        if (response.statusCode() == 200) {
            return objectMapper.readValue(response.body(),
                    new TypeReference<java.util.Map<String, Object>>() {});
        } else if (response.statusCode() == 404) {
            log.warn("No attendance found for section {} on {}", sectionId, date);
            return new java.util.HashMap<>();
        } else {
            throw new Exception("Failed to fetch class attendance: " + response.statusCode());
        }
    }

    /**
     * Get the active bell schedule with period times (New Heronix-SIS API)
     * @return Map containing schedule name, type, and period definitions
     */
    public java.util.Map<String, Object> getBellSchedule() throws Exception {
        HttpResponse<String> response = sendAuthenticated(
                () -> buildGetRequest("/api/teacher/attendance/bell-schedule"));

        if (response.statusCode() == 200) {
            return objectMapper.readValue(response.body(),
                    new TypeReference<java.util.Map<String, Object>>() {});
        } else {
            log.warn("Failed to fetch bell schedule: {}", response.statusCode());
            return new java.util.HashMap<>();
        }
    }

    /**
     * Get the current period based on server time (New Heronix-SIS API)
     * @return Map containing current period info or null if between periods
     */
    public java.util.Map<String, Object> getCurrentPeriod() throws Exception {
        HttpResponse<String> response = sendAuthenticated(
                () -> buildGetRequest("/api/teacher/attendance/current-period"));

        if (response.statusCode() == 200) {
            return objectMapper.readValue(response.body(),
                    new TypeReference<java.util.Map<String, Object>>() {});
        } else {
            return new java.util.HashMap<>();
        }
    }

    // ========================================================================
    // BEHAVIOR INCIDENTS
    // ========================================================================

    /**
     * Fetch behavior incidents for a specific student
     */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> getStudentBehaviorIncidents(Long studentId) throws Exception {
        HttpResponse<String> response = sendAuthenticated(
                () -> buildGetRequest("/api/behavior-incidents/students/" + studentId));

        if (response.statusCode() == 200) {
            return objectMapper.readValue(response.body(),
                    new TypeReference<List<Map<String, Object>>>() {});
        } else {
            throw new Exception("Failed to fetch behavior incidents: " + response.statusCode());
        }
    }

    /**
     * Create a new behavior incident
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> createBehaviorIncident(Map<String, Object> incidentData) throws Exception {
        String requestBody = objectMapper.writeValueAsString(incidentData);

        HttpResponse<String> response = sendAuthenticated(() -> HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/behavior-incidents"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + authToken)
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .timeout(Duration.ofSeconds(10))
                .build());

        if (response.statusCode() == 200 || response.statusCode() == 201) {
            return objectMapper.readValue(response.body(),
                    new TypeReference<Map<String, Object>>() {});
        } else {
            throw new Exception("Failed to create behavior incident: " + response.statusCode()
                    + " - " + response.body());
        }
    }

    // ========================================================================
    // SYNC STATUS
    // ========================================================================

    /**
     * Get sync status from server
     */
    public SyncStatusDTO getSyncStatus() throws Exception {
        HttpResponse<String> response = sendAuthenticated(
                () -> buildGetRequest("/api/teacher/sync/status"));

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
        SyncCompleteRequest syncRequest = new SyncCompleteRequest(gradeIds, attendanceIds);
        String requestBody = objectMapper.writeValueAsString(syncRequest);

        HttpResponse<String> response = sendAuthenticated(() -> HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/teacher/sync/complete"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + authToken)
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .timeout(Duration.ofSeconds(10))
                .build());

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
        HttpResponse<String> response = sendAuthenticated(
                () -> buildGetRequest("/api/teacher/schedule?employeeId=" + employeeId));

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
        HttpResponse<String> response = sendAuthenticated(
                () -> buildGetRequest("/api/teacher/roster?employeeId=" + employeeId + "&period=" + period));

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
     * Get all class rosters for all periods (0-7) - Legacy method using employeeId
     */
    public java.util.Map<Integer, ClassRosterDTO> getAllRosters(String employeeId) throws Exception {
        HttpResponse<String> response = sendAuthenticated(
                () -> buildGetRequest("/api/teacher/rosters?employeeId=" + employeeId));

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

    /**
     * Get all class rosters for a teacher by teacher ID (New Heronix-SIS API)
     * This is the preferred method for the new attendance system
     * @param teacherId The teacher's database ID (Long)
     * @return Map containing rosters array with section info and enrolled students
     */
    public java.util.Map<String, Object> getTeacherRosters(Long teacherId) throws Exception {
        HttpResponse<String> response = sendAuthenticated(
                () -> buildGetRequest("/api/teacher/attendance/roster/" + teacherId));

        if (response.statusCode() == 200) {
            return objectMapper.readValue(response.body(),
                    new TypeReference<java.util.Map<String, Object>>() {});
        } else if (response.statusCode() == 404) {
            log.warn("No rosters found for teacher ID: {}", teacherId);
            java.util.Map<String, Object> empty = new java.util.HashMap<>();
            empty.put("success", false);
            empty.put("message", "Teacher not found");
            return empty;
        } else {
            throw new Exception("Failed to fetch teacher rosters: " + response.statusCode());
        }
    }

    /**
     * Get roster for a specific teacher and period (New Heronix-SIS API)
     * @param teacherId The teacher's database ID
     * @param period The period number (1-7)
     * @return Map containing roster data with students
     */
    public java.util.Map<String, Object> getTeacherRosterForPeriod(Long teacherId, Integer period) throws Exception {
        HttpResponse<String> response = sendAuthenticated(
                () -> buildGetRequest("/api/teacher/attendance/roster/" + teacherId + "/period/" + period));

        if (response.statusCode() == 200) {
            return objectMapper.readValue(response.body(),
                    new TypeReference<java.util.Map<String, Object>>() {});
        } else if (response.statusCode() == 404) {
            log.warn("No roster found for teacher {} period {}", teacherId, period);
            java.util.Map<String, Object> empty = new java.util.HashMap<>();
            empty.put("success", true);
            empty.put("roster", null);
            empty.put("message", "No class scheduled for this period");
            return empty;
        } else {
            throw new Exception("Failed to fetch roster: " + response.statusCode());
        }
    }

    // ========================================================================
    // HEALTH CHECK
    // ========================================================================

    /**
     * Check if admin server is reachable
     * Tries multiple endpoints: /actuator/health, /api/health, then just attempts connection
     */
    public boolean isServerReachable() {
        // Try Spring Boot Actuator health endpoint first
        String[] healthEndpoints = {"/actuator/health", "/api/health", "/api/system/health"};

        for (String endpoint : healthEndpoints) {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(baseUrl + endpoint))
                        .GET()
                        .timeout(Duration.ofSeconds(3))
                        .build();

                HttpResponse<String> response = httpClient.send(request,
                        HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200) {
                    return true;
                }
            } catch (Exception e) {
                log.debug("Health endpoint {} not available: {}", endpoint, e.getMessage());
            }
        }

        // Fallback: try to connect to base URL
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl))
                    .GET()
                    .timeout(Duration.ofSeconds(3))
                    .build();

            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString());

            // Any response (even 404) means server is up
            return response.statusCode() < 500;

        } catch (Exception e) {
            log.debug("Server not reachable: {}", e.getMessage());
            return false;
        }
    }

    // ========================================================================
    // HELPER METHODS
    // ========================================================================

    /**
     * Send an HTTP request with automatic 401 retry.
     * On 401: tries refresh token first, then full re-authentication, then retries the request once.
     */
    private HttpResponse<String> sendAuthenticated(Supplier<HttpRequest> requestFactory) throws Exception {
        HttpResponse<String> response = httpClient.send(requestFactory.get(),
                HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 401) {
            log.info("Received 401 - attempting token refresh");
            if (refreshAccessToken() || reauthenticate()) {
                response = httpClient.send(requestFactory.get(),
                        HttpResponse.BodyHandlers.ofString());
            }
        }

        return response;
    }

    /**
     * Refresh the access token using the stored refresh token.
     * @return true if refresh succeeded and authToken was updated
     */
    private boolean refreshAccessToken() {
        if (refreshToken == null || refreshToken.isEmpty()) {
            return false;
        }

        try {
            String body = objectMapper.writeValueAsString(Map.of("refreshToken", refreshToken));

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/auth/refresh"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .timeout(Duration.ofSeconds(10))
                    .build();

            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                var root = objectMapper.readTree(response.body());
                var data = root.path("data");
                this.authToken = data.path("accessToken").asText(null);
                String newRefresh = data.path("refreshToken").asText(null);
                if (newRefresh != null) {
                    this.refreshToken = newRefresh;
                }
                log.info("Token refresh successful");
                return this.authToken != null;
            }
        } catch (Exception e) {
            log.debug("Token refresh failed: {}", e.getMessage());
        }

        return false;
    }

    /**
     * Re-authenticate using stored credentials as fallback when refresh token is also expired.
     * @return true if re-authentication succeeded
     */
    private boolean reauthenticate() {
        if (storedEmployeeId == null || storedPassword == null) {
            log.warn("Cannot re-authenticate: no stored credentials");
            return false;
        }

        log.info("Refresh token expired, re-authenticating with stored credentials");
        return authenticate(storedEmployeeId, storedPassword);
    }

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

    public String getAuthToken() {
        return authToken;
    }

    public boolean isAuthenticated() {
        return authToken != null && !authToken.isEmpty();
    }

    /**
     * Get the authenticated teacher's database ID
     * @return Teacher ID from authentication, or null if not authenticated
     */
    public Long getTeacherId() {
        return teacherId;
    }

    /**
     * Set the teacher ID manually (for testing or offline mode)
     */
    public void setTeacherId(Long teacherId) {
        this.teacherId = teacherId;
    }

    /**
     * Get the authenticated teacher's name
     * @return Teacher name from authentication, or null if not authenticated
     */
    public String getTeacherName() {
        return teacherName;
    }

    // ========================================================================
    // INNER CLASSES FOR REQUESTS/RESPONSES
    // ========================================================================

    private static class AuthRequest {
        public String username;
        public String password;

        public AuthRequest(String employeeId, String password) {
            this.username = employeeId;
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

        public Long getTeacherId() {
            return teacherId;
        }

        public String getTeacherName() {
            return teacherName;
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
