
package com.yak.minisoc.service;

import com.yak.minisoc.entity.User;
import com.yak.minisoc.entity.UserStatus;
import com.yak.minisoc.entity.security.SecurityEvent;
import com.yak.minisoc.entity.security.SecurityEventType;
import com.yak.minisoc.entity.security.Severity;
import com.yak.minisoc.repository.SecurityEventRepository;
import com.yak.minisoc.repository.UserRepository;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SecurityEventSimulatorService {

    private final SecurityEventRepository eventRepository;
    private final UserRepository userRepository;

    public SecurityEventSimulatorService(
        SecurityEventRepository eventRepository,
        UserRepository userRepository) {

        this.eventRepository = eventRepository;
        this.userRepository = userRepository;
    }

    // ==========================================
    // GENERATE DEMO SECURITY EVENTS
    // ==========================================

    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public int generateDemoEvents() {

        // 1. Lay tai khoan tu Spring Security
        Authentication authentication =
            SecurityContextHolder.getContext()
                .getAuthentication();

        if (authentication == null
            || !authentication.isAuthenticated()
            || "anonymousUser".equals(authentication.getPrincipal())) {

            throw new SecurityException(
                "Chua xac dinh tai khoan dang nhap."
            );
        }

        String username = authentication.getName();

        // 2. Tim user trong PostgreSQL
        User user = userRepository
            .findWithRolesByUsername(username)
            .orElseThrow(() -> new SecurityException(
                "Tai khoan khong ton tai."
            ));

        // 3. Kiem tra trang thai tai khoan
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new SecurityException(
                "Tai khoan khong con hoat dong."
            );
        }

        // 4. Kiem tra quyen ADMIN
        if (!authentication.getAuthorities().stream()
            .anyMatch(authority ->
                "ROLE_ADMIN".equals(
                    authority.getAuthority()))) {

            throw new SecurityException(
                "Chi ADMIN moi duoc tao su kien demo."
            );
        }

        if (!user.hasRole("ADMIN")) {
            throw new SecurityException(
                "Tai khoan khong co quyen ADMIN."
            );
        }

        // ==========================================
        // SIMULATION DATA
        // ==========================================

        String runId = UUID.randomUUID().toString();
        Instant now = Instant.now();

        // EVENT 1: LOGIN_FAILED
        SecurityEvent failedLogin = SecurityEvent.of(
                SecurityEventType.LOGIN_FAILED,
                now.minusSeconds(180),
                "192.0.2.10",
                "/login"
            )
            .withSeverityHint(Severity.MEDIUM)
            .withDescription(
                "[DEMO] Simulated failed login attempt"
            )
            .withMetadata(Map.of(
                "simulated", true,
                "runId", runId,
                "scenario", "FAILED_LOGIN"
            ));

        // EVENT 2: ACCESS_DENIED
        SecurityEvent accessDenied = SecurityEvent.of(
                SecurityEventType.ACCESS_DENIED,
                now.minusSeconds(120),
                "192.0.2.20",
                "/admin"
            )
            .withSeverityHint(Severity.HIGH)
            .withRequest(
                "GET",
                403,
                "MiniSOC-Demo"
            )
            .withDescription(
                "[DEMO] Simulated unauthorized access"
            )
            .withMetadata(Map.of(
                "simulated", true,
                "runId", runId,
                "scenario", "ACCESS_DENIED"
            ));

        // EVENT 3: SUSPICIOUS_REQUEST
        SecurityEvent suspiciousRequest = SecurityEvent.of(
                SecurityEventType.SUSPICIOUS_REQUEST,
                now.minusSeconds(60),
                "192.0.2.30",
                "/api/products"
            )
            .withSeverityHint(Severity.HIGH)
            .withRequest(
                "GET",
                400,
                "MiniSOC-Demo"
            )
            .withDescription(
                "[DEMO] Simulated suspicious HTTP request"
            )
            .withMetadata(Map.of(
                "simulated", true,
                "runId", runId,
                "scenario", "SUSPICIOUS_REQUEST"
            ));

        // EVENT 4: SIMULATION_RUN
        SecurityEvent simulationRun = SecurityEvent.of(
                SecurityEventType.SIMULATION_RUN,
                now,
                "192.0.2.1",
                "/simulator"
            )
            .withSeverityHint(Severity.LOW)
            .withDescription(
                "[DEMO] Mini SOC simulation batch"
            )
            .withMetadata(Map.of(
                "simulated", true,
                "runId", runId,
                "scenario", "SIMULATION_RUN"
            ));

        // ==========================================
        // SAVE TO POSTGRESQL
        // ==========================================

        List<SecurityEvent> events = List.of(
            failedLogin,
            accessDenied,
            suspiciousRequest,
            simulationRun
        );

        eventRepository.saveAll(events);

        System.out.println(
            "[Mini SOC] Created " + events.size()
                + " demo events | Run ID: " + runId
                + " | User: " + user.getUsername()
        );

        return events.size();
    }
}
