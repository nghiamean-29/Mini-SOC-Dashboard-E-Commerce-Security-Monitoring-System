
package com.yak.minisoc.service;

import com.yak.minisoc.repository.SecurityEventRepository;
import com.yak.minisoc.repository.SecurityIncidentRepository;
import com.yak.minisoc.repository.UserRepository;
import com.yak.minisoc.repository.SecurityAlertRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.yak.minisoc.entity.security.AlertStatus;
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

        long activeAlerts =
            alertRepository.countByStatus(AlertStatus.OPEN)
                + alertRepository.countByStatus(AlertStatus.INVESTIGATING);

        return new DashboardStats(
            eventRepository.count(),
            incidentRepository.count(),
            activeAlerts,
            userRepository.count()
        );
    }


    public record DashboardStats(
        long securityEvents,
        long securityIncidents,
        long activeAlerts,
        long users
    ) {}
}
