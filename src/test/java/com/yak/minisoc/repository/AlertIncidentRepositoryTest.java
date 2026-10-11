package com.yak.minisoc.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.yak.minisoc.entity.User;
import com.yak.minisoc.entity.security.AlertStatus;
import com.yak.minisoc.entity.security.AlertType;
import com.yak.minisoc.entity.security.IncidentStatus;
import com.yak.minisoc.entity.security.IncidentType;
import com.yak.minisoc.entity.security.SecurityAlert;
import com.yak.minisoc.entity.security.SecurityEvent;
import com.yak.minisoc.entity.security.SecurityEventType;
import com.yak.minisoc.entity.security.SecurityIncident;
import com.yak.minisoc.entity.security.Severity;
import com.yak.minisoc.util.BusinessCodes;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

/**
 * Persistence tests for the SOC workflow: {@link SecurityAlertRepository},
 * {@link SecurityIncidentRepository} and {@link IncidentNoteRepository}.
 *
 * <p>This is where the rules that make the SOC usable are verified: deduplication
 * (a UNIQUE {@code dedup_key}, not a hopeful "check first"), the idempotent alert→incident
 * link, the incident code sequence and the append-only investigation notebook.</p>
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class AlertIncidentRepositoryTest {

    @Autowired
    private SecurityAlertRepository alerts;

    @Autowired
    private SecurityIncidentRepository incidents;

    @Autowired
    private IncidentNoteRepository notes;

    @Autowired
    private SecurityEventRepository events;

    @Autowired
    private UserRepository users;

    @PersistenceContext
    private EntityManager em;

    private SecurityEvent anEvent(String ip) {
        return events.saveAndFlush(SecurityEvent.of(SecurityEventType.LOGIN_FAILED, Instant.now(), ip, "/login"));
    }

    private SecurityAlert aBruteForceAlert(String ip, String bucket) {
        SecurityAlert alert = SecurityAlert.raise(AlertType.BRUTE_FORCE, ip, Instant.now(),
                "BRUTE_FORCE|" + ip + "|" + bucket);
        alert.setRuleName("BruteForceRule");
        return alerts.saveAndFlush(alert);
    }

    @Test
    @DisplayName("an alert keeps its evidence and counts it")
    void alertKeepsEvidence() {
        SecurityAlert alert = SecurityAlert.raise(AlertType.BRUTE_FORCE, "203.0.113.5", Instant.now(),
                "BRUTE_FORCE|203.0.113.5|b1");
        alert.setRuleName("BruteForceRule");
        SecurityEvent first = anEvent("203.0.113.5");
        SecurityEvent second = anEvent("203.0.113.5");
        alert.addEvidence(first);
        alert.addEvidence(first);   // the same event twice must not inflate the count
        alert.addEvidence(second);
        alert.setUsername("victim");
        alerts.saveAndFlush(alert);
        em.clear();

        SecurityAlert reloaded = alerts.findWithEventsById(alert.getId()).orElseThrow();
        assertThat(reloaded.getEvents()).hasSize(2);
        assertThat(reloaded.getRelatedEventCount()).isEqualTo(2);
        assertThat(reloaded.getRuleName()).isEqualTo("BruteForceRule");
        assertThat(reloaded.getStatus()).isEqualTo(AlertStatus.OPEN);
        assertThat(reloaded.isCritical()).isFalse();
    }

    @Test
    @DisplayName("uk_security_alerts_dedup: the cooldown cannot be raced")
    void dedupKeyIsUnique() {
        aBruteForceAlert("203.0.113.5", "same-bucket");

        SecurityAlert duplicate = SecurityAlert.raise(AlertType.BRUTE_FORCE, "203.0.113.5",
                Instant.now(), "BRUTE_FORCE|203.0.113.5|same-bucket");

        assertThatThrownBy(() -> alerts.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("the cooldown lookup and the rule-quality report work")
    void cooldownLookupAndRuleQuality() {
        SecurityAlert alert = aBruteForceAlert("203.0.113.5", "b2");
        aBruteForceAlert("203.0.113.6", "b3").markFalsePositive();

        assertThat(alerts.existsByDedupKey(alert.getDedupKey())).isTrue();
        assertThat(alerts.existsByAlertTypeAndSourceIpAndDetectedAtAfter(AlertType.BRUTE_FORCE,
                "203.0.113.5", Instant.now().minus(1, ChronoUnit.MINUTES))).isTrue();
        assertThat(alerts.findByDedupKey(alert.getDedupKey())).isPresent();

        assertThat(alerts.ruleQualitySince(Instant.now().minus(1, ChronoUnit.HOURS)))
                .singleElement()
                .satisfies(quality -> {
                    assertThat(quality.getTotal()).isEqualTo(2);
                    assertThat(quality.getFalsePositives()).isEqualTo(1);
                    assertThat(quality.falsePositiveRate()).isEqualTo(50.0);
                });
    }

    @Test
    @DisplayName("the alert workflow refuses a second closing action")
    void alertWorkflowIsOneWay() {
        SecurityAlert alert = aBruteForceAlert("203.0.113.7", "b4");

        alert.startInvestigation();
        alert.resolve();
        alerts.saveAndFlush(alert);
        em.clear();

        SecurityAlert reloaded = alerts.findById(alert.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(AlertStatus.RESOLVED);
        assertThat(reloaded.isActive()).isFalse();
        assertThatThrownBy(reloaded::markFalsePositive)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already RESOLVED");
    }

    @Test
    @DisplayName("nextval(): incident codes are unique and fit the column")
    void incidentNumbersComeFromASequence() {
        long first = incidents.nextIncidentNumber();
        long second = incidents.nextIncidentNumber();

        assertThat(second).isGreaterThan(first);
        String code = BusinessCodes.incidentCode(first);
        assertThat(code).matches("INC-\\d{4,}");
        assertThat(code.length()).isLessThanOrEqualTo(BusinessCodes.INCIDENT_CODE_MAX_LENGTH);
    }

    @Test
    @DisplayName("linking an alert twice is idempotent and the counter follows (DB-8)")
    void linkingAlertsIsIdempotent() {
        SecurityIncident incident = SecurityIncident.open(IncidentType.POSSIBLE_BRUTE_FORCE_ATTACK,
                Severity.HIGH, "203.0.113.5", "Possible brute force attack from 203.0.113.5",
                Instant.now());
        incident.assignIncidentCode(BusinessCodes.incidentCode(incidents.nextIncidentNumber()));

        SecurityAlert first = aBruteForceAlert("203.0.113.5", "b5");
        SecurityAlert second = aBruteForceAlert("203.0.113.5", "b6");

        assertThat(incident.linkAlert(first)).isTrue();
        assertThat(incident.linkAlert(first)).as("same alert again").isFalse();
        assertThat(incident.linkAlert(second)).isTrue();
        assertThat(incident.getAlertCount()).isEqualTo(2);

        incidents.saveAndFlush(incident);
        em.clear();

        SecurityIncident reloaded = incidents
                .findWithDetailsByIncidentCode(incident.getIncidentCode()).orElseThrow();
        assertThat(reloaded.getAlerts()).hasSize(2);
        assertThat(reloaded.getAlertCount()).isEqualTo(2);
        assertThat(reloaded.getStatus()).isEqualTo(IncidentStatus.OPEN);
    }

    @Test
    @DisplayName("an incident inherits the highest severity of its alerts, never a lower one")
    void severityIsInheritedAndNeverLowered() {
        SecurityIncident incident = SecurityIncident.open(IncidentType.POSSIBLE_SQL_INJECTION,
                Severity.MEDIUM, "203.0.113.5", "Possible SQL injection", Instant.now());
        incident.assignIncidentCode(BusinessCodes.incidentCode(incidents.nextIncidentNumber()));

        SecurityAlert critical = SecurityAlert.raise(AlertType.SQL_INJECTION, "203.0.113.5",
                Instant.now(), "SQL_INJECTION|203.0.113.5|b7");
        assertThat(critical.getSeverity()).isEqualTo(Severity.CRITICAL);

        incident.linkAlert(critical);
        incident.escalateTo(Severity.LOW);   // must be ignored

        assertThat(incident.getSeverity()).isEqualTo(Severity.CRITICAL);
    }

    @Test
    @DisplayName("the notebook records notes in order; blank notes are refused")
    void notesAreAppendOnly() {
        User analyst = users.saveAndFlush(
                User.create("analyst", "analyst@example.com", "$2a$10$hash", "SOC Analyst"));
        SecurityIncident incident = SecurityIncident.open(IncidentType.MIXED_SUSPICIOUS_ACTIVITY,
                Severity.HIGH, "203.0.113.5", "Mixed suspicious activity", Instant.now());
        incident.assignIncidentCode(BusinessCodes.incidentCode(incidents.nextIncidentNumber()));
        incident.addNote(analyst, "analyst", "  Checked the auth logs: 7 failures from one IP.  ");
        incident.addNote(analyst, "analyst", "The IP belongs to a known scanning range.");
        incidents.saveAndFlush(incident);
        em.clear();

        SecurityIncident reloaded = incidents
                .findWithDetailsByIncidentCode(incident.getIncidentCode()).orElseThrow();
        assertThat(reloaded.getNotes()).hasSize(2);
        assertThat(reloaded.getNotes().get(0).getContent())
                .as("content is trimmed, it never starts with spaces")
                .isEqualTo("Checked the auth logs: 7 failures from one IP.");
        assertThat(reloaded.getNotes().get(0).getAuthorUsername()).isEqualTo("analyst");
        assertThat(reloaded.getNotes().get(0).getCreatedAt()).isNotNull();

        assertThatThrownBy(() -> reloaded.addNote(analyst, "analyst", "   "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("closing requires a text, records the moment and the duration")
    void closingRequiresAResolution() {
        SecurityIncident incident = SecurityIncident.open(IncidentType.POSSIBLE_REQUEST_FLOOD,
                Severity.HIGH, "203.0.113.9", "Possible request flood", Instant.now().minusSeconds(60));
        incident.assignIncidentCode(BusinessCodes.incidentCode(incidents.nextIncidentNumber()));
        incidents.saveAndFlush(incident);

        assertThatThrownBy(() -> incident.resolve("  ", Instant.now()))
                .isInstanceOf(IllegalArgumentException.class);

        Instant closedAt = Instant.now();
        incident.resolve("Blocked the IP at the reverse proxy.", closedAt);
        incidents.saveAndFlush(incident);
        em.clear();

        SecurityIncident reloaded = incidents.findById(incident.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(IncidentStatus.RESOLVED);
        assertThat(reloaded.getResolvedAt()).isNotNull();
        assertThat(reloaded.isResolved()).isTrue();
        assertThat(reloaded.timeToResolution(Instant.now())).isGreaterThan(Duration.ofSeconds(30));
        assertThatThrownBy(() -> reloaded.addNote(null, "x", "too late"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("closed");
    }

    @Test
    @DisplayName("the dashboard counters answer the SOC questions")
    void dashboardCounters() {
        aBruteForceAlert("203.0.113.21", "c1");
        SecurityAlert flood = SecurityAlert.raise(AlertType.REQUEST_FLOOD, "203.0.113.22",
                Instant.now(), "REQUEST_FLOOD|203.0.113.22|c2");
        flood.resolve();
        alerts.saveAndFlush(flood);

        SecurityIncident incident = SecurityIncident.open(IncidentType.POSSIBLE_BRUTE_FORCE_ATTACK,
                Severity.HIGH, "203.0.113.21", "Brute force", Instant.now());
        incident.assignIncidentCode(BusinessCodes.incidentCode(incidents.nextIncidentNumber()));
        incidents.saveAndFlush(incident);

        assertThat(alerts.countByStatus(AlertStatus.OPEN)).isEqualTo(1);
        assertThat(alerts.countByStatus(AlertStatus.RESOLVED)).isEqualTo(1);
        assertThat(alerts.countByAlertTypeAndDetectedAtAfter(AlertType.BRUTE_FORCE,
                Instant.now().minus(1, ChronoUnit.HOURS))).isEqualTo(1);
        assertThat(alerts.findActiveOrdered(java.util.List.of(AlertStatus.OPEN,
                AlertStatus.INVESTIGATING), org.springframework.data.domain.PageRequest.of(0, 10)))
                .singleElement()
                .satisfies(a -> assertThat(a.getAlertType()).isEqualTo(AlertType.BRUTE_FORCE));

        assertThat(incidents.countByStatus(IncidentStatus.OPEN)).isEqualTo(1);
        assertThat(incidents.countBySeverityAndStatus(Severity.HIGH, IncidentStatus.OPEN)).isEqualTo(1);
        assertThat(notes.countByCreatedAtAfter(Instant.now().minus(1, ChronoUnit.HOURS))).isZero();
    }
}
