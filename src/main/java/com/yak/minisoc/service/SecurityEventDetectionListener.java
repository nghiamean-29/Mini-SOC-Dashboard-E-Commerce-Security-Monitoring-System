
package com.yak.minisoc.service;

import com.yak.minisoc.entity.security.SecurityEvent;
import com.yak.minisoc.repository.SecurityEventRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class SecurityEventDetectionListener {

    private static final Logger log =
        LoggerFactory.getLogger(
            SecurityEventDetectionListener.class
        );

    private final SecurityEventRepository eventRepository;
    private final DetectionEngineService detectionEngine;

    public SecurityEventDetectionListener(
        SecurityEventRepository eventRepository,
        DetectionEngineService detectionEngine) {

        this.eventRepository = eventRepository;
        this.detectionEngine = detectionEngine;
    }

    @TransactionalEventListener(
        phase = TransactionPhase.AFTER_COMMIT
    )
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onSecurityEventRecorded(
        SecurityEventService.SecurityEventRecorded notification) {

        try {
            SecurityEvent event = eventRepository
                .findById(notification.eventId())
                .orElse(null);

            if (event == null) {
                log.warn(
                    "Security event {} not found for detection",
                    notification.eventId()
                );
                return;
            }

            detectionEngine.analyzeEvent(event);

        } catch (Exception exception) {
            log.error(
                "Detection failed for security event {}",
                notification.eventId(),
                exception
            );
        }
    }
}
