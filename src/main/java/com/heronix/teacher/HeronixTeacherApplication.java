package com.heronix.teacher;

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

            // Load LOGIN window (changed from Main.fxml)
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/Login.fxml"));
            loader.setControllerFactory(springContext::getBean);

            Parent root = loader.load();

            // Create scene
            Scene scene = new Scene(root, 800, 600);

            // Note: Stylesheet is loaded in Login.fxml, no need to add here

            // Configure stage
            primaryStage.setTitle("Heronix-Teacher - Login");
            primaryStage.setScene(scene);
            primaryStage.setMinWidth(800);
            primaryStage.setMinHeight(600);
            primaryStage.setResizable(false);  // Fixed size for login screen

            // Center on screen
            primaryStage.centerOnScreen();

            // Handle close request
            primaryStage.setOnCloseRequest(event -> {
                log.info("Application close requested");
                Platform.exit();
            });

            primaryStage.show();
            log.info("JavaFX application started successfully - Login screen displayed");

        } catch (Exception e) {
            log.error("Failed to start JavaFX application", e);
            Platform.exit();
        }
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
