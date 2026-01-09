package com.heronix.teacher.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Network monitoring service
 *
 * Monitors network connectivity to main server
 * Detects:
 * - Network availability
 * - Server reachability
 * - Network disruptions
 * - Recovery after failures
 *
 * @author EduScheduler Team
 * @version 1.0.0
 */
@Slf4j
@Service
public class NetworkMonitorService {

    @Value("${sync.admin-server.url}")
    private String adminServerUrl;

    @Value("${sync.admin-server.timeout.seconds:10}")
    private int timeoutSeconds;

    private boolean lastKnownStatus = false;
    private long lastSuccessfulCheck = 0;
    private long lastFailedCheck = 0;

    /**
     * Check if network is available and server is reachable
     */
    public boolean isNetworkAvailable() {
        try {
            URL url = new URL(adminServerUrl + "/api/health");
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(timeoutSeconds * 1000);
            connection.setReadTimeout(timeoutSeconds * 1000);
            connection.connect();

            int responseCode = connection.getResponseCode();
            boolean isAvailable = (responseCode >= 200 && responseCode < 300);

            if (isAvailable) {
                onNetworkAvailable();
            } else {
                onNetworkUnavailable();
            }

            connection.disconnect();
            return isAvailable;

        } catch (Exception e) {
            onNetworkUnavailable();
            return false;
        }
    }

    /**
     * Check if server is reachable (simplified check)
     */
    public boolean isServerReachable() {
        return isNetworkAvailable();
    }

    /**
     * Called when network becomes available
     */
    private void onNetworkAvailable() {
        if (!lastKnownStatus) {
            log.info("Network connectivity restored - Server reachable at {}", adminServerUrl);
        }

        lastKnownStatus = true;
        lastSuccessfulCheck = System.currentTimeMillis();
    }

    /**
     * Called when network becomes unavailable
     */
    private void onNetworkUnavailable() {
        if (lastKnownStatus) {
            log.warn("Network connectivity lost - Operating in offline mode");
        }

        lastKnownStatus = false;
        lastFailedCheck = System.currentTimeMillis();
    }

    /**
     * Get last known network status
     */
    public boolean getLastKnownStatus() {
        return lastKnownStatus;
    }

    /**
     * Get time since last successful check (milliseconds)
     */
    public long getTimeSinceLastSuccess() {
        if (lastSuccessfulCheck == 0) {
            return Long.MAX_VALUE;
        }
        return System.currentTimeMillis() - lastSuccessfulCheck;
    }

    /**
     * Get time since last failed check (milliseconds)
     */
    public long getTimeSinceLastFailure() {
        if (lastFailedCheck == 0) {
            return Long.MAX_VALUE;
        }
        return System.currentTimeMillis() - lastFailedCheck;
    }

    /**
     * Reset status (for testing)
     */
    public void reset() {
        lastKnownStatus = false;
        lastSuccessfulCheck = 0;
        lastFailedCheck = 0;
    }
}
