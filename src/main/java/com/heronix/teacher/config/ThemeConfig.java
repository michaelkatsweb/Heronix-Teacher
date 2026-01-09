package com.heronix.teacher.config;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

/**
 * Theme configuration for Dark/Light mode support
 *
 * Supports:
 * - Dark theme (default)
 * - Light theme
 * - Runtime theme switching
 *
 * @author EduScheduler Team
 * @version 1.0.0
 */
@Slf4j
@Getter
@Configuration
public class ThemeConfig {

    @Value("${app.theme.default:dark}")
    private String defaultTheme;

    @Value("${app.theme.dark-stylesheet:/css/dark-theme.css}")
    private String darkStylesheet;

    @Value("${app.theme.light-stylesheet:/css/light-theme.css}")
    private String lightStylesheet;

    /**
     * Get stylesheet path for specified theme
     *
     * @param theme Theme name ("dark" or "light")
     * @return Stylesheet path
     */
    public String getStylesheetForTheme(String theme) {
        if ("light".equalsIgnoreCase(theme)) {
            log.debug("Loading light theme stylesheet: {}", lightStylesheet);
            return lightStylesheet;
        } else {
            log.debug("Loading dark theme stylesheet: {}", darkStylesheet);
            return darkStylesheet;
        }
    }

    /**
     * Check if theme is valid
     *
     * @param theme Theme name to check
     * @return true if valid, false otherwise
     */
    public boolean isValidTheme(String theme) {
        return "dark".equalsIgnoreCase(theme) || "light".equalsIgnoreCase(theme);
    }
}
