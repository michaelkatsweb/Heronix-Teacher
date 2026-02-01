package com.heronix.teacher;

import com.heronix.teacher.model.domain.Teacher;
import com.heronix.teacher.repository.TeacherRepository;
import com.heronix.teacher.service.SessionManager;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

/**
 * Main application class for Heronix-Teacher
 *
 * This is a standalone desktop application for teachers to manage:
 * - Student gradebook
 * - Attendance tracking
 * - Hall pass management
 *
 * Architecture:
 * - JavaFX 21 for UI
 * - Spring Boot (embedded, no server)
 * - SQLite for local database (offline-first)
 * - Optional network sync with EduScheduler-Pro admin server
 *
 * Features:
 * - Offline-first operation
 * - Dark/Light theme support
 * - Air-gapped security
 * - Local network/WiFi/Internet sync (when available)
 * - AES-256 encrypted data storage
 *
 * @author EduScheduler Team
 * @version 1.0.0
 * @since 2025-11-28
 */
@Slf4j
@SpringBootApplication
public class HeronixTeacherApplication extends Application {

    private ConfigurableApplicationContext springContext;
    private Stage primaryStage;

    /**
     * Main entry point for the application
     */
    public static void main(String[] args) {
        log.info("Starting Heronix-Teacher Application...");
        launch(args);
    }

    /**
     * Initialize Spring Boot context before JavaFX starts
     */
    @Override
    public void init() {
        log.info("Initializing Spring Boot context...");
        springContext = SpringApplication.run(HeronixTeacherApplication.class);
        log.info("Spring Boot context initialized successfully");
    }

    /**
     * Start JavaFX application
     */
    @Override
    public void start(Stage primaryStage) {
        try {
            log.info("Starting JavaFX application...");
            this.primaryStage = primaryStage;

            // Handle close request
            primaryStage.setOnCloseRequest(event -> {
                log.info("Application close requested");
                Platform.exit();
            });

            // Check for SSO token from Heronix Hub
            String ssoTeacherName = attemptSsoLogin();

            if (ssoTeacherName != null) {
                // SSO successful - go directly to main application
                log.info("SSO login successful for: {}, skipping login screen", ssoTeacherName);
                showMainApplication(primaryStage, ssoTeacherName);
            } else {
                // No SSO - show normal login screen
                showLoginScreen(primaryStage);
            }

            log.info("JavaFX application started successfully");

        } catch (Exception e) {
            log.error("Failed to start JavaFX application", e);
            Platform.exit();
        }
    }

    /**
     * Attempt SSO login by reading the Hub's JWT token file.
     */
    private String attemptSsoLogin() {
        try {
            String tokenFilePath = System.getProperty("user.home") + "/.heronix/auth/token.jwt";
            Path tokenPath = Paths.get(tokenFilePath);

            if (!Files.exists(tokenPath)) {
                log.debug("SSO token file not found: {}", tokenFilePath);
                return null;
            }

            String token = Files.readString(tokenPath).trim();
            if (token.isEmpty()) return null;

            // Parse JWT payload (trusted local file from Hub)
            String[] parts = token.split("\\.");
            if (parts.length != 3) return null;

            String payload = new String(java.util.Base64.getUrlDecoder().decode(parts[1]));
            String username = extractJsonValue(payload, "sub");
            String fullName = extractJsonValue(payload, "fullName");
            String role = extractJsonValue(payload, "role");

            if (username == null || username.isEmpty()) return null;

            // Check expiration
            String expStr = extractJsonValue(payload, "exp");
            if (expStr != null) {
                long exp = Long.parseLong(expStr);
                if (System.currentTimeMillis() / 1000 > exp) {
                    log.warn("SSO token has expired");
                    return null;
                }
            }

            // Look up the teacher in the local database
            TeacherRepository teacherRepo = springContext.getBean(TeacherRepository.class);
            Optional<Teacher> teacherOpt = teacherRepo.findByEmployeeId(username);

            if (teacherOpt.isEmpty()) {
                log.warn("SSO: Teacher not found in local DB for employeeId: {}", username);
                return null;
            }

            // Establish session
            SessionManager sessionManager = springContext.getBean(SessionManager.class);
            sessionManager.login(teacherOpt.get());

            log.info("SSO authentication successful: {} (role: {})", username, role);
            return sessionManager.getCurrentTeacherName();

        } catch (Exception e) {
            log.warn("SSO login failed, falling back to login screen: {}", e.getMessage());
            return null;
        }
    }

    private String extractJsonValue(String json, String key) {
        String searchKey = "\"" + key + "\"";
        int keyIdx = json.indexOf(searchKey);
        if (keyIdx < 0) return null;
        int colonIdx = json.indexOf(':', keyIdx + searchKey.length());
        if (colonIdx < 0) return null;
        int valueStart = colonIdx + 1;
        while (valueStart < json.length() && json.charAt(valueStart) == ' ') valueStart++;
        if (valueStart >= json.length()) return null;
        if (json.charAt(valueStart) == '"') {
            int valueEnd = json.indexOf('"', valueStart + 1);
            if (valueEnd < 0) return null;
            return json.substring(valueStart + 1, valueEnd);
        } else {
            int valueEnd = valueStart;
            while (valueEnd < json.length() && json.charAt(valueEnd) != ',' && json.charAt(valueEnd) != '}') valueEnd++;
            return json.substring(valueStart, valueEnd).trim();
        }
    }

    private void showLoginScreen(Stage stage) throws Exception {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/Login.fxml"));
        loader.setControllerFactory(springContext::getBean);
        Parent root = loader.load();

        Scene scene = new Scene(root, 800, 600);
        stage.setTitle("Heronix-Teacher - Login");
        stage.setScene(scene);
        stage.setMinWidth(800);
        stage.setMinHeight(600);
        stage.setResizable(false);
        stage.centerOnScreen();
        stage.show();
    }

    private void showMainApplication(Stage stage, String teacherName) throws Exception {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/Main.fxml"));
        loader.setControllerFactory(springContext::getBean);
        Parent root = loader.load();

        Scene scene = new Scene(root, 1200, 800);
        String stylesheet = getClass().getResource("/css/light-theme.css") != null
                ? getClass().getResource("/css/light-theme.css").toExternalForm() : null;
        if (stylesheet != null) {
            scene.getStylesheets().add(stylesheet);
        }

        stage.setTitle("Heronix-Teacher - " + teacherName);
        stage.setScene(scene);
        stage.setMinWidth(800);
        stage.setMinHeight(600);
        stage.setResizable(true);
        stage.centerOnScreen();
        stage.show();
    }

    /**
     * Cleanup when application stops
     */
    @Override
    public void stop() {
        log.info("Stopping Heronix-Teacher Application...");

        if (springContext != null) {
            springContext.close();
            log.info("Spring Boot context closed");
        }

        log.info("Heronix-Teacher Application stopped");
    }

    /**
     * Get Spring application context
     * @return Spring application context
     */
    public ConfigurableApplicationContext getSpringContext() {
        return springContext;
    }

    /**
     * Get primary stage
     * @return JavaFX primary stage
     */
    public Stage getPrimaryStage() {
        return primaryStage;
    }
}
