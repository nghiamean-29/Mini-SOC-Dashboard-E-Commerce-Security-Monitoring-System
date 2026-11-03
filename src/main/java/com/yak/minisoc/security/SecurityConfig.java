
package com.yak.minisoc.security;

import com.vaadin.flow.spring.security.VaadinSecurityConfigurer;
import com.yak.minisoc.view.LoginView;

import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    // ==========================================
    // PASSWORD ENCODER
    // ==========================================

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    // ==========================================
    // WEB SECURITY CONFIGURATION
    // ==========================================

    @Bean
    @ConditionalOnWebApplication(
        type = ConditionalOnWebApplication.Type.SERVLET
    )
    public SecurityFilterChain securityFilterChain(
        HttpSecurity http) throws Exception {

        // ==========================================
        // URL AUTHORIZATION
        // ==========================================

        http.authorizeHttpRequests(auth -> auth

            // Public endpoints
            .requestMatchers(
                "/register",
                "/error",
                "/css/**",
                "/js/**",
                "/images/**"
            ).permitAll()

            // ADMIN only
            .requestMatchers("/admin/**")
            .hasRole("ADMIN")

            // SOC ANALYST and ADMIN
            .requestMatchers("/soc/**")
            .hasAnyRole("ADMIN", "SOC_ANALYST")

            // CUSTOMER and ADMIN
            .requestMatchers("/cart/**", "/orders/**")
            .hasAnyRole("CUSTOMER", "ADMIN")
        );

        // ==========================================
        // VAADIN SECURITY INTEGRATION
        // ==========================================

        http.with(
            VaadinSecurityConfigurer.vaadin(),
            configurer -> configurer.loginView(LoginView.class)
        );

        // ==========================================
        // SESSION SECURITY
        // ==========================================

        http.sessionManagement(session -> session
            .sessionFixation(fixation -> fixation.changeSessionId())
            .maximumSessions(1)
        );

        // ==========================================
        // SECURITY HEADERS
        // ==========================================

        http.headers(headers -> headers
            .frameOptions(frame -> frame.deny())
        );

        return http.build();
    }
}
