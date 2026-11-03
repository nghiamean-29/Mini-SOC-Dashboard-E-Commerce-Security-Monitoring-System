
package com.yak.minisoc.service;

import com.yak.minisoc.entity.User;
import com.yak.minisoc.entity.security.SecurityEvent;
import com.yak.minisoc.entity.security.SecurityEventType;
import com.yak.minisoc.repository.SecurityEventRepository;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SecurityEventService {

    private final SecurityEventRepository eventRepository;
    private final ApplicationEventPublisher eventPublisher;

    public SecurityEventService(
        SecurityEventRepository eventRepository,
        ApplicationEventPublisher eventPublisher) {

        this.eventRepository = eventRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional(readOnly = true)
    public List<EventRow> getLatestEvents() {

        return eventRepository
            .findAllByOrderByOccurredAtDesc(
                PageRequest.of(0, 100)
            )
            .getContent()
            .stream()
            .map(this::toRow)
            .toList();
    }

    @Transactional
    public void recordEvent(
        SecurityEventType eventType,
        User user,
        String username,
        String sourceIp,
        String endpoint,
        String description,
        Map<String, Object> metadata) {

        Objects.requireNonNull(eventType, "eventType");

        SecurityEvent event = SecurityEvent.of(
            eventType,
            Instant.now(),
            sourceIp,
            endpoint
        );

        event.by(user, username);
        event.withDescription(description);

        event.withMetadata(
            metadata == null
                ? Map.of()
                : Map.copyOf(metadata)
        );

        SecurityEvent savedEvent = eventRepository.save(event);

        // Publish event ID for processing after transaction commit.
        eventPublisher.publishEvent(
            new SecurityEventRecorded(savedEvent.getId())
        );
    }

    @Transactional
    public void recordOrderCreated(
        User user,
        String username,
        String orderCode) {

        recordEvent(
            SecurityEventType.ORDER_CREATED,
            user,
            username,
            null,
            "/checkout",
            "Customer created order " + orderCode,
            Map.of(
                "orderCode", orderCode,
                "action", "ORDER_CREATED"
            )
        );
    }

    @Transactional
    public void recordOrderCancelled(
        User user,
        String username,
        String orderCode) {

        recordEvent(
            SecurityEventType.ORDER_CANCELLED,
            user,
            username,
            null,
            "/orders",
            "Customer cancelled order " + orderCode,
            Map.of(
                "orderCode", orderCode,
                "action", "ORDER_CANCELLED"
            )
        );
    }

    private EventRow toRow(SecurityEvent event) {

        return new EventRow(
            event.getOccurredAt(),
            event.getEventType() == null
                ? ""
                : event.getEventType().name(),
            event.getSourceIp(),
            event.getSeverityHint() == null
                ? ""
                : event.getSeverityHint().name(),
            event.getUsername(),
            event.getEndpoint(),
            event.getStatusCode(),
            event.getDescription()
        );
    }

    public record EventRow(
        Instant occurredAt,
        String eventType,
        String sourceIp,
        String severity,
        String username,
        String endpoint,
        Integer statusCode,
        String description
    ) {}

    public record SecurityEventRecorded(Long eventId) {}
}
