
package com.yak.minisoc.service;

import com.yak.minisoc.entity.security.AlertType;
import com.yak.minisoc.entity.security.SecurityAlert;
import com.yak.minisoc.entity.security.SecurityIncident;
import com.yak.minisoc.repository.SecurityAlertRepository;
import com.yak.minisoc.repository.SecurityIncidentRepository;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class IncidentDeduplicationIntegrationTest {

    @Autowired
    private IncidentManagementService incidentService;

    @Autowired
    private SecurityAlertRepository alertRepository;

    @Autowired
    private SecurityIncidentRepository incidentRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TransactionTemplate transactionTemplate;

    private final String testId = UUID.randomUUID().toString();

    private Long alertId;

    private SecurityAlert createTestAlert() {

        SecurityAlert alert = SecurityAlert.raise(
            AlertType.BRUTE_FORCE,
            "198.18.0.100",
            Instant.now(),
            "incident-dedup-test-" + testId
        );

        return alertRepository.saveAndFlush(alert);
    }

    private void assertTestDatabase() {

        String database = jdbcTemplate.queryForObject(
            "SELECT current_database()",
            String.class
        );

        if (!"minisoc_test".equals(database)) {
            throw new IllegalStateException(
                "Integration test must use minisoc_test. "
                    + "Current database: " + database
            );
        }
    }

    // ==========================================
    // TEST 1 - SEQUENTIAL DEDUPLICATION
    // ==========================================

    @Test
    void shouldReuseIncidentForSameAlert() {

        assertTestDatabase();

        SecurityAlert alert = createTestAlert();
        alertId = alert.getId();

        SecurityIncident first =
            incidentService.createIncidentFromAlert(alertId);

        SecurityIncident second =
            incidentService.createIncidentFromAlert(alertId);

        assertThat(first.getId()).isNotNull();
        assertThat(second.getId()).isEqualTo(first.getId());

        assertThat(first.getIncidentCode())
            .isEqualTo(second.getIncidentCode());

        assertThat(countIncidentLinks()).isEqualTo(1);
    }

    // ==========================================
    // TEST 2 - CONCURRENT DEDUPLICATION
    // ==========================================

    @Test
    void shouldCreateOnlyOneIncidentConcurrently()
        throws Exception {

        assertTestDatabase();

        SecurityAlert alert = createTestAlert();
        alertId = alert.getId();

        int requestCount = 2;

        CountDownLatch startLatch = new CountDownLatch(1);

        ExecutorService executor =
            Executors.newFixedThreadPool(requestCount);

        List<Future<SecurityIncident>> futures =
            new ArrayList<>();

        try {

            for (int i = 0; i < requestCount; i++) {

                futures.add(executor.submit(() -> {

                    startLatch.await();

                    return incidentService
                        .createIncidentFromAlert(alertId);
                }));
            }

            startLatch.countDown();

            SecurityIncident first =
                futures.get(0).get(30, TimeUnit.SECONDS);

            SecurityIncident second =
                futures.get(1).get(30, TimeUnit.SECONDS);

            assertThat(first.getId()).isNotNull();

            assertThat(second.getId())
                .isEqualTo(first.getId());

            assertThat(second.getIncidentCode())
                .isEqualTo(first.getIncidentCode());

            assertThat(countIncidentLinks()).isEqualTo(1);

        } finally {

            startLatch.countDown();
            executor.shutdownNow();

            if (!executor.awaitTermination(
                30, TimeUnit.SECONDS)) {

                throw new IllegalStateException(
                    "Test executor did not terminate"
                );
            }
        }
    }

    // ==========================================
    // DATABASE VERIFICATION
    // ==========================================

    private long countIncidentLinks() {

        Long count = jdbcTemplate.queryForObject("""
            SELECT COUNT(*)
            FROM incident_alerts
            WHERE alert_id = ?
            """,
            Long.class,
            alertId
        );

        return count == null ? 0 : count;
    }

    // ==========================================
    // SAFE CLEANUP
    // ==========================================

    @AfterEach
    void cleanup() {

        if (alertId == null) {
            return;
        }

        assertTestDatabase();

        transactionTemplate.executeWithoutResult(status -> {

            List<Long> incidentIds =
                jdbcTemplate.queryForList("""
                    SELECT incident_id
                    FROM incident_alerts
                    WHERE alert_id = ?
                    """,
                    Long.class,
                    alertId
                );

            // Delete only links belonging to this test.
            jdbcTemplate.update("""
                DELETE FROM incident_alerts
                WHERE alert_id = ?
                """,
                alertId
            );

            // Delete only incidents linked to this
            // test alert, provided they have no
            // remaining alert associations.
            for (Long incidentId : incidentIds) {

                jdbcTemplate.update("""
                    DELETE FROM security_incidents
                    WHERE id = ?
                      AND NOT EXISTS (
                          SELECT 1
                          FROM incident_alerts
                          WHERE incident_id = ?
                      )
                    """,
                    incidentId,
                    incidentId
                );
            }

            // Delete only the test alert.
            jdbcTemplate.update("""
                DELETE FROM security_alerts
                WHERE id = ?
                  AND dedup_key = ?
                  AND NOT EXISTS (
                      SELECT 1
                      FROM incident_alerts
                      WHERE alert_id = ?
                  )
                """,
                alertId,
                "incident-dedup-test-" + testId,
                alertId
            );
        });
    }
}
