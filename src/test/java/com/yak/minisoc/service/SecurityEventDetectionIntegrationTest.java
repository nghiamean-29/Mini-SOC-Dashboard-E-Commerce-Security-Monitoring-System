
package com.yak.minisoc.service;

import com.yak.minisoc.entity.security.AlertType;
import com.yak.minisoc.entity.security.SecurityAlert;
import com.yak.minisoc.entity.security.SecurityEventType;
import com.yak.minisoc.repository.SecurityAlertRepository;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class SecurityEventDetectionIntegrationTest {

    @Autowired
    private SecurityEventService eventService;

    @Autowired
    private SecurityAlertRepository alertRepository;

    // Generate a test IP that is unlikely to be reused
    // across Maven test executions.
    private String uniqueIp() {
        int value = ThreadLocalRandom.current()
            .nextInt(1, 65535);

        return "198.18."
            + (value / 256)
            + "."
            + (value % 256);
    }

    private void recordEvents(
        SecurityEventType type,
        String sourceIp,
        int count,
        boolean simulated) {

        String testId = UUID.randomUUID().toString();

        for (int i = 0; i < count; i++) {
            eventService.recordEvent(
                type,
                null,
                "integration-" + testId,
                sourceIp,
                "/security-test",
                "Detection integration test " + i,
                Map.of(
                    "testId", testId,
                    "simulated", simulated
                )
            );
        }
    }

    private List<SecurityAlert> findAlerts(
        String sourceIp,
        AlertType alertType) {

        return alertRepository
            .findBySourceIpOrderByDetectedAtDesc(sourceIp)
            .stream()
            .filter(alert ->
                alert.getAlertType() == alertType)
            .toList();
    }

    // ==========================================
    // TEST 1: BRUTE FORCE
    // ==========================================

    @Test
    void shouldAutomaticallyDetectBruteForce() {

        String ip = uniqueIp();

        recordEvents(
            SecurityEventType.LOGIN_FAILED,
            ip,
            5,
            false
        );

        var alerts = findAlerts(ip, AlertType.BRUTE_FORCE);

        assertThat(alerts)
            .as("Exactly one BRUTE_FORCE alert expected")
            .hasSize(1);

        assertThat(alerts.get(0).getRelatedEventCount())
            .as("BRUTE_FORCE should contain 5 events")
            .isEqualTo(5);
    }

    // ==========================================
    // TEST 2: UNAUTHORIZED ACCESS
    // ==========================================

    @Test
    void shouldDetectUnauthorizedAccess() {

        String ip = uniqueIp();

        recordEvents(
            SecurityEventType.ACCESS_DENIED,
            ip,
            3,
            false
        );

        var alerts = findAlerts(
            ip,
            AlertType.UNAUTHORIZED_ACCESS
        );

        assertThat(alerts)
            .as("Exactly one UNAUTHORIZED_ACCESS alert expected")
            .hasSize(1);

        assertThat(alerts.get(0).getRelatedEventCount())
            .isEqualTo(3);
    }

    // ==========================================
    // TEST 3: REQUEST FLOOD
    // ==========================================

    @Test
    void shouldDetectRequestFlood() {

        String ip = uniqueIp();

        recordEvents(
            SecurityEventType.SUSPICIOUS_REQUEST,
            ip,
            20,
            false
        );

        var alerts = findAlerts(
            ip,
            AlertType.REQUEST_FLOOD
        );

        assertThat(alerts)
            .as("Exactly one REQUEST_FLOOD alert expected")
            .hasSize(1);

        assertThat(alerts.get(0).getRelatedEventCount())
            .isEqualTo(20);
    }

    // ==========================================
    // TEST 4: COOLDOWN
    // ==========================================

    @Test
    void shouldNotCreateDuplicateAlertsDuringCooldown() {

        String ip = uniqueIp();

        recordEvents(
            SecurityEventType.LOGIN_FAILED,
            ip,
            5,
            false
        );

        assertThat(
            findAlerts(ip, AlertType.BRUTE_FORCE)
        ).hasSize(1);

        // More events within the cooldown window.
        recordEvents(
            SecurityEventType.LOGIN_FAILED,
            ip,
            3,
            false
        );

        assertThat(
            findAlerts(ip, AlertType.BRUTE_FORCE)
        )
            .as("Cooldown should prevent duplicate alerts")
            .hasSize(1);
    }

    // ==========================================
    // TEST 5: SIMULATED EVENTS
    // ==========================================

    @Test
    void shouldIgnoreSimulatedEvents() {

        String ip = uniqueIp();

        recordEvents(
            SecurityEventType.LOGIN_FAILED,
            ip,
            6,
            true
        );

        assertThat(
            findAlerts(ip, AlertType.BRUTE_FORCE)
        )
            .as("Simulated events must not create alerts")
            .isEmpty();
    }
}
