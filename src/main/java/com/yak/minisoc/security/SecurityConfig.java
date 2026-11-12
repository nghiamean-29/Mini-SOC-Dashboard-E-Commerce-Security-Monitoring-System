
package com.yak.minisoc.security;

import com.vaadin.flow.spring.security.RequestUtil;
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
import org.springframework.security.web.session.HttpSessionEventPublisher;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;

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
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    public HttpSessionEventPublisher httpSessionEventPublisher() {
        return new HttpSessionEventPublisher();
    }

    @Bean
    @ConditionalOnWebApplication(
        type = ConditionalOnWebApplication.Type.SERVLET
    )
    public SecurityFilterChain securityFilterChain(
        HttpSecurity http, RequestUtil vaadinRequests) throws Exception {

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
            .requestMatchers("/admin/**", "/actuator/**")
            .hasRole("ADMIN")

            // SOC ANALYST and ADMIN
            .requestMatchers("/soc/**")
            .hasAnyRole("ADMIN", "SOC_ANALYST")

            // Match the customer-only Vaadin views and commerce services.
            .requestMatchers("/cart/**", "/checkout/**", "/orders/**")
            .hasRole("CUSTOMER")
        );

        // ==========================================
        // VAADIN SECURITY INTEGRATION
        // ==========================================

        var paths = PathPatternRequestMatcher.withDefaults();
        var login = paths.matcher("/login");
        var logout = paths.matcher("/logout");

        // Vaadin's LoginForm sends the Spring CSRF token from the page metadata.
        // Vaadin RPC uses its own CSRF token. Keep that exemption off login and logout.
        http.csrf(csrf -> csrf.ignoringRequestMatchers(request ->
            !login.matches(request) && !logout.matches(request)
                && vaadinRequests.isFrameworkInternalRequest(request)));

        http.with(
            VaadinSecurityConfigurer.vaadin(),
            configurer -> configurer.loginView(LoginView.class)
                .enableCsrfConfiguration(false)
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
