
package com.yak.minisoc.service;
import com.yak.minisoc.entity.User;
import com.yak.minisoc.entity.UserStatus;
import com.yak.minisoc.entity.security.IncidentNote;
import com.yak.minisoc.entity.security.AlertType;
import com.yak.minisoc.entity.security.IncidentType;
import com.yak.minisoc.entity.security.SecurityAlert;
import com.yak.minisoc.entity.security.SecurityIncident;
import com.yak.minisoc.repository.SecurityAlertRepository;
import com.yak.minisoc.repository.SecurityIncidentRepository;
import com.yak.minisoc.repository.UserRepository;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

import org.springframework.data.domain.PageRequest;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@PreAuthorize("hasAnyRole('ADMIN', 'SOC_ANALYST')")
@Transactional
public class IncidentManagementService {

    private static final Duration CORRELATION_WINDOW = Duration.ofMinutes(30);

    private final SecurityIncidentRepository incidentRepository;
    private final SecurityAlertRepository alertRepository;
    private final UserRepository userRepository;

    public IncidentManagementService(
        SecurityIncidentRepository incidentRepository,
        SecurityAlertRepository alertRepository,
        UserRepository userRepository) {

        this.incidentRepository = incidentRepository;
        this.alertRepository = alertRepository;
        this.userRepository = userRepository;
    }

    // ==========================================
    // CREATE OR CORRELATE INCIDENT WITH DEDUPLICATION
    // ==========================================

    public SecurityIncident createIncidentFromAlert(Long alertId) {

        Objects.requireNonNull(alertId, "alertId");

        // 1. Lock the alert to prevent concurrent creation
        SecurityAlert alert = alertRepository.findByIdForUpdate(alertId)
            .orElseThrow(() -> new IllegalArgumentException(
                "Security alert not found: " + alertId));

        // 2. Check whether an incident already exists
        var existingIncident = incidentRepository.findByAlertId(alertId);

        if (existingIncident.isPresent()) {
            return existingIncident.get();
        }

        // 3. Only active alerts can create new incidents
        if (!alert.isActive()) {
            throw new IllegalStateException(
                "Cannot create incident from inactive alert: " + alertId);
        }

        // 4. Map AlertType to IncidentType
        IncidentType incidentType =
            mapIncidentType(alert.getAlertType());

        String sourceIp = alert.getSourceIp();
        String title = incidentType.label();
        if (sourceIp != null && !sourceIp.isBlank()) {
            incidentRepository.lockCorrelationKey(sourceIp, incidentType.name());

            Instant detectedAt = alert.getDetectedAt();
            var candidates = incidentRepository.findCorrelationCandidates(
                sourceIp, incidentType,
                detectedAt.minus(CORRELATION_WINDOW),
                detectedAt.plus(CORRELATION_WINDOW),
                PageRequest.of(0, 1)
            );
            if (!candidates.isEmpty()) {
                SecurityIncident incident = candidates.getFirst();
                incident.linkAlert(alert);
                return incident;
            }
            title += " from " + sourceIp;
        }

        // 6. Create incident
        SecurityIncident incident = SecurityIncident.open(
            incidentType,
            alert.getSeverity(),
            alert.getSourceIp(),
            title,
            alert.getDetectedAt()
        );

        // 7. Generate unique incident code
        long number = incidentRepository.nextIncidentNumber();

        incident.assignIncidentCode(
            "INC-%06d".formatted(number)
        );

        // 8. Link the originating alert
        incident.linkAlert(alert);

        // 9. Save incident to PostgreSQL
        return incidentRepository.save(incident);
    }

    // ==========================================
    // PHASE 7.3 - ASSIGN INCIDENT TO SOC ANALYST
    // ==========================================

    public SecurityIncident assignIncident(
        Long incidentId,
        Long analystId) {

        Objects.requireNonNull(incidentId, "incidentId");
        Objects.requireNonNull(analystId, "analystId");

        // 1. Find the incident
        SecurityIncident incident = incidentRepository
            .findById(incidentId)
            .orElseThrow(() -> new IllegalArgumentException(
                "Security incident not found: " + incidentId
            ));

        // 2. Only active incidents can be assigned
        if (!incident.isActive()) {
            throw new IllegalStateException(
                "Cannot assign inactive incident: " + incidentId
            );
        }

        // 3. Find the analyst
        User analyst = userRepository.findById(analystId)
            .orElseThrow(() -> new IllegalArgumentException(
                "SOC analyst not found: " + analystId
            ));

        // 4. Check account status
        if (analyst.getStatus() != UserStatus.ACTIVE) {
            throw new IllegalStateException(
                "Cannot assign incident to inactive analyst: "
                    + analystId
            );
        }

        // 5. Check SOC_ANALYST role
        if (!analyst.hasRole("SOC_ANALYST")) {
            throw new IllegalArgumentException(
                "User does not have SOC_ANALYST role: "
                    + analystId
            );
        }

        // 6. Assign incident
        incident.assignTo(analyst);

        // 7. Persist changes
        return incidentRepository.save(incident);
    }

