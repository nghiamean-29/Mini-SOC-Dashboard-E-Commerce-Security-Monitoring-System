package com.yak.minisoc.service;

import com.yak.minisoc.entity.security.AlertStatus;
import com.yak.minisoc.entity.security.AlertType;
import com.yak.minisoc.entity.security.IncidentStatus;
import com.yak.minisoc.entity.security.SecurityAlert;
import com.yak.minisoc.entity.security.SecurityEvent;
import com.yak.minisoc.entity.security.SecurityEventType;
import com.yak.minisoc.entity.security.Severity;
import com.yak.minisoc.repository.SecurityAlertRepository;
import com.yak.minisoc.repository.SecurityIncidentRepository;
import java.io.Serializable;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@PreAuthorize("hasAnyRole('ADMIN', 'SOC_ANALYST')")
@Transactional(readOnly = true)
public class AlertMonitoringService {

    public static final int PAGE_SIZE = 25;

    public record Summary(Long id, AlertType type, Severity severity, AlertStatus status,
                          String sourceIp, String ruleName, Instant detectedAt) implements Serializable {}

    public record Incident(String code, String title, IncidentStatus status) implements Serializable {}

    public record Evidence(Long id, SecurityEventType type, Instant occurredAt,
                           String endpoint) implements Serializable {}

    public record Details(Summary summary, Long userId, String username, String description,
                          int relatedEventCount, String dedupKey, List<Evidence> evidence,
                          Incident incident) implements Serializable {}

    private final SecurityAlertRepository alerts;
    private final SecurityIncidentRepository incidents;

    public AlertMonitoringService(SecurityAlertRepository alerts, SecurityIncidentRepository incidents) {
        this.alerts = alerts;
        this.incidents = incidents;
    }

    public Page<Summary> list(Severity severity, AlertStatus status, AlertType type, String sourceIp, int page) {
        String ip = sourceIp == null || sourceIp.isBlank() ? null : sourceIp.strip();
        return alerts.findForMonitoring(severity, status, type, ip, PageRequest.of(page, PAGE_SIZE))
            .map(AlertMonitoringService::summary);
    }

    public Optional<Details> details(Long alertId) {
        return alerts.findWithEventsById(alertId).map(alert -> {
            Incident incident = incidents.findByAlertId(alertId)
                .map(linked -> new Incident(linked.getIncidentCode(), linked.getTitle(), linked.getStatus()))
                .orElse(null);
            var evidence = alert.getEvents().stream()
                .sorted(Comparator.comparing(SecurityEvent::getOccurredAt).reversed()
                    .thenComparing(SecurityEvent::getId))
                .map(event -> new Evidence(event.getId(), event.getEventType(), event.getOccurredAt(),
                    event.getEndpoint()))
                .toList();
            Long userId = alert.getUser() == null ? null : alert.getUser().getId();
            return new Details(summary(alert), userId, alert.getUsername(), alert.getDescription(),
                alert.getRelatedEventCount(), alert.getDedupKey(), evidence, incident);
        });
    }

    private static Summary summary(SecurityAlert alert) {
        return new Summary(alert.getId(), alert.getAlertType(), alert.getSeverity(), alert.getStatus(),
            alert.getSourceIp(), alert.getRuleName(), alert.getDetectedAt());
    }
}
