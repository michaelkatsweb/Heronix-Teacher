package com.heronix.teacher.util;

import com.heronix.teacher.config.ThemeConfig;
import javafx.scene.Scene;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Theme manager utility for runtime theme switching
 *
 * Manages:
 * - Current theme state
 * - Theme switching logic
 * - Stylesheet application
 *
 * @author EduScheduler Team
 * @version 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ThemeManager {

    private final ThemeConfig themeConfig;
    private String currentTheme;

    /**
     * Initialize theme manager with default theme
     */
    public void initialize() {
        currentTheme = themeConfig.getDefaultTheme();
        log.info("Theme manager initialized with default theme: {}", currentTheme);
    }

    /**
     * Get current theme
     *
     * @return Current theme name
     */
    public String getCurrentTheme() {
        if (currentTheme == null) {
            initialize();
        }
        return currentTheme;
    }

    /**
     * Switch to specified theme
     *
     * @param scene JavaFX scene to apply theme to
     * @param theme Theme name ("dark" or "light")
     */
    public void switchTheme(Scene scene, String theme) {
        if (!themeConfig.isValidTheme(theme)) {
            log.warn("Invalid theme requested: {}, using default", theme);
            theme = themeConfig.getDefaultTheme();
        }

        log.info("Switching theme from '{}' to '{}'", currentTheme, theme);

        // Remove all existing stylesheets
        scene.getStylesheets().clear();

        // Add new theme stylesheet
        String stylesheet = themeConfig.getStylesheetForTheme(theme);
        String stylesheetUrl = getClass().getResource(stylesheet).toExternalForm();
        scene.getStylesheets().add(stylesheetUrl);

        currentTheme = theme;
        log.info("Theme switched successfully to: {}", theme);
    }

    /**
     * Toggle between dark and light themes
     *
     * @param scene JavaFX scene to apply theme to
     */
    public void toggleTheme(Scene scene) {
        String newTheme = "dark".equalsIgnoreCase(currentTheme) ? "light" : "dark";
        switchTheme(scene, newTheme);
    }

    /**
     * Apply current theme to a scene
     *
     * @param scene JavaFX scene to apply theme to
     */
    public void applyCurrentTheme(Scene scene) {
        if (currentTheme == null) {
            initialize();
        }
        switchTheme(scene, currentTheme);
    }
}
