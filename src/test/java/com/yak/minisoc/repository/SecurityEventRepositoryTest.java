package com.yak.minisoc.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.yak.minisoc.entity.User;
import com.yak.minisoc.entity.security.SecurityAlert;
import com.yak.minisoc.entity.security.SecurityEvent;
import com.yak.minisoc.entity.security.SecurityEventType;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Persistence tests for {@link SecurityEventRepository} - the table the whole SOC reads.
 *
 * <p>The JSONB test is the interesting one: it proves the mapping
 * ({@code Map<String,Object>} ⇄ {@code jsonb}) really works on PostgreSQL, including the
 * containment query the GIN index of migration V8 was created for.</p>
 */
@DataJpaTest
// Flyway is NOT part of the @DataJpaTest slice in Spring Boot 4: the slice only
// imports the JPA/JDBC auto-configurations, so the migrations must be imported
// explicitly or the schema would simply not exist when Hibernate validates it.
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class SecurityEventRepositoryTest {

    @Autowired
    private SecurityEventRepository events;

    @Autowired
    private SecurityAlertRepository alerts;

    @Autowired
    private UserRepository users;

    @PersistenceContext
    private EntityManager em;

    private SecurityEvent loginFailed(String ip, Instant when) {
        return events.saveAndFlush(SecurityEvent.of(SecurityEventType.LOGIN_FAILED, when, ip, "/login")
                .withDescription("Failed login")
                .withRequest("POST", 401, "curl/8.5.0")
                .withSeverityHint(com.yak.minisoc.entity.security.Severity.MEDIUM));
    }

    @Test
    @DisplayName("an event stores who/what/when and reads back identically")
    void storesAndReadsAnEvent() {
        User user = users.saveAndFlush(User.create("victim", "victim@example.com", "$2a$10$hash", null));
        Instant when = Instant.now().truncatedTo(ChronoUnit.MILLIS);

        SecurityEvent event = events.saveAndFlush(
                SecurityEvent.of(SecurityEventType.LOGIN_SUCCESS, when, "203.0.113.45", "/login")
                        .by(user, "victim")
                        .withRequest("POST", 200, "Mozilla/5.0")
                        .withRequestId("3f1c2b4a-0000-4000-8000-000000000001"));
        em.clear();

        SecurityEvent reloaded = events.findById(event.getId()).orElseThrow();
        assertThat(reloaded.getEventType()).isEqualTo(SecurityEventType.LOGIN_SUCCESS);
        assertThat(reloaded.getOccurredAt()).isEqualTo(when);
        assertThat(reloaded.getSourceIp()).isEqualTo("203.0.113.45");
        assertThat(reloaded.getUser().getUsername()).isEqualTo("victim");
        assertThat(reloaded.getStatusCode()).isEqualTo(200);
        assertThat(reloaded.getRequestId()).isEqualTo("3f1c2b4a-0000-4000-8000-000000000001");
        assertThat(reloaded.isFailedRequest()).isFalse();
        // created_at is technical and filled by Hibernate - it must never be null.
        assertThat(reloaded.getCreatedAt()).isNotNull();
    }

    @Test
    @DisplayName("metadata round-trips through jsonb and can be queried with @> (the GIN index)")
    void metadataIsJsonb() {
        events.saveAndFlush(SecurityEvent.of(SecurityEventType.SIMULATION_RUN, Instant.now(), "198.51.100.7", "/api/simulator")
                .withMetadata(Map.of("rule", "BRUTE_FORCE", "attempts", 7, "simulated", true)));
        events.saveAndFlush(SecurityEvent.of(SecurityEventType.API_REQUEST, Instant.now(), "198.51.100.8", "/products"));
        em.clear();

        List<SecurityEvent> simulated = events.findByMetadataContaining("{\"simulated\": true}");

        assertThat(simulated).hasSize(1);
        SecurityEvent event = simulated.get(0);
        assertThat(event.getEventType()).isEqualTo(SecurityEventType.SIMULATION_RUN);
        assertThat(event.getMetadata()).containsEntry("rule", "BRUTE_FORCE");
        // JSON numbers come back as Integer/Long/Double: assert on the value, not the class.
        assertThat(String.valueOf(event.getMetadata().get("attempts"))).isEqualTo("7");
        assertThat(event.getMetadata().get("simulated")).isEqualTo(true);
    }

    @Test
    @DisplayName("the investigation queries return the right window, newest first")
    void investigationQueries() {
        String attacker = "203.0.113.99";
        Instant base = Instant.now().truncatedTo(ChronoUnit.MILLIS).minus(10, ChronoUnit.MINUTES);
        loginFailed(attacker, base);
        loginFailed(attacker, base.plus(1, ChronoUnit.MINUTES));
        loginFailed(attacker, base.plus(2, ChronoUnit.MINUTES));
        loginFailed("198.51.100.1", base.plus(2, ChronoUnit.MINUTES));

        List<SecurityEvent> window = events.findBySourceIpAndOccurredAtAfterOrderByOccurredAtDesc(
                attacker, base.plus(30, ChronoUnit.SECONDS));

        assertThat(window).hasSize(2);
        assertThat(window.get(0).getOccurredAt()).isAfter(window.get(1).getOccurredAt());
        assertThat(events.countByTypeSince(SecurityEventType.LOGIN_FAILED, base)).isEqualTo(4);
        assertThat(events.countByTypeAndStatusSince(SecurityEventType.LOGIN_FAILED, 401, base)).isEqualTo(4);
    }

    @Test
    @DisplayName("the dashboard aggregations come back as projections")
    void dashboardAggregations() {
        Instant from = Instant.now().minus(1, ChronoUnit.HOURS);
        Instant to = Instant.now().plus(1, ChronoUnit.HOURS);
        loginFailed("203.0.113.10", Instant.now());
        loginFailed("203.0.113.10", Instant.now());
        events.saveAndFlush(SecurityEvent.of(SecurityEventType.API_REQUEST, Instant.now(), "203.0.113.10", "/products"));

        assertThat(events.countByEventTypeBetween(from, to))
                .anySatisfy(row -> {
                    assertThat(row.getEventType()).isEqualTo(SecurityEventType.LOGIN_FAILED);
                    assertThat(row.getTotal()).isEqualTo(2);
                });
        assertThat(events.findTopSourceIpsBetween(from, to, PageRequest.of(0, 5)))
                .singleElement()
                .satisfies(row -> {
                    assertThat(row.getSourceIp()).isEqualTo("203.0.113.10");
                    assertThat(row.getTotal()).isEqualTo(3);
                });
        assertThat(events.countByHourOfDayBetween(from, to))
                .isNotEmpty()
                .allSatisfy(row -> assertThat(row.getHour()).isBetween(0, 23));
        assertThat(events.countByOccurredAtAfter(from)).isEqualTo(3);
    }

    @Test
    @DisplayName("retention: deletion is one bulk DELETE and cascades to alert_events")
    void retentionDeletesOldEventsAndTheirLinks() {
        Instant old = Instant.now().minus(40, ChronoUnit.DAYS);
        SecurityEvent oldEvent = loginFailed("203.0.113.50", old);
        SecurityEvent freshEvent = loginFailed("203.0.113.51", Instant.now());

        SecurityAlert alert = SecurityAlert.raise(
                com.yak.minisoc.entity.security.AlertType.BRUTE_FORCE,
                "203.0.113.50", old, "BRUTE_FORCE|203.0.113.50|bucket-1");
        alert.addEvidence(oldEvent);
        alerts.saveAndFlush(alert);
        em.flush();
        em.clear();

        int deleted = events.deleteOlderThan(Instant.now().minus(30, ChronoUnit.DAYS));
        em.clear();

        assertThat(deleted).isEqualTo(1);
        assertThat(events.findById(oldEvent.getId())).isEmpty();
        assertThat(events.findById(freshEvent.getId())).isPresent();
        // The alert itself stays (it is a judgement), its evidence link disappears.
        assertThat(alerts.findById(alert.getId())).isPresent();
        assertThat(countRows("alert_events")).isZero();
    }

    @Test
    @DisplayName("a failed request for a non-existing user keeps the typed username (no account needed)")
    void anonymousFailuresAreKept() {
        SecurityEvent event = events.saveAndFlush(SecurityEvent.of(SecurityEventType.LOGIN_FAILED,
                        Instant.now(), "203.0.113.77", "/login")
                .by(null, "admin'--")
                .withRequest("POST", 401, null));
        em.clear();

        SecurityEvent reloaded = events.findById(event.getId()).orElseThrow();
        assertThat(reloaded.getUser()).isNull();
        assertThat(reloaded.getUsername()).isEqualTo("admin'--");
        assertThat(reloaded.getUserAgent()).isNull();
    }

    private long countRows(String table) {
        return ((Number) em.createNativeQuery("select count(*) from " + table).getSingleResult()).longValue();
    }
}
