
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

    private void mockIncidentSave() {
        when(incidentRepository.nextIncidentNumber())
            .thenReturn(1L);

        when(incidentRepository.save(any(SecurityIncident.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void shouldCreateIncidentFromBruteForceAlert() {

        SecurityAlert alert = createAlert(AlertType.BRUTE_FORCE);

        when(alertRepository.findById(1L))
            .thenReturn(Optional.of(alert));

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

        when(alertRepository.findById(2L))
            .thenReturn(Optional.of(alert));

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

        when(alertRepository.findById(3L))
            .thenReturn(Optional.of(alert));

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

        when(alertRepository.findById(999L))
            .thenReturn(Optional.empty());

        assertThatThrownBy(
            () -> service.createIncidentFromAlert(999L)
        )
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Security alert not found");

        verify(incidentRepository, never())
            .save(any(SecurityIncident.class));
    }

    @Test
    void shouldRejectResolvedAlert() {

        SecurityAlert alert = createAlert(AlertType.BRUTE_FORCE);
        alert.resolve();

        when(alertRepository.findById(4L))
            .thenReturn(Optional.of(alert));

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

        when(alertRepository.findById(5L))
            .thenReturn(Optional.of(alert));

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

        when(alertRepository.findById(6L))
            .thenReturn(Optional.of(alert));

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
}
