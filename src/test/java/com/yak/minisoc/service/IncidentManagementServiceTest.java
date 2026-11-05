
package com.yak.minisoc.service;

import com.yak.minisoc.entity.security.AlertType;
import com.yak.minisoc.entity.security.IncidentStatus;
import com.yak.minisoc.entity.security.IncidentType;
import com.yak.minisoc.entity.security.SecurityAlert;
import com.yak.minisoc.entity.security.SecurityIncident;
import com.yak.minisoc.repository.SecurityAlertRepository;
import com.yak.minisoc.repository.SecurityIncidentRepository;
import com.yak.minisoc.repository.UserRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IncidentManagementServiceTest {

    @Mock
    private SecurityIncidentRepository incidentRepository;

    @Mock
    private SecurityAlertRepository alertRepository;

    @Mock
    private UserRepository userRepository;

    private IncidentManagementService service;

    private final Instant detectedAt =
        Instant.parse("2026-11-05T07:00:00Z");

    @BeforeEach
    void setUp() {
        service = new IncidentManagementService(
            incidentRepository,
            alertRepository,
            userRepository
        );
    }

    private SecurityAlert createAlert(AlertType type) {
        return SecurityAlert.raise(
            type,
            "192.168.1.100",
            detectedAt,
            type.name() + "|192.168.1.100|test"
        );
    }

    /**
     * Mocks the locked alert lookup and indicates
     * that no incident exists for this alert yet.
     */
    private void mockNewAlert(Long alertId, SecurityAlert alert) {
        when(alertRepository.findByIdForUpdate(alertId))
            .thenReturn(Optional.of(alert));

        when(incidentRepository.findByAlertId(alertId))
            .thenReturn(Optional.empty());
    }

    private void mockIncidentSave() {
        when(incidentRepository.nextIncidentNumber())
            .thenReturn(1L);

        when(incidentRepository.save(any(SecurityIncident.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
    }

    // ==========================================
    // PHASE 7.1 - ORIGINAL TESTS
    // ==========================================

    @Test
    void shouldCreateIncidentFromBruteForceAlert() {

        SecurityAlert alert = createAlert(AlertType.BRUTE_FORCE);

        mockNewAlert(1L, alert);
        mockIncidentSave();

        SecurityIncident incident =
            service.createIncidentFromAlert(1L);

        assertThat(incident.getIncidentType())
            .isEqualTo(IncidentType.POSSIBLE_BRUTE_FORCE_ATTACK);

        assertThat(incident.getStatus())
            .isEqualTo(IncidentStatus.OPEN);

        assertThat(incident.getSourceIp())
            .isEqualTo("192.168.1.100");

        assertThat(incident.getDetectedAt())
            .isEqualTo(detectedAt);
    }

    @Test
    void shouldGenerateIncidentCode() {

        SecurityAlert alert = createAlert(AlertType.BRUTE_FORCE);

        mockNewAlert(2L, alert);

        when(incidentRepository.nextIncidentNumber())
            .thenReturn(42L);

        when(incidentRepository.save(any(SecurityIncident.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        SecurityIncident incident =
            service.createIncidentFromAlert(2L);

        assertThat(incident.getIncidentCode())
            .isEqualTo("INC-000042");
    }

    @Test
    void shouldLinkAlertToIncident() {

        SecurityAlert alert = createAlert(AlertType.BRUTE_FORCE);

        mockNewAlert(3L, alert);
        mockIncidentSave();

        SecurityIncident incident =
            service.createIncidentFromAlert(3L);

        assertThat(incident.getAlertCount()).isEqualTo(1);
        assertThat(incident.getAlerts()).containsExactly(alert);

        ArgumentCaptor<SecurityIncident> captor =
            ArgumentCaptor.forClass(SecurityIncident.class);

        verify(incidentRepository).save(captor.capture());

        assertThat(captor.getValue().getAlerts())
            .contains(alert);
    }

    @Test
    void shouldRejectMissingAlert() {

        when(alertRepository.findByIdForUpdate(999L))
            .thenReturn(Optional.empty());

        assertThatThrownBy(
            () -> service.createIncidentFromAlert(999L)
        )
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Security alert not found");

        verify(incidentRepository, never())
            .save(any(SecurityIncident.class));

        verify(incidentRepository, never())
            .findByAlertId(any());
    }

    @Test
    void shouldRejectResolvedAlert() {

        SecurityAlert alert = createAlert(AlertType.BRUTE_FORCE);
        alert.resolve();

        mockNewAlert(4L, alert);

        assertThatThrownBy(
            () -> service.createIncidentFromAlert(4L)
        )
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("inactive alert");

        verify(incidentRepository, never())
            .save(any(SecurityIncident.class));
    }

    @Test
    void shouldCreateRequestFloodIncident() {

        SecurityAlert alert = createAlert(AlertType.REQUEST_FLOOD);

        mockNewAlert(5L, alert);
        mockIncidentSave();

        SecurityIncident incident =
            service.createIncidentFromAlert(5L);

        assertThat(incident.getIncidentType())
            .isEqualTo(IncidentType.POSSIBLE_REQUEST_FLOOD);
    }

    @Test
    void shouldCreateUnauthorizedAccessIncident() {

        SecurityAlert alert =
            createAlert(AlertType.UNAUTHORIZED_ACCESS);

        mockNewAlert(6L, alert);
        mockIncidentSave();

        SecurityIncident incident =
            service.createIncidentFromAlert(6L);

        assertThat(incident.getIncidentType())
            .isEqualTo(IncidentType.POSSIBLE_UNAUTHORIZED_ACCESS);
    }

    @Test
    void shouldRejectNullAlertId() {

        assertThatThrownBy(
            () -> service.createIncidentFromAlert(null)
        )
            .isInstanceOf(NullPointerException.class);

        verifyNoInteractions(
            alertRepository,
            incidentRepository,
            userRepository
        );
    }

    // ==========================================
    // PHASE 7.2 - INCIDENT DEDUPLICATION TESTS
    // ==========================================

    @Test
    void shouldReturnExistingIncidentForSameAlert() {

        SecurityAlert alert = createAlert(AlertType.BRUTE_FORCE);

        SecurityIncident existing = SecurityIncident.open(
            IncidentType.POSSIBLE_BRUTE_FORCE_ATTACK,
            alert.getSeverity(),
            alert.getSourceIp(),
            "Existing brute force incident",
            detectedAt
        );

        existing.assignIncidentCode("INC-000010");
        existing.linkAlert(alert);

        when(alertRepository.findByIdForUpdate(10L))
            .thenReturn(Optional.of(alert));

        when(incidentRepository.findByAlertId(10L))
            .thenReturn(Optional.of(existing));

        SecurityIncident result =
            service.createIncidentFromAlert(10L);

        assertThat(result).isSameAs(existing);
        assertThat(result.getIncidentCode())
            .isEqualTo("INC-000010");

        verify(incidentRepository, never())
            .nextIncidentNumber();

        verify(incidentRepository, never())
            .save(any(SecurityIncident.class));
    }

    @Test
    void shouldReturnExistingIncidentForResolvedAlert() {

        SecurityAlert alert = createAlert(AlertType.BRUTE_FORCE);
        alert.resolve();

        SecurityIncident existing = SecurityIncident.open(
            IncidentType.POSSIBLE_BRUTE_FORCE_ATTACK,
            alert.getSeverity(),
            alert.getSourceIp(),
            "Existing incident",
            detectedAt
        );

        existing.assignIncidentCode("INC-000011");
        existing.linkAlert(alert);

        when(alertRepository.findByIdForUpdate(11L))
            .thenReturn(Optional.of(alert));

        when(incidentRepository.findByAlertId(11L))
            .thenReturn(Optional.of(existing));

        SecurityIncident result =
            service.createIncidentFromAlert(11L);

        assertThat(result).isSameAs(existing);

        verify(incidentRepository, never())
            .save(any(SecurityIncident.class));
    }

    @Test
    void shouldCheckExistingIncidentBeforeGeneratingCode() {

        SecurityAlert alert = createAlert(AlertType.REQUEST_FLOOD);

        SecurityIncident existing = SecurityIncident.open(
            IncidentType.POSSIBLE_REQUEST_FLOOD,
            alert.getSeverity(),
            alert.getSourceIp(),
            "Existing flood incident",
            detectedAt
        );

        existing.assignIncidentCode("INC-000012");
        existing.linkAlert(alert);

        when(alertRepository.findByIdForUpdate(12L))
            .thenReturn(Optional.of(alert));

        when(incidentRepository.findByAlertId(12L))
            .thenReturn(Optional.of(existing));

        SecurityIncident result =
            service.createIncidentFromAlert(12L);

        assertThat(result.getIncidentCode())
            .isEqualTo("INC-000012");

        verify(incidentRepository).findByAlertId(12L);

        verify(incidentRepository, never())
            .nextIncidentNumber();

        verify(incidentRepository, never())
            .save(any(SecurityIncident.class));
    }
}
