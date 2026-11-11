package com.yak.minisoc.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.yak.minisoc.entity.security.AlertType;
import com.yak.minisoc.entity.security.IncidentType;
import com.yak.minisoc.entity.security.SecurityAlert;
import com.yak.minisoc.entity.security.SecurityEvent;
import com.yak.minisoc.entity.security.SecurityEventType;
import com.yak.minisoc.entity.security.SecurityIncident;
import com.yak.minisoc.entity.security.Severity;
import com.yak.minisoc.repository.SecurityAlertRepository;
import com.yak.minisoc.repository.SecurityEventRepository;
import com.yak.minisoc.repository.SecurityIncidentRepository;
import com.yak.minisoc.service.DashboardAnalyticsService.Bucket;
import com.yak.minisoc.service.DashboardAnalyticsService.Count;
import com.yak.minisoc.service.DashboardAnalyticsService.Window;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class DashboardAnalyticsIntegrationTest {

    private static final Instant TO = Instant.parse("2040-01-08T00:00:00Z");
    private static final Instant FROM = TO.minus(1, ChronoUnit.DAYS);

    @Autowired
    private SecurityEventRepository events;

    @Autowired
    private SecurityAlertRepository alerts;

    @Autowired
    private SecurityIncidentRepository incidents;

    @Autowired
    private EntityManager entityManager;

    @Test
    void aggregatesBoundedTimelineSeverityAndCurrentIncidentStatus() {
        entityManager.createNativeQuery("SET LOCAL TIME ZONE 'Asia/Bangkok'").executeUpdate();

        saveEvent(FROM.minusSeconds(1), Severity.LOW);
        saveEvent(FROM, Severity.HIGH);
        saveEvent(FROM.plus(2, ChronoUnit.HOURS), null);
        saveEvent(TO, Severity.CRITICAL);

        saveIncident("DASH-OLD", FROM.minusSeconds(1), false);
        saveIncident("DASH-OPEN", FROM, false);
        saveIncident("DASH-FP", TO.minusSeconds(1), true);
        saveIncident("DASH-FUTURE", TO, false);
        entityManager.flush();

        DashboardAnalyticsService service = new DashboardAnalyticsService(events, alerts, incidents);
        var daily = service.load(Window.LAST_24_HOURS, TO);
        assertThat(daily.from()).isEqualTo(FROM);
        assertThat(daily.to()).isEqualTo(TO);
        assertThat(daily.timeline()).hasSize(24);
        assertThat(daily.timeline().subList(0, 3)).containsExactly(
            new Bucket(FROM, 1), new Bucket(FROM.plus(1, ChronoUnit.HOURS), 0),
            new Bucket(FROM.plus(2, ChronoUnit.HOURS), 1));
        assertThat(daily.timeline().stream().mapToLong(Bucket::total).sum()).isEqualTo(2);
        assertThat(daily.severity()).containsExactly(
            new Count("LOW", 0), new Count("MEDIUM", 0), new Count("HIGH", 1),
            new Count("CRITICAL", 0), new Count("Unclassified", 1));
        assertThat(daily.incidentStatuses()).containsExactlyInAnyOrder(
            new Count("OPEN", 1), new Count("INVESTIGATING", 0),
            new Count("RESOLVED", 0), new Count("FALSE_POSITIVE", 1));

        var weekly = service.load(Window.LAST_7_DAYS, TO);
        assertThat(weekly.timeline()).hasSize(7).endsWith(
            new Bucket(FROM.minus(1, ChronoUnit.DAYS), 1), new Bucket(FROM, 2));
    }

    @Test
    void ranksAtMostTenIpsAndUsesAlertsAsAttackEvidence() {
        for (int index = 10; index >= 0; index--) {
            saveAlert(AlertType.BRUTE_FORCE, "203.0.113." + (100 + index), FROM, "dash-" + index, false);
        }
        saveAlert(AlertType.SQL_INJECTION, "203.0.113.110", TO.minusSeconds(1), "dash-repeat", false);
        saveAlert(AlertType.REQUEST_FLOOD, null, FROM, "dash-no-ip", false);
        saveAlert(AlertType.REQUEST_FLOOD, "  ", FROM, "dash-blank-ip", false);
        saveAlert(AlertType.REQUEST_FLOOD, "192.0.2.1", FROM, "dash-false-positive", true);
        saveAlert(AlertType.REQUEST_FLOOD, "192.0.2.2", FROM.minusSeconds(1), "dash-old", false);
        saveAlert(AlertType.REQUEST_FLOOD, "192.0.2.3", TO, "dash-future", false);
        entityManager.flush();

        var analytics = new DashboardAnalyticsService(events, alerts, incidents).load(Window.LAST_24_HOURS, TO);
        assertThat(analytics.attackTypes()).containsExactly(
            new Count("BRUTE_FORCE", 11), new Count("REQUEST_FLOOD", 2), new Count("SQL_INJECTION", 1));
        assertThat(analytics.sourceIps()).hasSize(10)
            .startsWith(new Count("203.0.113.110", 2), new Count("203.0.113.100", 1))
            .endsWith(new Count("203.0.113.108", 1));
    }

    private void saveEvent(Instant when, Severity severity) {
        events.save(SecurityEvent.of(SecurityEventType.LOGIN_FAILED, when, "192.0.2.1", "/login")
            .withSeverityHint(severity));
    }

    private void saveIncident(String code, Instant when, boolean falsePositive) {
        SecurityIncident incident = SecurityIncident.open(IncidentType.POSSIBLE_BRUTE_FORCE_ATTACK,
            Severity.HIGH, "192.0.2.1", "Dashboard test", when);
        incident.assignIncidentCode(code);
        if (falsePositive) {
            incident.markFalsePositive("Expected traffic", when);
        }
        incidents.save(incident);
    }

    private void saveAlert(AlertType type, String ip, Instant when, String key, boolean falsePositive) {
        SecurityAlert alert = SecurityAlert.raise(type, ip, when, key);
        if (falsePositive) {
            alert.markFalsePositive();
        }
        alerts.save(alert);
    }
}
