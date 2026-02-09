package com.heronix.teacher.ui.controller;

import com.heronix.teacher.model.domain.Teacher;
import com.heronix.teacher.service.AdminApiClient;
import com.heronix.teacher.service.AuthenticationService;
import com.heronix.teacher.service.SessionManager;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.Stage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Controller;

import java.util.Optional;

/**
 * Login Controller
 *
 * Handles teacher authentication and navigation to main application
 *
 * @author Heronix-Teacher Team
 * @version 1.0.0
 */
@Controller
@Slf4j
@RequiredArgsConstructor
public class LoginController {

    private final AuthenticationService authenticationService;
    private final SessionManager sessionManager;
    private final ConfigurableApplicationContext springContext;
    private final AdminApiClient adminApiClient;

    @FXML
    private TextField employeeIdField;

    @FXML
    private PasswordField passwordField;

    @FXML
    private CheckBox rememberMeCheckbox;

    @FXML
    private Label errorLabel;

    @FXML
    private Button loginButton;

    @FXML
    private Hyperlink forgotPasswordLink;

    /**
     * Initialize controller
     */
    @FXML
    public void initialize() {
        log.info("LoginController initialized");

        // Clear error message initially
        hideError();

        // Set focus on employee ID field
        Platform.runLater(() -> employeeIdField.requestFocus());
    }

    /**
     * Handle login button click or Enter key press
     */
    @FXML
    public void handleLogin() {
        log.info("Login attempt...");

        // Get input values
        String employeeId = employeeIdField.getText();
        String password = passwordField.getText();

        // Validate input
        if (employeeId == null || employeeId.trim().isEmpty()) {
            showError("Please enter your Employee ID");
            employeeIdField.requestFocus();
            return;
        }

        if (password == null || password.isEmpty()) {
            showError("Please enter your password");
            passwordField.requestFocus();
            return;
        }

        // Disable login button during authentication
        loginButton.setDisable(true);
        hideError();

        // Perform authentication (in background to keep UI responsive)
        Platform.runLater(() -> {
            try {
                Optional<Teacher> teacherOpt = authenticationService.authenticate(
                    employeeId.trim(),
                    password
                );

                if (teacherOpt.isPresent()) {
                    Teacher teacher = teacherOpt.get();
                    log.info("Login successful for: {} ({})",
                            teacher.getFullName(), teacher.getEmployeeId());

                    // Set session with password for Talk authentication
                    sessionManager.login(teacher, password);

                    // Authenticate with SIS Server to get JWT for API calls
                    try {
                        boolean serverAuth = adminApiClient.authenticate(
                                employeeId.trim(), password);
                        if (serverAuth) {
                            log.info("SIS Server authentication successful");
                            // Fetch server-side teacher ID from schedule endpoint
                            try {
                                var schedule = adminApiClient.getTeacherSchedule(employeeId.trim());
                                if (schedule != null && schedule.getTeacherId() != null) {
                                    adminApiClient.setTeacherId(schedule.getTeacherId());
                                    log.info("Server teacher ID: {}", schedule.getTeacherId());
                                } else {
                                    adminApiClient.setTeacherId(teacher.getId());
                                }
                            } catch (Exception ex) {
                                log.warn("Could not fetch server teacher ID, using local: {}", ex.getMessage());
                                adminApiClient.setTeacherId(teacher.getId());
                            }
                        } else {
                            log.warn("SIS Server authentication failed - API features will be limited");
                        }
                    } catch (Exception e) {
                        log.warn("Could not authenticate with SIS Server: {}", e.getMessage());
                    }

                    // Navigate to main application
                    navigateToMainApplication();

                } else {
                    log.warn("Login failed for employee ID: {}", employeeId);
                    showError("Invalid Employee ID or password. Please try again.");
                    passwordField.clear();
                    passwordField.requestFocus();
                    loginButton.setDisable(false);
                }

            } catch (Exception e) {
                log.error("Error during authentication", e);
                showError("An error occurred during login. Please try again.");
                loginButton.setDisable(false);
            }
        });
    }

    /**
     * Handle forgot password link click
     */
    @FXML
    public void handleForgotPassword() {
        log.info("Forgot password clicked");

        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Password Reset");
        alert.setHeaderText("Forgot Your Password?");
        alert.setContentText(
            "Please contact your school administrator to reset your password.\n\n" +
            "For security reasons, password resets must be performed by an administrator."
        );

        alert.showAndWait();
    }

    /**
     * Navigate to main application window
     */
    private void navigateToMainApplication() {
        try {
            log.info("Loading main application window...");

            // Get current stage
            Stage stage = (Stage) loginButton.getScene().getWindow();

            // Load main FXML
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/Main.fxml"));
            loader.setControllerFactory(springContext::getBean);

            Parent root = loader.load();

            // Create new scene
            Scene scene = new Scene(root, 1200, 800);

            // Apply light theme stylesheet
            String stylesheet = getClass().getResource("/css/light-theme.css").toExternalForm();
            scene.getStylesheets().add(stylesheet);

            // Set scene on stage
            stage.setScene(scene);
            stage.setTitle("Heronix-Teacher - " + sessionManager.getCurrentTeacherName());
            stage.setMinWidth(800);
            stage.setMinHeight(600);
            stage.setResizable(true);  // Enable window resizing for main application
            stage.centerOnScreen();

            log.info("Main application window loaded successfully");

        } catch (Exception e) {
            log.error("Failed to load main application window", e);
            showError("Failed to load application. Please try again.");
            loginButton.setDisable(false);
            sessionManager.logout();
        }
    }

    /**
     * Show error message
     */
    private void showError(String message) {
        errorLabel.setText(message);
        errorLabel.setVisible(true);
        errorLabel.setManaged(true);
    }

    /**
     * Hide error message
     */
    private void hideError() {
        errorLabel.setVisible(false);
        errorLabel.setManaged(false);
    }
}
