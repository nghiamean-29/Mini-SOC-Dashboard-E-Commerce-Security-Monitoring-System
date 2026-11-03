
package com.yak.minisoc.service;

import com.yak.minisoc.entity.security.AlertType;
import com.yak.minisoc.entity.security.SecurityAlert;
import com.yak.minisoc.entity.security.SecurityEvent;
import com.yak.minisoc.entity.security.SecurityEventType;
import com.yak.minisoc.repository.SecurityAlertRepository;
import com.yak.minisoc.repository.SecurityEventRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DetectionEngineServiceTest {

    private SecurityEventRepository eventRepository;
    private SecurityAlertRepository alertRepository;
    private DetectionEngineService detectionEngine;

    private final Instant now =
        Instant.parse("2026-11-03T09:00:00Z");

    private long nextEventId;

    @BeforeEach
    void setUp() {
        eventRepository = mock(SecurityEventRepository.class);
        alertRepository = mock(SecurityAlertRepository.class);

        detectionEngine = new DetectionEngineService(
            eventRepository,
            alertRepository
        );

        nextEventId = 1L;
    }

    // ==========================================
    // CREATE TEST EVENT
    // ==========================================

    private SecurityEvent createEvent(
        SecurityEventType type,
        String ip,
        Instant time,
        boolean simulated) {

        SecurityEvent event = SecurityEvent.of(
            type,
            time,
            ip,
            "/login"
        );

        event.withMetadata(
            Map.of("simulated", simulated)
        );

        // Simulate an event already saved to DB.
        SecurityEvent savedEvent = spy(event);

        doReturn(nextEventId++)
            .when(savedEvent)
            .getId();

        return savedEvent;
    }

    // ==========================================
    // CREATE MULTIPLE DISTINCT EVENTS
    // ==========================================

    private List<SecurityEvent> createEvents(
        SecurityEventType type,
        String ip,
        int count,
        Instant lastTime,
        boolean simulated) {

        List<SecurityEvent> events = new ArrayList<>();

        for (int i = 0; i < count; i++) {
            events.add(createEvent(
                type,
                ip,
                lastTime.minusSeconds(count - 1L - i),
                simulated
            ));
        }

        return events;
    }

    // ==========================================
    // TEST 1: BELOW THRESHOLD
    // ==========================================

    @Test
    void shouldNotAlertBelowThreshold() {

        List<SecurityEvent> events = createEvents(
            SecurityEventType.LOGIN_FAILED,
            "192.0.2.10",
            4,
            now,
            false
        );

        SecurityEvent trigger = events.getLast();

        when(eventRepository
            .findBySourceIpAndOccurredAtAfterOrderByOccurredAtDesc(
                eq("192.0.2.10"),
                any(Instant.class)
            ))
            .thenReturn(events);

        detectionEngine.analyzeEvent(trigger);

        verify(alertRepository, never())
            .save(any(SecurityAlert.class));
    }

    // ==========================================
    // TEST 2: BRUTE FORCE DETECTED
    // ==========================================

    @Test
    void shouldCreateBruteForceAlert() {

        List<SecurityEvent> events = createEvents(
            SecurityEventType.LOGIN_FAILED,
            "192.0.2.10",
            5,
            now,
            false
        );

        SecurityEvent trigger = events.getLast();

        // All events are created BEFORE stubbing.
        when(eventRepository
            .findBySourceIpAndOccurredAtAfterOrderByOccurredAtDesc(
                eq("192.0.2.10"),
                any(Instant.class)
            ))
            .thenReturn(events);

        when(alertRepository.existsByDedupKey(anyString()))
            .thenReturn(false);

        detectionEngine.analyzeEvent(trigger);

        ArgumentCaptor<SecurityAlert> captor =
            ArgumentCaptor.forClass(SecurityAlert.class);

        verify(alertRepository).save(captor.capture());

        SecurityAlert alert = captor.getValue();

        assertEquals(
            AlertType.BRUTE_FORCE,
            alert.getAlertType()
        );

        assertEquals(
            5,
            alert.getRelatedEventCount()
        );

        assertEquals(
            5,
            alert.getEvents().size()
        );

        assertEquals(
            "BRUTE_FORCE_5_IN_5M",
            alert.getRuleName()
        );
    }

    // ==========================================
    // TEST 3: IGNORE SIMULATED EVENT
    // ==========================================

    @Test
    void shouldIgnoreSimulatedEvent() {

        SecurityEvent event = createEvent(
            SecurityEventType.LOGIN_FAILED,
            "192.0.2.10",
            now,
            true
        );

        detectionEngine.analyzeEvent(event);

        verifyNoInteractions(
            eventRepository,
            alertRepository
        );
    }

    // ==========================================
    // TEST 4: PREVENT DUPLICATE ALERT
    // ==========================================

    @Test
    void shouldNotCreateDuplicateAlert() {

        List<SecurityEvent> events = createEvents(
            SecurityEventType.LOGIN_FAILED,
            "192.0.2.10",
            5,
            now,
            false
        );

        SecurityEvent trigger = events.getLast();

        when(eventRepository
            .findBySourceIpAndOccurredAtAfterOrderByOccurredAtDesc(
                eq("192.0.2.10"),
                any(Instant.class)
            ))
            .thenReturn(events);

        when(alertRepository.existsByDedupKey(anyString()))
            .thenReturn(true);

        detectionEngine.analyzeEvent(trigger);

        verify(alertRepository, never())
            .save(any(SecurityAlert.class));
    }

    // ==========================================
    // TEST 5: UNAUTHORIZED ACCESS
    // ==========================================

    @Test
    void shouldCreateUnauthorizedAccessAlert() {

        List<SecurityEvent> events = createEvents(
            SecurityEventType.ACCESS_DENIED,
            "192.0.2.20",
            3,
            now,
            false
        );

        SecurityEvent trigger = events.getLast();

        when(eventRepository
            .findBySourceIpAndOccurredAtAfterOrderByOccurredAtDesc(
                eq("192.0.2.20"),
                any(Instant.class)
            ))
            .thenReturn(events);

        when(alertRepository.existsByDedupKey(anyString()))
            .thenReturn(false);

        detectionEngine.analyzeEvent(trigger);

        ArgumentCaptor<SecurityAlert> captor =
            ArgumentCaptor.forClass(SecurityAlert.class);

        verify(alertRepository).save(captor.capture());

        assertEquals(
            AlertType.UNAUTHORIZED_ACCESS,
            captor.getValue().getAlertType()
        );

        assertEquals(
            3,
            captor.getValue().getRelatedEventCount()
        );
    }

    // ==========================================
    // TEST 6: REQUEST FLOOD
    // ==========================================

    @Test
    void shouldCreateRequestFloodAlert() {

        List<SecurityEvent> events = createEvents(
            SecurityEventType.SUSPICIOUS_REQUEST,
            "192.0.2.30",
            20,
            now,
            false
        );

        SecurityEvent trigger = events.getLast();

        when(eventRepository
            .findBySourceIpAndOccurredAtAfterOrderByOccurredAtDesc(
                eq("192.0.2.30"),
                any(Instant.class)
            ))
            .thenReturn(events);

        when(alertRepository.existsByDedupKey(anyString()))
            .thenReturn(false);

        detectionEngine.analyzeEvent(trigger);

        ArgumentCaptor<SecurityAlert> captor =
            ArgumentCaptor.forClass(SecurityAlert.class);

        verify(alertRepository).save(captor.capture());

        assertEquals(
            AlertType.REQUEST_FLOOD,
            captor.getValue().getAlertType()
        );

        assertEquals(
            20,
            captor.getValue().getRelatedEventCount()
        );
    }

    @Test
    void shouldNotCreateAlertDuringCooldown() {

        List<SecurityEvent> events = createEvents(
            SecurityEventType.LOGIN_FAILED,
            "192.0.2.10",
            5,
            now,
            false
        );

        SecurityEvent trigger = events.getLast();

        when(eventRepository
            .findBySourceIpAndOccurredAtAfterOrderByOccurredAtDesc(
                eq("192.0.2.10"),
                any(Instant.class)
            ))
            .thenReturn(events);

        SecurityAlert existingAlert = SecurityAlert.raise(
            AlertType.BRUTE_FORCE,
            "192.0.2.10",
            now.minusSeconds(60),
            "existing-alert-key"
        );

        when(alertRepository
            .findFirstByAlertTypeAndSourceIpAndDetectedAtAfterOrderByDetectedAtDesc(
                eq(AlertType.BRUTE_FORCE),
                eq("192.0.2.10"),
                any(Instant.class)
            ))
            .thenReturn(java.util.Optional.of(existingAlert));

        detectionEngine.analyzeEvent(trigger);

        verify(alertRepository, never())
            .save(any(SecurityAlert.class));
    }

}
