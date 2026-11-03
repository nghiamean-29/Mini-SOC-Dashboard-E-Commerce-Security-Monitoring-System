
package com.yak.minisoc.service;

import com.yak.minisoc.entity.security.AlertType;
import com.yak.minisoc.entity.security.SecurityAlert;
import com.yak.minisoc.entity.security.SecurityEvent;
import com.yak.minisoc.entity.security.SecurityEventType;
import com.yak.minisoc.repository.SecurityAlertRepository;
import com.yak.minisoc.repository.SecurityEventRepository;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DetectionEngineService {

    private final SecurityEventRepository eventRepository;
    private final SecurityAlertRepository alertRepository;

    public DetectionEngineService(
        SecurityEventRepository eventRepository,
        SecurityAlertRepository alertRepository) {

        this.eventRepository = eventRepository;
        this.alertRepository = alertRepository;
    }

    // ==========================================
    // ANALYZE SECURITY EVENT
    // ==========================================

    @Transactional
    public void analyzeEvent(SecurityEvent event) {

        Objects.requireNonNull(event, "event");

        if (event.getId() == null) {
            throw new IllegalArgumentException(
                "SecurityEvent must be saved before detection"
            );
        }

        String sourceIp = event.getSourceIp();

        if (sourceIp == null || sourceIp.isBlank()) {
            return;
        }

        // Ignore simulated demo events.
        if (isSimulated(event)) {
            return;
        }

        if (event.getEventType() == SecurityEventType.LOGIN_FAILED) {

            detect(
                event,
                AlertType.BRUTE_FORCE,
                Duration.ofMinutes(5),
                5,
                "BRUTE_FORCE_5_IN_5M"
            );

        } else if (event.getEventType()
            == SecurityEventType.ACCESS_DENIED) {

            detect(
                event,
                AlertType.UNAUTHORIZED_ACCESS,
                Duration.ofMinutes(5),
                3,
                "ACCESS_DENIED_3_IN_5M"
            );

        } else if (event.getEventType()
            == SecurityEventType.SUSPICIOUS_REQUEST) {

            detect(
                event,
                AlertType.REQUEST_FLOOD,
                Duration.ofMinutes(1),
                20,
                "REQUEST_FLOOD_20_IN_1M"
            );
        }
    }

    // ==========================================
    // DETECTION RULE
    // ==========================================

    private void detect(
        SecurityEvent trigger,
        AlertType alertType,
        Duration window,
        int threshold,
        String ruleName) {

        Instant occurredAt = trigger.getOccurredAt();
        Instant since = occurredAt.minus(window);

        List<SecurityEvent> matchingEvents =
            eventRepository
                .findBySourceIpAndOccurredAtAfterOrderByOccurredAtDesc(
                    trigger.getSourceIp(),
                    since
                )
                .stream()
                .filter(e ->
                    e.getEventType() == trigger.getEventType())
                .filter(e ->
                    !e.getOccurredAt().isAfter(occurredAt))
                .filter(e -> !isSimulated(e))
                .distinct()
                .toList();

        // Not enough events to trigger an alert.
        if (matchingEvents.size() < threshold) {
            return;
        }

        // ======================================
        // COOLDOWN CHECK
        // ======================================

        boolean inCooldown = alertRepository
            .findFirstByAlertTypeAndSourceIpAndDetectedAtAfterOrderByDetectedAtDesc(
                alertType,
                trigger.getSourceIp(),
                since
            )
            .filter(existing ->
                !existing.getDetectedAt().isAfter(occurredAt))
            .isPresent();

        if (inCooldown) {
            return;
        }

        // ======================================
        // DEDUPLICATION KEY
        // ======================================

        long bucket = occurredAt.getEpochSecond()
            / window.getSeconds();

        String dedupKey = alertType.name()
            + "|" + trigger.getSourceIp()
            + "|" + bucket;

        if (alertRepository.existsByDedupKey(dedupKey)) {
            return;
        }

        // ======================================
        // CREATE SECURITY ALERT
        // ======================================

        SecurityAlert alert = SecurityAlert.raise(
            alertType,
            trigger.getSourceIp(),
            occurredAt,
            dedupKey
        );

        alert.setRuleName(ruleName);
        alert.setUser(trigger.getUser());
        alert.setUsername(trigger.getUsername());

        alert.setDescription(
            "Detected " + matchingEvents.size()
                + " " + trigger.getEventType()
                + " events from IP " + trigger.getSourceIp()
                + " within " + window.toMinutes()
                + " minute(s)."
        );

        // Link evidence to the alert.
        matchingEvents.forEach(alert::addEvidence);

        alertRepository.save(alert);
    }

    // ==========================================
    // SIMULATION CHECK
    // ==========================================

    private boolean isSimulated(SecurityEvent event) {

        if (event.getMetadata() == null) {
            return false;
        }

        return Boolean.TRUE.equals(
            event.getMetadata().get("simulated")
        );
    }
}
