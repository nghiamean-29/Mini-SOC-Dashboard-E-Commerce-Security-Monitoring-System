package com.yak.minisoc.service;

import com.yak.minisoc.entity.security.IncidentStatus;
import com.yak.minisoc.entity.security.Severity;
import com.yak.minisoc.repository.SecurityAlertRepository;
import com.yak.minisoc.repository.SecurityEventRepository;
import com.yak.minisoc.repository.SecurityIncidentRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@PreAuthorize("hasAnyRole('ADMIN', 'SOC_ANALYST')")
public class DashboardAnalyticsService {

    private static final int TOP_IP_LIMIT = 10;

    private final SecurityEventRepository events;
    private final SecurityAlertRepository alerts;
    private final SecurityIncidentRepository incidents;

    public DashboardAnalyticsService(
        SecurityEventRepository events,
        SecurityAlertRepository alerts,
        SecurityIncidentRepository incidents
    ) {
        this.events = events;
        this.alerts = alerts;
        this.incidents = incidents;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Analytics getAnalytics(Window window) {
        Instant to = Instant.now().truncatedTo(ChronoUnit.MICROS);
        return load(window, to);
    }

    Analytics load(Window window, Instant to) {
        Instant from = to.minus(window.days, ChronoUnit.DAYS);

        Map<Instant, Long> buckets = new HashMap<>();
        events.countTimelineBetween(from, to, window.bucketName)
            .forEach(row -> buckets.put(Instant.ofEpochSecond(row.getEpochSecond()), row.getTotal()));

        List<Bucket> timeline = new ArrayList<>();
        for (Instant start = from.truncatedTo(window.bucketUnit); start.isBefore(to);
             start = start.plus(1, window.bucketUnit)) {
            timeline.add(new Bucket(start, buckets.getOrDefault(start, 0L)));
        }

        Map<Severity, Long> severityCounts = new HashMap<>();
        events.countBySeverityBetween(from, to)
            .forEach(row -> severityCounts.put(row.getSeverity(), row.getTotal()));
        List<Count> severity = new ArrayList<>();
        for (Severity level : Severity.values()) {
            severity.add(new Count(level.name(), severityCounts.getOrDefault(level, 0L)));
        }
        severity.add(new Count("Unclassified", severityCounts.getOrDefault(null, 0L)));

        List<Count> attackTypes = alerts.countAttackTypesBetween(from, to).stream()
            .map(row -> new Count(row.getAlertType().name(), row.getTotal()))
            .toList();
        List<Count> sourceIps = alerts.findTopSuspiciousIpsBetween(from, to, PageRequest.of(0, TOP_IP_LIMIT))
            .stream()
            .map(row -> new Count(row.getSourceIp(), row.getTotal()))
            .toList();

        Map<IncidentStatus, Long> statusCounts = new HashMap<>();
        incidents.countByStatusBetween(from, to)
            .forEach(row -> statusCounts.put(row.getStatus(), row.getTotal()));
        List<Count> incidentStatuses = new ArrayList<>();
        for (IncidentStatus status : IncidentStatus.values()) {
            incidentStatuses.add(new Count(status.name(), statusCounts.getOrDefault(status, 0L)));
        }

        return new Analytics(from, to, List.copyOf(timeline), List.copyOf(severity),
            attackTypes, sourceIps, List.copyOf(incidentStatuses));
    }

    public enum Window {
        LAST_24_HOURS("Last 24 hours", 1, "hour", ChronoUnit.HOURS),
        LAST_7_DAYS("Last 7 days", 7, "day", ChronoUnit.DAYS);

        private final String label;
        private final int days;
        private final String bucketName;
        private final ChronoUnit bucketUnit;

        Window(String label, int days, String bucketName, ChronoUnit bucketUnit) {
            this.label = label;
            this.days = days;
            this.bucketName = bucketName;
            this.bucketUnit = bucketUnit;
        }

        public String label() {
            return label;
        }
    }

    public record Bucket(Instant start, long total) {}

    public record Count(String label, long total) {}

    public record Analytics(
        Instant from,
        Instant to,
        List<Bucket> timeline,
        List<Count> severity,
        List<Count> attackTypes,
        List<Count> sourceIps,
        List<Count> incidentStatuses
    ) {}
}
