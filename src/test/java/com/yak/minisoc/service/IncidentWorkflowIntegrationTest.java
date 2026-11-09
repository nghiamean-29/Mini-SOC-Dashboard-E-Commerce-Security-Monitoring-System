package com.yak.minisoc.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.yak.minisoc.entity.User;
import com.yak.minisoc.entity.security.AlertType;
import com.yak.minisoc.entity.security.IncidentStatus;
import com.yak.minisoc.entity.security.IncidentType;
import com.yak.minisoc.entity.security.SecurityAlert;
import com.yak.minisoc.entity.security.SecurityIncident;
import com.yak.minisoc.repository.RoleRepository;
import com.yak.minisoc.repository.SecurityAlertRepository;
import com.yak.minisoc.repository.SecurityIncidentRepository;
import com.yak.minisoc.repository.UserRepository;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.flyway.autoconfigure.FlywayMigrationStrategy;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.annotation.Rollback;

@DataJpaTest
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import({IncidentManagementService.class, IncidentWorkflowIntegrationTest.DatabaseGuard.class})
@Rollback
class IncidentWorkflowIntegrationTest {

    @Autowired
    private IncidentManagementService service;

    @Autowired
    private SecurityAlertRepository alerts;

    @Autowired
    private SecurityIncidentRepository incidents;

    @Autowired
    private UserRepository users;

    @Autowired
    private RoleRepository roles;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void verifyApplicationDatabase() {
        requireTestDatabase(jdbc);
    }

    @Test
    void persistsResolvedInvestigationWithAlertAssigneeAndNote() {
        SecurityAlert alert = createAlert();
        User analyst = createAnalyst();
        Long incidentId = service.createIncidentFromAlert(alert.getId()).getId();

        SecurityIncident opened = reload(incidentId);
        assertThat(opened.getStatus()).isEqualTo(IncidentStatus.OPEN);
        assertThat(opened.getIncidentCode()).matches("INC-[0-9]{6,}");
        assertThat(opened.getIncidentType()).isEqualTo(IncidentType.POSSIBLE_BRUTE_FORCE_ATTACK);
        assertThat(opened.getSeverity()).isEqualTo(alert.getSeverity());
        assertThat(opened.getAssignedTo()).isNull();

        service.assignIncident(incidentId, analyst.getId());
        assertThat(reload(incidentId).getAssignedTo().getId()).isEqualTo(analyst.getId());

        service.startInvestigation(incidentId);
        assertThat(reload(incidentId).getStatus()).isEqualTo(IncidentStatus.INVESTIGATING);

        String content = "Confirmed repeated failed logins from the alert source.";
        service.addInvestigationNote(incidentId, analyst.getId(), content);
        String resolution = "Blocked the source and verified that the failures stopped.";
        service.resolveIncident(incidentId, resolution);

        SecurityIncident resolved = reload(incidentId);
        assertClosed(resolved, IncidentStatus.RESOLVED, resolution);
        assertThat(resolved.getAssignedTo().getId()).isEqualTo(analyst.getId());
        assertThat(resolved.getAlertCount()).isEqualTo(1);
        assertThat(resolved.getAlerts()).extracting(SecurityAlert::getId).containsExactly(alert.getId());
        assertThat(resolved.getNotes()).singleElement().satisfies(note -> {
            assertThat(note.getId()).isNotNull();
            assertThat(note.getAuthor().getId()).isEqualTo(analyst.getId());
            assertThat(note.getAuthorUsername()).isEqualTo(analyst.getUsername());
            assertThat(note.getContent()).isEqualTo(content);
        });
    }

    @Test
    void persistsFalsePositiveWithDismissalReason() {
        SecurityAlert alert = createAlert();
        User analyst = createAnalyst();
        Long incidentId = service.createIncidentFromAlert(alert.getId()).getId();
        service.assignIncident(incidentId, analyst.getId());
        service.startInvestigation(incidentId);

        String reason = "The source was an authorized security scanner.";
        service.markFalsePositive(incidentId, reason);

        SecurityIncident dismissed = reload(incidentId);
        assertClosed(dismissed, IncidentStatus.FALSE_POSITIVE, reason);
        assertThat(dismissed.getAssignedTo().getId()).isEqualTo(analyst.getId());
        assertThat(dismissed.getAlerts()).extracting(SecurityAlert::getId).containsExactly(alert.getId());
    }

    private SecurityAlert createAlert() {
        return alerts.saveAndFlush(SecurityAlert.raise(
            AlertType.BRUTE_FORCE, "198.18.0.101", Instant.now().minusSeconds(60),
            "incident-workflow-test-" + UUID.randomUUID()));
    }

    private User createAnalyst() {
        String username = "workflow-" + UUID.randomUUID();
        User analyst = User.create(username, username + "@example.test", "{noop}unused", "Workflow Analyst");
        analyst.addRole(roles.findByCode("SOC_ANALYST").orElseThrow());
        return users.saveAndFlush(analyst);
    }

    // Flush real SQL and discard managed entities. The test transaction rolls back its own rows.
    private SecurityIncident reload(Long incidentId) {
        entityManager.flush();
        entityManager.clear();
        return incidents.findById(incidentId).orElseThrow();
    }

    private static void assertClosed(SecurityIncident incident, IncidentStatus status, String resolution) {
        assertThat(incident.getStatus()).isEqualTo(status);
        assertThat(incident.isActive()).isFalse();
        assertThat(incident.getResolution()).isEqualTo(resolution);
        assertThat(incident.getResolvedAt()).isAfterOrEqualTo(incident.getDetectedAt());
    }

    private static void requireTestDatabase(JdbcTemplate jdbc) {
        String database = jdbc.queryForObject("SELECT current_database()", String.class);
        if (!"minisoc_test".equals(database)) {
            throw new IllegalStateException("Expected PostgreSQL database minisoc_test, found " + database);
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class DatabaseGuard {

        @Bean
        FlywayMigrationStrategy testDatabaseMigration() {
            return flyway -> {
                requireTestDatabase(new JdbcTemplate(flyway.getConfiguration().getDataSource()));
                flyway.migrate();
            };
        }
    }
}

