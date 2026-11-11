
package com.yak.minisoc.service;

import com.yak.minisoc.entity.security.AlertStatus;
import com.yak.minisoc.entity.security.IncidentStatus;
import com.yak.minisoc.entity.security.Severity;
import com.yak.minisoc.repository.SecurityAlertRepository;
import com.yak.minisoc.repository.SecurityEventRepository;
import com.yak.minisoc.repository.SecurityIncidentRepository;
import com.yak.minisoc.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DashboardService {

    private final SecurityEventRepository eventRepository;
    private final SecurityIncidentRepository incidentRepository;
    private final SecurityAlertRepository alertRepository;
    private final UserRepository userRepository;

    public DashboardService(
        SecurityEventRepository eventRepository,
        SecurityIncidentRepository incidentRepository,
        SecurityAlertRepository alertRepository,
        UserRepository userRepository) {

        this.eventRepository = eventRepository;
        this.incidentRepository = incidentRepository;
        this.alertRepository = alertRepository;
        this.userRepository = userRepository;
    }

    // ==========================================
    // PHASE 8.2.1 - SOC DASHBOARD OVERVIEW
    // ==========================================

    @Transactional(readOnly = true)
    public DashboardStats getStats() {

        // 1. Total security events
        long securityEvents = eventRepository.count();

        // 2. Total security incidents
        long securityIncidents = incidentRepository.count();

        // 3. Active security alerts
        long activeAlerts =
            alertRepository.countByStatus(AlertStatus.OPEN)
                + alertRepository.countByStatus(
                AlertStatus.INVESTIGATING
            );

        // 4. Total users
        long users = userRepository.count();

        // 5. Open and investigating incidents
        long openIncidents =
            incidentRepository.countByStatus(IncidentStatus.OPEN)
                + incidentRepository.countByStatus(
                IncidentStatus.INVESTIGATING
            );

        // 6. Critical active incidents
        long criticalIncidents =
            incidentRepository.countBySeverityAndStatus(
                Severity.CRITICAL,
                IncidentStatus.OPEN
            )
                + incidentRepository.countBySeverityAndStatus(
                Severity.CRITICAL,
                IncidentStatus.INVESTIGATING
            );

        // 7. Return dashboard statistics
        return new DashboardStats(
            securityEvents,
            securityIncidents,
            activeAlerts,
            users,
            openIncidents,
            criticalIncidents
        );
    }

    // ==========================================
    // DASHBOARD STATISTICS DTO
    // ==========================================

    public record DashboardStats(
        long securityEvents,
        long securityIncidents,
        long activeAlerts,
        long users,
        long openIncidents,
        long criticalIncidents
    ) {
    }
}
