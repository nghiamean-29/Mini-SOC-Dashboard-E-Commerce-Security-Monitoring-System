
package com.yak.minisoc.service;

import com.yak.minisoc.entity.security.AlertType;
import com.yak.minisoc.entity.security.SecurityAlert;
import com.yak.minisoc.entity.security.SecurityEventType;
import com.yak.minisoc.repository.SecurityAlertRepository;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class SecurityEventDetectionIntegrationTest {

    @Autowired
    private SecurityEventService eventService;

    @Autowired
    private SecurityAlertRepository alertRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TransactionTemplate transactionTemplate;

    // Unique identifier for each test method.
    private final String testId = UUID.randomUUID().toString();

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
    // CLEANUP AFTER EACH TEST
    // ==========================================

    @AfterEach
    void cleanupTestData() {

        String databaseName = jdbcTemplate.queryForObject(
            "SELECT current_database()",
            String.class
        );

        if (!"minisoc_test".equals(databaseName)) {
            throw new IllegalStateException(
                "Cleanup is allowed only on minisoc_test. "
                    + "Current database: " + databaseName
            );
        }

        transactionTemplate.executeWithoutResult(status -> {

            // Find alerts whose linked events belong
            // exclusively to this test.
            List<Long> alertIds = jdbcTemplate.queryForList("""
                SELECT DISTINCT ae.alert_id
                FROM alert_events ae
                JOIN security_events se
                    ON se.id = ae.event_id
                WHERE se.metadata ->> 'testId' = ?
                  AND NOT EXISTS (
                      SELECT 1
                      FROM alert_events other_ae
                      JOIN security_events other_se
                          ON other_se.id = other_ae.event_id
                      WHERE other_ae.alert_id = ae.alert_id
                        AND (
                            other_se.metadata ->> 'testId'
                        ) IS DISTINCT FROM ?
                  )
                  AND NOT EXISTS (
                      SELECT 1
                      FROM incident_alerts ia
                      WHERE ia.alert_id = ae.alert_id
                  )
                """,
                Long.class,
                testId,
                testId
            );

            // Remove event-alert links created by this test.
            jdbcTemplate.update("""
                DELETE FROM alert_events
                WHERE event_id IN (
                    SELECT id
                    FROM security_events
                    WHERE metadata ->> 'testId' = ?
                )
                """, testId);

            // Remove alerts identified above,
            // provided they have no remaining links.
            for (Long alertId : alertIds) {
                jdbcTemplate.update("""
                    DELETE FROM security_alerts
                    WHERE id = ?
                      AND NOT EXISTS (
                          SELECT 1
                          FROM alert_events
                          WHERE alert_id = ?
                      )
                      AND NOT EXISTS (
                          SELECT 1
                          FROM incident_alerts
                          WHERE alert_id = ?
                      )
                    """, alertId, alertId, alertId);
            }

            // Remove events belonging to this test.
            jdbcTemplate.update("""
                DELETE FROM security_events
                WHERE metadata ->> 'testId' = ?
                """, testId);
        });
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
