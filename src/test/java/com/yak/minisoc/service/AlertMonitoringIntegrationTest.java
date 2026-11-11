package com.yak.minisoc.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.yak.minisoc.entity.security.AlertStatus;
import com.yak.minisoc.entity.security.AlertType;
import com.yak.minisoc.entity.security.IncidentStatus;
import com.yak.minisoc.entity.security.IncidentType;
import com.yak.minisoc.entity.security.SecurityAlert;
import com.yak.minisoc.entity.security.SecurityEvent;
import com.yak.minisoc.entity.security.SecurityEventType;
import com.yak.minisoc.entity.security.SecurityIncident;
import com.yak.minisoc.entity.security.Severity;
import com.yak.minisoc.repository.SecurityAlertRepository;
import com.yak.minisoc.repository.SecurityEventRepository;
import com.yak.minisoc.repository.SecurityIncidentRepository;
import com.yak.minisoc.security.SecurityConfig;
import com.yak.minisoc.service.AlertMonitoringService.Evidence;
import com.yak.minisoc.service.AlertMonitoringService.Incident;
import com.yak.minisoc.service.AlertMonitoringService.Summary;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import({AlertMonitoringService.class, SecurityConfig.class})
class AlertMonitoringIntegrationTest {

    private static final Instant DETECTED = Instant.parse("2040-01-08T00:00:00Z");
    private static final String IP = "2001:db8:85::1";

    @Autowired
    private AlertMonitoringService service;
    @Autowired
    private SecurityAlertRepository alerts;
    @Autowired
    private SecurityIncidentRepository incidents;
    @Autowired
    private SecurityEventRepository events;
    @Autowired
    private EntityManager entityManager;

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void combinesFiltersAndPaginatesWithStableNewestFirstOrder() {
        authenticate("SOC_ANALYST");
        SecurityAlert oldest = saveAlert("oldest", AlertType.BRUTE_FORCE, IP, DETECTED.minusSeconds(1));
        for (int index = 0; index < AlertMonitoringService.PAGE_SIZE - 2; index++) {
            saveAlert("tie-" + index, AlertType.BRUTE_FORCE, IP, DETECTED);
        }
        SecurityAlert lastTie = saveAlert("last-tie", AlertType.BRUTE_FORCE, IP, DETECTED);
        SecurityAlert newest = saveAlert("newest", AlertType.BRUTE_FORCE, IP, DETECTED.plusSeconds(1));
        saveAlert("other-severity", AlertType.BRUTE_FORCE, IP, DETECTED).escalateTo(Severity.CRITICAL);
        saveAlert("other-status", AlertType.BRUTE_FORCE, IP, DETECTED).resolve();
        saveAlert("other-type", AlertType.REQUEST_FLOOD, IP, DETECTED);
        saveAlert("other-ip", AlertType.BRUTE_FORCE, IP + "0", DETECTED);
        entityManager.flush();
        entityManager.clear();

        var first = service.list(Severity.HIGH, AlertStatus.OPEN, AlertType.BRUTE_FORCE, " " + IP + " ", 0);
        var second = service.list(Severity.HIGH, AlertStatus.OPEN, AlertType.BRUTE_FORCE, IP, 1);

        assertThat(first.getTotalElements()).isEqualTo(26);
        assertThat(first.getTotalPages()).isEqualTo(2);
        assertThat(first.getContent()).hasSize(25).extracting(Summary::id)
            .startsWith(newest.getId(), lastTie.getId()).doesNotContain(oldest.getId());
        assertThat(second.getContent()).extracting(Summary::id).containsExactly(oldest.getId());
        assertThat(service.list(null, null, null, IP, 0).getTotalElements()).isEqualTo(29);
        assertThat(service.list(null, null, null, "2001:db8:85::ffff", 0)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"ADMIN", "SOC_ANALYST"})
    void socRolesCanReadDetailsWithEvidenceAndOptionalIncident(String role) {
        authenticate(role);
        var alert = saveAlert("linked", AlertType.BRUTE_FORCE, IP, DETECTED);
        var unlinked = saveAlert("unlinked", AlertType.REQUEST_FLOOD, null, DETECTED);
        var event = events.save(SecurityEvent.of(SecurityEventType.LOGIN_FAILED, DETECTED, IP, "/login"));
        alert.addEvidence(event);
        alert.recordAdditionalMatch(2);
        alert.setUsername("target-account");
        alert.setRuleName("brute-force-rule");
        alert.setDescription("Repeated login failures");
        var incident = SecurityIncident.open(IncidentType.POSSIBLE_BRUTE_FORCE_ATTACK,
            Severity.HIGH, IP, "Possible brute force", DETECTED);
        incident.assignIncidentCode("INC-850001");
        incident.linkAlert(alert);
        incidents.save(incident);
        entityManager.flush();
        entityManager.clear();

        var details = service.details(alert.getId()).orElseThrow();
        var unlinkedDetails = service.details(unlinked.getId()).orElseThrow();
        entityManager.clear();

        assertThat(details.summary()).isEqualTo(new Summary(alert.getId(), AlertType.BRUTE_FORCE,
            Severity.HIGH, AlertStatus.OPEN, IP, "brute-force-rule", DETECTED));
        assertThat(details.username()).isEqualTo("target-account");
        assertThat(details.description()).isEqualTo("Repeated login failures");
        assertThat(details.relatedEventCount()).isEqualTo(3);
        assertThat(details.dedupKey()).isEqualTo("phase85-linked");
        assertThat(details.evidence()).containsExactly(
            new Evidence(event.getId(), SecurityEventType.LOGIN_FAILED, DETECTED, "/login"));
        assertThat(details.incident()).isEqualTo(new Incident("INC-850001", "Possible brute force",
            IncidentStatus.OPEN));
        assertThat(unlinkedDetails.incident()).isNull();
        assertThat(unlinkedDetails.evidence()).isEmpty();
        assertThat(service.details(Long.MAX_VALUE)).isEmpty();
    }

    @Test
    void rejectsCustomerAndUnauthenticatedAccess() {
        authenticate("CUSTOMER");
        assertThatThrownBy(() -> service.list(null, null, null, null, 0))
            .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.details(1L)).isInstanceOf(AccessDeniedException.class);

        SecurityContextHolder.clearContext();
        assertThatThrownBy(() -> service.list(null, null, null, null, 0))
            .isInstanceOf(AuthenticationCredentialsNotFoundException.class);
        assertThatThrownBy(() -> service.details(1L))
            .isInstanceOf(AuthenticationCredentialsNotFoundException.class);
    }

    private SecurityAlert saveAlert(String key, AlertType type, String ip, Instant detectedAt) {
        return alerts.save(SecurityAlert.raise(type, ip, detectedAt, "phase85-" + key));
    }

    private static void authenticate(String role) {
        SecurityContextHolder.getContext().setAuthentication(
            UsernamePasswordAuthenticationToken.authenticated("operator", null,
                AuthorityUtils.createAuthorityList("ROLE_" + role)));
    }
}
