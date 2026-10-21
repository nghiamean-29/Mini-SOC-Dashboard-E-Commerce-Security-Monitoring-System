
package com.yak.minisoc.service;

import com.yak.minisoc.entity.security.SecurityEvent;
import com.yak.minisoc.repository.SecurityEventRepository;

import java.time.Instant;
import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SecurityEventService {

    private final SecurityEventRepository eventRepository;

    public SecurityEventService(
        SecurityEventRepository eventRepository) {
        this.eventRepository = eventRepository;
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
}
