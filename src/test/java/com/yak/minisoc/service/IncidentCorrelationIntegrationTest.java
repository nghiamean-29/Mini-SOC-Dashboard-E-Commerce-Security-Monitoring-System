package com.yak.minisoc.service;

import com.yak.minisoc.entity.security.AlertType;
import com.yak.minisoc.entity.security.IncidentStatus;
import com.yak.minisoc.entity.security.SecurityAlert;
import com.yak.minisoc.entity.security.Severity;
import com.yak.minisoc.repository.SecurityAlertRepository;
import com.yak.minisoc.repository.SecurityIncidentRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.concurrent.DelegatingSecurityContextExecutorService;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@WithMockUser(roles = "SOC_ANALYST")
class IncidentCorrelationIntegrationTest {

    private static final Instant START = Instant.parse("2026-11-05T07:00:00Z");
    private static final String SOURCE_IP = "198.19.8.1";

    @Autowired private IncidentManagementService service;
    @Autowired private SecurityAlertRepository alerts;
    @Autowired private SecurityIncidentRepository incidents;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private TransactionTemplate transactions;

    @BeforeEach
    void verifyTestDatabase() {
        assertThat(jdbc.queryForObject("select current_database()", String.class))
            .isEqualTo("minisoc_test");
    }

    @Test
    @Transactional
    void correlatesInvestigatingIncidentAtInclusiveBoundaryAndDeduplicatesRetry() {
        var later = alert(AlertType.BRUTE_FORCE, SOURCE_IP, START.plusSeconds(1800));
        var incident = service.createIncidentFromAlert(later.getId());
        incident.startInvestigation();

        var earlier = SecurityAlert.raise(AlertType.BRUTE_FORCE, SOURCE_IP, START, key());
        earlier.escalateTo(Severity.CRITICAL);
        alerts.saveAndFlush(earlier);

        assertThat(service.createIncidentFromAlert(earlier.getId()).getId())
            .isEqualTo(incident.getId());
        var retry = service.createIncidentFromAlert(earlier.getId());
        assertThat(retry.getId()).isEqualTo(incident.getId());
        assertThat(retry.getStatus()).isEqualTo(IncidentStatus.INVESTIGATING);
        assertThat(retry.getAlertCount()).isEqualTo(2);
        assertThat(retry.getSeverity()).isEqualTo(Severity.CRITICAL);
        incidents.flush();
        assertThat(jdbc.queryForObject(
            "select count(*) from incident_alerts where incident_id = ?", Long.class,
            incident.getId()
        )).isEqualTo(2L);

        // The later alert cannot extend the span past 30 minutes.
        var beyondSpan = alert(AlertType.BRUTE_FORCE, SOURCE_IP, START.plusSeconds(1801));
        assertThat(service.createIncidentFromAlert(beyondSpan.getId()).getId())
            .isNotEqualTo(incident.getId());
    }

    @Test
    @Transactional
    void separatesDifferentIpTypeExpiredWindowAndClosedIncident() {
        var original = alert(AlertType.BRUTE_FORCE, SOURCE_IP, START);
        var incident = service.createIncidentFromAlert(original.getId());

        var otherIp = alert(AlertType.BRUTE_FORCE, "198.19.8.2", START);
        var otherType = alert(AlertType.REQUEST_FLOOD, SOURCE_IP, START);
        var expired = alert(AlertType.BRUTE_FORCE, SOURCE_IP, START.plusSeconds(1801));
        assertThat(service.createIncidentFromAlert(otherIp.getId()).getId())
            .isNotEqualTo(incident.getId());
        assertThat(service.createIncidentFromAlert(otherType.getId()).getId())
            .isNotEqualTo(incident.getId());
        assertThat(service.createIncidentFromAlert(expired.getId()).getId())
            .isNotEqualTo(incident.getId());

        service.resolveIncident(incident.getId(), "Resolved by integration test");
        var afterClose = alert(AlertType.BRUTE_FORCE, SOURCE_IP, START);
        assertThat(service.createIncidentFromAlert(afterClose.getId()).getId())
            .isNotEqualTo(incident.getId());
        assertThat(service.createIncidentFromAlert(original.getId()).getId())
            .isEqualTo(incident.getId());
    }

    @Test
    void correlatesDistinctAlertsConcurrentlyWhenNoIncidentExists() throws Exception {
        var first = alert(AlertType.BRUTE_FORCE, SOURCE_IP, START);
        var second = alert(AlertType.BRUTE_FORCE, SOURCE_IP, START.plusSeconds(1));
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        try (var executor = new DelegatingSecurityContextExecutorService(Executors.newFixedThreadPool(2))) {
            var firstResult = executor.submit(() -> {
                ready.countDown();
                start.await(10, TimeUnit.SECONDS);
                return service.createIncidentFromAlert(first.getId()).getId();
            });
            var secondResult = executor.submit(() -> {
                ready.countDown();
                start.await(10, TimeUnit.SECONDS);
                return service.createIncidentFromAlert(second.getId()).getId();
            });
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            Long incidentId = firstResult.get(20, TimeUnit.SECONDS);
            assertThat(secondResult.get(20, TimeUnit.SECONDS)).isEqualTo(incidentId);
            assertThat(incidents.findById(incidentId).orElseThrow().getAlertCount()).isEqualTo(2);
            assertThat(jdbc.queryForObject(
                "select count(*) from incident_alerts where incident_id = ? and alert_id in (?, ?)",
                Long.class, incidentId, first.getId(), second.getId()
            )).isEqualTo(2L);
        } finally {
            transactions.executeWithoutResult(status -> {
                var incidentIds = jdbc.queryForList(
                    "select distinct incident_id from incident_alerts where alert_id in (?, ?)",
                    Long.class, first.getId(), second.getId()
                );
                jdbc.update("delete from incident_alerts where alert_id in (?, ?)",
                    first.getId(), second.getId());
                for (Long id : incidentIds) {
                    jdbc.update("""
                        delete from security_incidents where id = ?
                          and not exists (select 1 from incident_alerts where incident_id = ?)
                        """, id, id);
                }
                alerts.deleteAllById(List.of(first.getId(), second.getId()));
            });
        }
    }

    private SecurityAlert alert(AlertType type, String ip, Instant detectedAt) {
        return alerts.saveAndFlush(SecurityAlert.raise(type, ip, detectedAt, key()));
    }

    private String key() {
        return "incident-correlation-" + UUID.randomUUID();
    }
}
