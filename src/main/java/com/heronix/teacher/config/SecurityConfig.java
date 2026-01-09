package com.heronix.teacher.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Security Configuration
 *
 * Provides password encryption using BCrypt
 * Note: This does NOT enable Spring Security's full authentication framework
 * We only use BCryptPasswordEncoder for password hashing
 *
 * @author Heronix-Teacher Team
 * @version 1.0.0
 */
@Configuration
public class SecurityConfig {

    /**
     * Password encoder bean using BCrypt hashing algorithm
     *
     * BCrypt automatically salts passwords and uses adaptive hashing
     * Default strength: 10 rounds (2^10 iterations)
     *
     * @return PasswordEncoder instance
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