    // ==========================================
    // MAP ALERT TYPE TO INCIDENT TYPE
    // ==========================================

    private IncidentType mapIncidentType(AlertType alertType) {

        Objects.requireNonNull(alertType, "alertType");

        return switch (alertType) {

            case BRUTE_FORCE ->
                IncidentType.POSSIBLE_BRUTE_FORCE_ATTACK;

            case REQUEST_FLOOD ->
                IncidentType.POSSIBLE_REQUEST_FLOOD;

            case UNAUTHORIZED_ACCESS ->
                IncidentType.POSSIBLE_UNAUTHORIZED_ACCESS;

            default ->
                throw new IllegalArgumentException(
                    "Unsupported alert type for incident creation: "
                        + alertType);
        };
    }


    // ==========================================
    // PHASE 7.4 - INVESTIGATION WORKFLOW
    // ==========================================

    public SecurityIncident startInvestigation(Long incidentId) {
        Objects.requireNonNull(incidentId, "incidentId");

        SecurityIncident incident = incidentRepository.findById(incidentId)
            .orElseThrow(() -> new IllegalArgumentException(
                "Security incident not found: " + incidentId));

        if (!incident.isActive()) {
            throw new IllegalStateException(
                "Cannot investigate inactive incident: " + incidentId);
        }

        if (incident.getAssignedTo() == null) {
            throw new IllegalStateException(
                "Incident must be assigned before investigation");
        }

        incident.startInvestigation();

        return incidentRepository.save(incident);
    }

    @PreAuthorize("hasRole('SOC_ANALYST')")
    public IncidentNote addInvestigationNote(
        Long incidentId,
        Long authorId,
        String content) {

        Objects.requireNonNull(incidentId, "incidentId");
        Objects.requireNonNull(authorId, "authorId");

        SecurityIncident incident = incidentRepository.findById(incidentId)
            .orElseThrow(() -> new IllegalArgumentException(
                "Security incident not found: " + incidentId));

        if (!incident.isActive()) {
            throw new IllegalStateException(
                "Cannot add note to inactive incident: " + incidentId);
        }

        if (incident.getAssignedTo() == null) {
            throw new IllegalStateException(
                "Incident has no assigned SOC analyst");
        }

        User author = userRepository.findById(authorId)
            .orElseThrow(() -> new IllegalArgumentException(
                "SOC analyst not found: " + authorId));

        if (author.getStatus() != UserStatus.ACTIVE) {
            throw new IllegalStateException(
                "Cannot add note using inactive analyst");
        }

        if (!author.hasRole("SOC_ANALYST")) {
            throw new IllegalArgumentException(
                "User must have SOC_ANALYST role");
        }

        if (incident.getAssignedTo().getId() == null
            || !incident.getAssignedTo().getId().equals(authorId)) {
            throw new IllegalStateException(
                "Only the assigned analyst can add notes");
        }

        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
            || !authentication.getName().equals(author.getUsername())) {
            throw new AccessDeniedException("Investigation notes must use the signed-in analyst.");
        }

        IncidentNote note = incident.addNote(
            author,
            author.getUsername(),
            content
        );

        // IncidentNote is persisted through CascadeType.ALL.
        incidentRepository.save(incident);

        return note;
    }

    public SecurityIncident resolveIncident(
        Long incidentId,
        String resolution) {

        Objects.requireNonNull(incidentId, "incidentId");

        SecurityIncident incident = incidentRepository.findById(incidentId)
            .orElseThrow(() -> new IllegalArgumentException(
                "Security incident not found: " + incidentId));

        if (!incident.isActive()) {
            throw new IllegalStateException(
                "Cannot resolve inactive incident: " + incidentId);
        }

        incident.resolve(resolution, Instant.now());

        return incidentRepository.save(incident);
    }

    public SecurityIncident markFalsePositive(
        Long incidentId,
        String reason) {

        Objects.requireNonNull(incidentId, "incidentId");

        SecurityIncident incident = incidentRepository.findById(incidentId)
            .orElseThrow(() -> new IllegalArgumentException(
                "Security incident not found: " + incidentId));

        if (!incident.isActive()) {
            throw new IllegalStateException(
                "Cannot close inactive incident: " + incidentId);
        }

        incident.markFalsePositive(reason, Instant.now());

        return incidentRepository.save(incident);
    }

}
