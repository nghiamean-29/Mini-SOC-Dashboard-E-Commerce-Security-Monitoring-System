package com.yak.minisoc.service;

import com.yak.minisoc.entity.Role;
import com.yak.minisoc.entity.User;
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

import com.yak.minisoc.entity.security.Severity;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
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


    private SecurityIncident createOpenIncident() {
        return SecurityIncident.open(
            IncidentType.POSSIBLE_BRUTE_FORCE_ATTACK,
            createAlert(AlertType.BRUTE_FORCE).getSeverity(),
            "192.168.1.100",
            "Brute force incident",
            detectedAt
        );
    }

    // ==========================================
    // PHASE 7.1 - ORIGINAL TESTS
    // ==========================================

    @Test
    void shouldLinkAndEscalateMatchingIncidentWithoutAllocatingCode() {
        SecurityAlert alert = createAlert(AlertType.BRUTE_FORCE);
        alert.escalateTo(Severity.CRITICAL);
        SecurityIncident incident = createOpenIncident();
        incident.linkAlert(createAlert(AlertType.BRUTE_FORCE));
        mockNewAlert(20L, alert);
        var type = IncidentType.POSSIBLE_BRUTE_FORCE_ATTACK;
        var firstPage = PageRequest.of(0, 1);
        when(incidentRepository.findCorrelationCandidates(
            alert.getSourceIp(), type, detectedAt.minusSeconds(1800),
            detectedAt.plusSeconds(1800), firstPage
        )).thenReturn(List.of(incident));

        assertThat(service.createIncidentFromAlert(20L)).isSameAs(incident);
        assertThat(incident.getSeverity()).isEqualTo(Severity.CRITICAL);
        assertThat(incident.getAlertCount()).isEqualTo(2);
        var order = inOrder(incidentRepository);
        order.verify(incidentRepository).lockCorrelationKey(alert.getSourceIp(), type.name());
        order.verify(incidentRepository).findCorrelationCandidates(
            alert.getSourceIp(), type, detectedAt.minusSeconds(1800),
            detectedAt.plusSeconds(1800), firstPage
        );
        verify(incidentRepository, never()).nextIncidentNumber();
    }

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


    // ==========================================
    // PHASE 7.3 - ASSIGN INCIDENT TESTS
    // ==========================================

    @Test
    void shouldRejectAssigningMissingIncident() {
        when(incidentRepository.findById(999L))
            .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.assignIncident(999L, 10L))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Security incident not found");

        verifyNoInteractions(userRepository);
        verify(incidentRepository, never())
            .save(any(SecurityIncident.class));
    }

    @Test
    void shouldRejectAssigningMissingAnalyst() {
        SecurityIncident incident = createOpenIncident();

        when(incidentRepository.findById(1L))
            .thenReturn(Optional.of(incident));
        when(userRepository.findById(999L))
            .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.assignIncident(1L, 999L))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("SOC analyst not found");

        verify(incidentRepository, never())
            .save(any(SecurityIncident.class));
    }

    @Test
    void shouldRejectNullIncidentId() {
        assertThatThrownBy(() -> service.assignIncident(null, 10L))
            .isInstanceOf(NullPointerException.class);

        verifyNoInteractions(incidentRepository, alertRepository, userRepository);
    }

    @Test
    void shouldRejectNullAnalystId() {
        assertThatThrownBy(() -> service.assignIncident(1L, null))
            .isInstanceOf(NullPointerException.class);

        verifyNoInteractions(incidentRepository, alertRepository, userRepository);
    }

    @Test
    void shouldRejectAssigningResolvedIncident() {
        SecurityIncident incident = createOpenIncident();
        incident.resolve("Investigation completed", detectedAt.plusSeconds(3600));

        when(incidentRepository.findById(1L))
            .thenReturn(Optional.of(incident));

        assertThatThrownBy(() -> service.assignIncident(1L, 10L))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Cannot assign inactive incident");

        verifyNoInteractions(userRepository);
        verify(incidentRepository, never())
            .save(any(SecurityIncident.class));
    }

    @Test
    void shouldRejectAssigningFalsePositiveIncident() {
        SecurityIncident incident = createOpenIncident();
        incident.markFalsePositive("Confirmed false positive", detectedAt.plusSeconds(3600));

        when(incidentRepository.findById(1L))
            .thenReturn(Optional.of(incident));

        assertThatThrownBy(() -> service.assignIncident(1L, 10L))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Cannot assign inactive incident");

        verifyNoInteractions(userRepository);
        verify(incidentRepository, never())
            .save(any(SecurityIncident.class));
    }


    // ==========================================
    // PHASE 7.3 - ANALYST ROLE & STATUS TESTS
    // ==========================================

    @Test
    void shouldAssignIncidentToActiveSocAnalyst() {
        SecurityIncident incident = createOpenIncident();
        User analyst = User.create("soc_analyst", "analyst@example.com", "test-hash", "SOC Analyst");
        analyst.addRole(Role.create("SOC_ANALYST", "SOC Analyst", "Security analyst"));

        when(incidentRepository.findById(1L)).thenReturn(Optional.of(incident));
        when(userRepository.findById(10L)).thenReturn(Optional.of(analyst));
        when(incidentRepository.save(incident)).thenReturn(incident);

        SecurityIncident result = service.assignIncident(1L, 10L);

        assertThat(result).isSameAs(incident);
        assertThat(result.getAssignedTo()).isSameAs(analyst);
        assertThat(result.getStatus()).isEqualTo(IncidentStatus.OPEN);
        verify(incidentRepository).save(incident);
    }

    @Test
    void shouldRejectLockedSocAnalyst() {
        SecurityIncident incident = createOpenIncident();
        User analyst = User.create("locked_analyst", "locked@example.com", "test-hash", "Locked Analyst");
        analyst.addRole(Role.create("SOC_ANALYST", "SOC Analyst", "Security analyst"));
        analyst.lock();

        when(incidentRepository.findById(1L)).thenReturn(Optional.of(incident));
        when(userRepository.findById(10L)).thenReturn(Optional.of(analyst));

        assertThatThrownBy(() -> service.assignIncident(1L, 10L))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("inactive analyst");
        verify(incidentRepository, never()).save(any(SecurityIncident.class));
    }

    @Test
    void shouldRejectDisabledSocAnalyst() {
        SecurityIncident incident = createOpenIncident();
        User analyst = User.create("disabled_analyst", "disabled@example.com", "test-hash", "Disabled Analyst");
        analyst.addRole(Role.create("SOC_ANALYST", "SOC Analyst", "Security analyst"));
        analyst.disable();

        when(incidentRepository.findById(1L)).thenReturn(Optional.of(incident));
        when(userRepository.findById(10L)).thenReturn(Optional.of(analyst));

        assertThatThrownBy(() -> service.assignIncident(1L, 10L))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("inactive analyst");
        verify(incidentRepository, never()).save(any(SecurityIncident.class));
    }

    @Test
    void shouldRejectUserWithoutSocAnalystRole() {
        SecurityIncident incident = createOpenIncident();
        User customer = User.create("customer", "customer@example.com", "test-hash", "Customer");
        customer.addRole(Role.create("CUSTOMER", "Customer", "E-commerce customer"));

        when(incidentRepository.findById(1L)).thenReturn(Optional.of(incident));
        when(userRepository.findById(20L)).thenReturn(Optional.of(customer));

        assertThatThrownBy(() -> service.assignIncident(1L, 20L))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("SOC_ANALYST role");
        verify(incidentRepository, never()).save(any(SecurityIncident.class));
    }

    // ==========================================
    // PHASE 7.4 - INVESTIGATION WORKFLOW TESTS
    // ==========================================

    @Test
    void shouldStartInvestigation() {
        SecurityIncident incident = createOpenIncident();
        User analyst = User.create(
            "analyst", "analyst@example.com", "hash", "SOC Analyst");
        analyst.addRole(Role.create(
            "SOC_ANALYST", "SOC Analyst", "Security analyst"));
        incident.assignTo(analyst);

        when(incidentRepository.findById(1L))
            .thenReturn(Optional.of(incident));
        when(incidentRepository.save(incident))
            .thenReturn(incident);

        SecurityIncident result = service.startInvestigation(1L);

        assertThat(result.getStatus())
            .isEqualTo(IncidentStatus.INVESTIGATING);
        verify(incidentRepository).save(incident);
    }

    @Test
    void shouldRejectInvestigationWithoutAssignment() {
        SecurityIncident incident = createOpenIncident();

        when(incidentRepository.findById(1L))
            .thenReturn(Optional.of(incident));

        assertThatThrownBy(() -> service.startInvestigation(1L))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("must be assigned");

        verify(incidentRepository, never())
            .save(any(SecurityIncident.class));
    }

    @Test
    void shouldRejectInvestigationOfResolvedIncident() {
        SecurityIncident incident = createOpenIncident();
        incident.resolve("Resolved", detectedAt.plusSeconds(3600));

        when(incidentRepository.findById(1L))
            .thenReturn(Optional.of(incident));

        assertThatThrownBy(() -> service.startInvestigation(1L))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("inactive incident");
    }

    @Test
    void shouldRejectMissingIncidentForInvestigation() {
        when(incidentRepository.findById(999L))
            .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.startInvestigation(999L))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Security incident not found");
    }

    @Test
    void shouldResolveIncident() {
        SecurityIncident incident = createOpenIncident();

        when(incidentRepository.findById(1L))
            .thenReturn(Optional.of(incident));
        when(incidentRepository.save(incident))
            .thenReturn(incident);

        SecurityIncident result =
            service.resolveIncident(1L, "Blocked malicious IP");

        assertThat(result.getStatus())
            .isEqualTo(IncidentStatus.RESOLVED);
        assertThat(result.getResolution())
            .isEqualTo("Blocked malicious IP");
        assertThat(result.getResolvedAt()).isNotNull();
    }

    @Test
    void shouldRejectResolvingAlreadyResolvedIncident() {
        SecurityIncident incident = createOpenIncident();
        incident.resolve("Already resolved", detectedAt.plusSeconds(3600));

        when(incidentRepository.findById(1L))
            .thenReturn(Optional.of(incident));

        assertThatThrownBy(
            () -> service.resolveIncident(1L, "Resolve again"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("inactive incident");
    }

    @Test
    void shouldMarkIncidentAsFalsePositive() {
        SecurityIncident incident = createOpenIncident();

        when(incidentRepository.findById(1L))
            .thenReturn(Optional.of(incident));
        when(incidentRepository.save(incident))
            .thenReturn(incident);

        SecurityIncident result =
            service.markFalsePositive(1L, "Legitimate user activity");

        assertThat(result.getStatus())
            .isEqualTo(IncidentStatus.FALSE_POSITIVE);
        assertThat(result.getResolution())
            .isEqualTo("Legitimate user activity");
        assertThat(result.getResolvedAt()).isNotNull();
    }

    @Test
    void shouldRejectFalsePositiveForClosedIncident() {
        SecurityIncident incident = createOpenIncident();
        incident.resolve("Already closed", detectedAt.plusSeconds(3600));

        when(incidentRepository.findById(1L))
            .thenReturn(Optional.of(incident));

        assertThatThrownBy(
            () -> service.markFalsePositive(1L, "False alarm"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("inactive incident");
    }

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    private void signInAsAnalyst() {
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new TestingAuthenticationToken("soc_analyst", null, "ROLE_SOC_ANALYST"));
        SecurityContextHolder.setContext(context);
    }

    @Test
    void shouldRejectBlankInvestigationNote() {
        signInAsAnalyst();
        SecurityIncident incident = createOpenIncident();

        User analyst = org.mockito.Mockito.mock(User.class);
        when(analyst.getId()).thenReturn(10L);
        when(analyst.getStatus())
            .thenReturn(com.yak.minisoc.entity.UserStatus.ACTIVE);
        when(analyst.hasRole("SOC_ANALYST")).thenReturn(true);

        incident.assignTo(analyst);

        when(incidentRepository.findById(1L))
            .thenReturn(Optional.of(incident));
        when(userRepository.findById(10L))
            .thenReturn(Optional.of(analyst));
        when(analyst.getUsername()).thenReturn("soc_analyst");

        assertThatThrownBy(
            () -> service.addInvestigationNote(1L, 10L, "  "))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("note content must not be blank");

        verify(incidentRepository, never())
            .save(any(SecurityIncident.class));
    }

    @Test
    void shouldAddInvestigationNoteSuccessfully() {
        signInAsAnalyst();
        SecurityIncident incident = createOpenIncident();

        User analyst = org.mockito.Mockito.mock(User.class);
        when(analyst.getId()).thenReturn(10L);
        when(analyst.getStatus())
            .thenReturn(com.yak.minisoc.entity.UserStatus.ACTIVE);
        when(analyst.hasRole("SOC_ANALYST")).thenReturn(true);
        when(analyst.getUsername()).thenReturn("soc_analyst");

        incident.assignTo(analyst);

        when(incidentRepository.findById(1L))
            .thenReturn(Optional.of(incident));
        when(userRepository.findById(10L))
            .thenReturn(Optional.of(analyst));

        var note = service.addInvestigationNote(
            1L, 10L, "Suspicious login attempts detected"
        );

        assertThat(note.getContent())
            .isEqualTo("Suspicious login attempts detected");
        assertThat(note.getAuthor()).isSameAs(analyst);
        assertThat(note.getAuthorUsername()).isEqualTo("soc_analyst");
        assertThat(incident.getNotes()).contains(note);

        verify(incidentRepository).save(incident);
    }

    @Test
    void shouldRejectNoteFromUnassignedAnalyst() {
        SecurityIncident incident = createOpenIncident();

        User assignedAnalyst = org.mockito.Mockito.mock(User.class);
        when(assignedAnalyst.getId()).thenReturn(10L);
        incident.assignTo(assignedAnalyst);

        User otherAnalyst = org.mockito.Mockito.mock(User.class);
        when(otherAnalyst.getStatus())
            .thenReturn(com.yak.minisoc.entity.UserStatus.ACTIVE);
        when(otherAnalyst.hasRole("SOC_ANALYST")).thenReturn(true);

        when(incidentRepository.findById(1L))
            .thenReturn(Optional.of(incident));
        when(userRepository.findById(20L))
            .thenReturn(Optional.of(otherAnalyst));

        assertThatThrownBy(() ->
            service.addInvestigationNote(
                1L, 20L, "Unauthorized investigation note"
            )
        )
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Only the assigned analyst");

        verify(incidentRepository, never())
            .save(any(SecurityIncident.class));
    }


}
