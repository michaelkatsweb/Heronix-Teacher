package com.heronix.teacher.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
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

/**
 * Service to fetch dismissal board data from SIS Server API.
 * Teacher portal is read-only for dismissal events.
 */
@Slf4j
@Service
public class DismissalService {

    @Value("${sync.admin-server.url:http://localhost:9590}")
    private String serverUrl;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    public List<Map<String, Object>> getTodaysEvents() {
        return fetchList("/api/dismissal/today");
    }

    public List<Map<String, Object>> getTodaysBuses() {
        return fetchList("/api/dismissal/today/buses");
    }

    public List<Map<String, Object>> getTodaysPickups() {
        return fetchList("/api/dismissal/today/pickups");
    }

    public Map<String, Object> getTodaysStats() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(serverUrl + "/api/dismissal/today/stats"))
                    .header("Content-Type", "application/json")
                    .GET()
                    .timeout(Duration.ofSeconds(10))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                return objectMapper.readValue(response.body(), new TypeReference<>() {});
            }
        } catch (Exception e) {
            log.error("Failed to fetch dismissal stats", e);
        }
        return Collections.emptyMap();
    }

    public List<Map<String, Object>> getCounselorSummonNotifications() {
        return fetchList("/api/notifications/type/COUNSELOR_SUMMON");
    }

    private List<Map<String, Object>> fetchList(String path) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(serverUrl + path))
                    .header("Content-Type", "application/json")
                    .GET()
                    .timeout(Duration.ofSeconds(10))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                return objectMapper.readValue(response.body(), new TypeReference<>() {});
            }
        } catch (Exception e) {
            log.error("Failed to fetch dismissal data from {}", path, e);
        }
        return Collections.emptyList();
    }
}
