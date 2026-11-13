package com.yak.minisoc.service;

import com.yak.minisoc.entity.User;
import com.yak.minisoc.entity.UserStatus;
import com.yak.minisoc.entity.security.AlertStatus;
import com.yak.minisoc.entity.security.AlertType;
import com.yak.minisoc.entity.security.IncidentStatus;
import com.yak.minisoc.entity.security.IncidentType;
import com.yak.minisoc.entity.security.SecurityAlert;
import com.yak.minisoc.entity.security.SecurityIncident;
import com.yak.minisoc.entity.security.Severity;
import com.yak.minisoc.repository.SecurityIncidentRepository;
import com.yak.minisoc.repository.UserRepository;
import java.io.Serializable;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@PreAuthorize("hasAnyRole('ADMIN', 'SOC_ANALYST')")
@Transactional(readOnly = true)
public class IncidentMonitoringService {

    public static final int PAGE_SIZE = 25;
    public static final int MAX_TEXT_LENGTH = 4000;

    public record Summary(Long id, String code, String title, IncidentType type,
                          Severity severity, IncidentStatus status, String sourceIp,
                          Analyst assignee, int alertCount, Instant detectedAt) implements Serializable {}

    public record Analyst(Long id, String username, String fullName) implements Serializable {
        public String label() {
            return fullName == null || fullName.isBlank()
                ? username : fullName + " (" + username + ")";
        }
    }

    public record Alert(Long id, AlertType type, Severity severity, AlertStatus status,
                        String sourceIp, Instant detectedAt) implements Serializable {}

    public record Note(String author, String content, Instant createdAt) implements Serializable {}

    public record Details(Summary summary, String description, String resolution,
                          Instant resolvedAt, List<Alert> alerts, List<Note> notes,
                          boolean canAddNote) implements Serializable {}

    private final SecurityIncidentRepository incidents;
    private final UserRepository users;
    private final IncidentManagementService management;

    public IncidentMonitoringService(SecurityIncidentRepository incidents, UserRepository users,
                                     IncidentManagementService management) {
        this.incidents = incidents;
        this.users = users;
        this.management = management;
    }

    public Page<Summary> list(IncidentStatus status, Severity severity, int page) {
        return incidents.findForMonitoring(status, severity, PageRequest.of(page, PAGE_SIZE))
            .map(IncidentMonitoringService::summary);
    }

    public Optional<Details> details(String code) {
        User actor = currentActor();
        // Load collections separately in the transaction to avoid multiplying alerts by notes.
        return incidents.findByIncidentCode(code).map(incident -> {
            Summary summary = summary(incident);
            var alerts = incident.getAlerts().stream()
                .sorted(Comparator.comparing(SecurityAlert::getDetectedAt).reversed()
                    .thenComparing(SecurityAlert::getId))
                .map(alert -> new Alert(alert.getId(), alert.getAlertType(), alert.getSeverity(),
                    alert.getStatus(), alert.getSourceIp(), alert.getDetectedAt()))
                .toList();
            var notes = incident.getNotes().stream()
                .map(note -> new Note(note.getAuthorUsername(), note.getContent(), note.getCreatedAt()))
                .toList();
            boolean canAddNote = summary.status().isActive() && summary.assignee() != null
                && summary.assignee().id().equals(actor.getId()) && actor.hasRole("SOC_ANALYST");
            return new Details(summary, incident.getDescription(), incident.getResolution(),
                incident.getResolvedAt(), alerts, notes, canAddNote);
        });
    }

    public List<Analyst> eligibleAnalysts() {
        return users.findSocAnalystsByStatus(UserStatus.ACTIVE).stream()
            .map(IncidentMonitoringService::analyst).toList();
    }

    @Transactional
    public void assign(Long incidentId, Long analystId) {
        currentActor();
        management.assignIncident(incidentId, analystId);
    }

    @Transactional
    public void start(Long incidentId) {
        currentActor();
        management.startInvestigation(incidentId);
    }

    @Transactional
    public void addNote(Long incidentId, String content) {
        User actor = currentActor();
        if (!actor.hasRole("SOC_ANALYST")) {
            throw new AccessDeniedException("Only the assigned SOC analyst can add notes.");
        }
        management.addInvestigationNote(incidentId, actor.getId(), requiredText(content));
    }

    @Transactional
    public void resolve(Long incidentId, String resolution) {
        currentActor();
        management.resolveIncident(incidentId, requiredText(resolution));
    }

    @Transactional
    public void markFalsePositive(Long incidentId, String reason) {
        currentActor();
        management.markFalsePositive(incidentId, requiredText(reason));
    }

    private User currentActor() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new AccessDeniedException("Sign in to manage incidents.");
        }
        return users.findWithRolesByUsername(authentication.getName())
            .filter(user -> user.getStatus() == UserStatus.ACTIVE)
            .filter(user -> user.hasRole("ADMIN") || user.hasRole("SOC_ANALYST"))
            .orElseThrow(() -> new AccessDeniedException("Your account cannot manage incidents."));
    }

    private static String requiredText(String text) {
        if (text == null || text.isBlank() || text.length() > MAX_TEXT_LENGTH || text.indexOf('\0') >= 0) {
            throw new IllegalArgumentException("Enter between 1 and " + MAX_TEXT_LENGTH + " characters without NUL.");
        }
        return text.strip();
    }

    private static Summary summary(SecurityIncident incident) {
        User assignedTo = incident.getAssignedTo();
        return new Summary(incident.getId(), incident.getIncidentCode(), incident.getTitle(),
            incident.getIncidentType(), incident.getSeverity(), incident.getStatus(),
            incident.getSourceIp(), assignedTo == null ? null : analyst(assignedTo),
            incident.getAlertCount(), incident.getDetectedAt());
    }

    private static Analyst analyst(User user) {
        return new Analyst(user.getId(), user.getUsername(), user.getFullName());
    }
}
