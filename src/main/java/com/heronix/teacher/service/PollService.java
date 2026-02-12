package com.heronix.teacher.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class PollService {

    private final AdminApiClient adminApiClient;

    @Value("${sync.admin-server.url:http://localhost:9590}")
    private String serverUrl;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    public PollService(AdminApiClient adminApiClient) {
        this.adminApiClient = adminApiClient;
    }

    private HttpRequest.Builder withAuth(HttpRequest.Builder builder) {
        String token = adminApiClient.getAuthToken();
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        return builder;
    }

    public List<Map<String, Object>> getMyPolls(String creatorName) {
        return fetchList("/api/polls/my-polls?creatorName=" + encode(creatorName));
    }

    public List<Map<String, Object>> getActivePolls(String audience) {
        return fetchList("/api/polls/active?audience=" + audience);
    }

    public Map<String, Object> getPoll(Long id) {
        return fetchMap("/api/polls/" + id);
    }

    public Map<String, Object> createPoll(Map<String, Object> poll) {
        return postJson("/api/polls", poll);
    }

    public Map<String, Object> publishPoll(Long id) {
        return postEmpty("/api/polls/" + id + "/publish");
    }

    public Map<String, Object> closePoll(Long id) {
        return postEmpty("/api/polls/" + id + "/close");
    }

    public void deletePoll(Long id) {
        try {
            HttpRequest request = withAuth(HttpRequest.newBuilder()
                    .uri(URI.create(serverUrl + "/api/polls/" + id))
                    .DELETE()
                    .timeout(Duration.ofSeconds(10)))
                    .build();
            httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (Exception e) {
            log.error("Failed to delete poll {}", id, e);
        }
    }

    public Map<String, Object> submitResponse(Long pollId, Map<String, Object> response) {
        return postJson("/api/polls/" + pollId + "/respond", response);
    }

    public boolean hasResponded(Long pollId, Long userId, String userType) {
        try {
            HttpRequest request = withAuth(HttpRequest.newBuilder()
                    .uri(URI.create(serverUrl + "/api/polls/" + pollId +
                            "/has-responded?userId=" + userId + "&userType=" + userType))
                    .GET()
                    .timeout(Duration.ofSeconds(10)))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                return Boolean.parseBoolean(response.body());
            }
        } catch (Exception e) {
            log.error("Failed to check response status", e);
        }
        return false;
    }

    public Map<String, Object> getResults(Long pollId) {
        return fetchMap("/api/polls/" + pollId + "/results");
    }

    private List<Map<String, Object>> fetchList(String path) {
        try {
            HttpRequest request = withAuth(HttpRequest.newBuilder()
                    .uri(URI.create(serverUrl + path))
                    .header("Content-Type", "application/json")
                    .GET()
                    .timeout(Duration.ofSeconds(10)))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                return objectMapper.readValue(response.body(), new TypeReference<>() {});
            }
        } catch (Exception e) {
            log.error("Failed to fetch from {}", path, e);
        }
        return Collections.emptyList();
    }

    private Map<String, Object> fetchMap(String path) {
        try {
            HttpRequest request = withAuth(HttpRequest.newBuilder()
                    .uri(URI.create(serverUrl + path))
                    .header("Content-Type", "application/json")
                    .GET()
                    .timeout(Duration.ofSeconds(10)))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                return objectMapper.readValue(response.body(), new TypeReference<>() {});
            }
        } catch (Exception e) {
            log.error("Failed to fetch from {}", path, e);
        }
        return Collections.emptyMap();
    }

    private Map<String, Object> postJson(String path, Map<String, Object> body) {
        try {
            String json = objectMapper.writeValueAsString(body);
            HttpRequest request = withAuth(HttpRequest.newBuilder()
                    .uri(URI.create(serverUrl + path))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .timeout(Duration.ofSeconds(10)))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200 || response.statusCode() == 201) {
                return objectMapper.readValue(response.body(), new TypeReference<>() {});
            }
            log.error("POST to {} failed with status {} : {}", path, response.statusCode(), response.body());
            throw new RuntimeException("Server returned " + response.statusCode());
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to POST to {}", path, e);
            throw new RuntimeException("Request failed: " + e.getMessage(), e);
        }
    }

    private Map<String, Object> postEmpty(String path) {
        try {
            HttpRequest request = withAuth(HttpRequest.newBuilder()
                    .uri(URI.create(serverUrl + path))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.noBody())
                    .timeout(Duration.ofSeconds(10)))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                return objectMapper.readValue(response.body(), new TypeReference<>() {});
            }
            log.error("POST to {} failed with status {} : {}", path, response.statusCode(), response.body());
            throw new RuntimeException("Server returned " + response.statusCode());
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to POST to {}", path, e);
            throw new RuntimeException("Request failed: " + e.getMessage(), e);
        }
    }

    private String encode(String value) {
        try {
            return java.net.URLEncoder.encode(value, java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            return value;
        }
    }
}
