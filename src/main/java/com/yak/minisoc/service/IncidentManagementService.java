
package com.yak.minisoc.service;

import com.yak.minisoc.entity.security.AlertType;
import com.yak.minisoc.entity.security.IncidentType;
import com.yak.minisoc.entity.security.SecurityAlert;
import com.yak.minisoc.entity.security.SecurityIncident;
import com.yak.minisoc.repository.SecurityAlertRepository;
import com.yak.minisoc.repository.SecurityIncidentRepository;
import com.yak.minisoc.repository.UserRepository;

import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class IncidentManagementService {

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
    // PHASE 7.1 - CREATE INCIDENT FROM ALERT
    // ==========================================

    public SecurityIncident createIncidentFromAlert(Long alertId) {

        Objects.requireNonNull(alertId, "alertId");

        // 1. Find the security alert
        SecurityAlert alert = alertRepository.findById(alertId)
            .orElseThrow(() -> new IllegalArgumentException(
                "Security alert not found: " + alertId));

        // 2. Only active alerts can create incidents
        if (!alert.isActive()) {
            throw new IllegalStateException(
                "Cannot create incident from inactive alert: " + alertId);
        }

        // 3. Map AlertType to IncidentType
        IncidentType incidentType =
            mapIncidentType(alert.getAlertType());

        // 4. Generate incident title
        String title = incidentType.label()
            + (alert.getSourceIp() == null
            || alert.getSourceIp().isBlank()
            ? ""
            : " from " + alert.getSourceIp());

        // 5. Create incident
        SecurityIncident incident = SecurityIncident.open(
            incidentType,
            alert.getSeverity(),
            alert.getSourceIp(),
            title,
            alert.getDetectedAt()
        );

        // 6. Generate unique incident code
        long number = incidentRepository.nextIncidentNumber();

        incident.assignIncidentCode(
            "INC-%06d".formatted(number)
        );

        // 7. Link the originating alert
        incident.linkAlert(alert);

        // 8. Save incident to PostgreSQL
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
    // NEXT PHASE 7 FEATURES
    // ==========================================

    // assignIncident()
    // startInvestigation()
    // addInvestigationNote()
    // resolveIncident()
    // markFalsePositive()
}
