
package com.yak.minisoc.service;

import com.yak.minisoc.entity.security.AlertStatus;
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

    @Transactional(readOnly = true)
    public DashboardStats getStats() {

        long securityEvents = eventRepository.count();

        long securityIncidents = incidentRepository.count();

        long activeAlerts =
            alertRepository.countByStatus(AlertStatus.OPEN)
                + alertRepository.countByStatus(AlertStatus.INVESTIGATING);

        long users = userRepository.count();

        return new DashboardStats(
            securityEvents,
            securityIncidents,
            activeAlerts,
            users
        );
    }

    public record DashboardStats(
        long securityEvents,
        long securityIncidents,
        long activeAlerts,
        long users
    ) {
    }
}
