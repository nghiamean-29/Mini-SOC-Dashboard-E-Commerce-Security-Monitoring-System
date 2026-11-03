
package com.yak.minisoc.repository;

import com.yak.minisoc.entity.security.SecurityEvent;
import com.yak.minisoc.entity.security.SecurityEventType;
import com.yak.minisoc.entity.security.Severity;

import org.springframework.data.jpa.domain.Specification;

import jakarta.persistence.criteria.Predicate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class SecurityEventSpecifications {

    private SecurityEventSpecifications() {
    }

    public static Specification<SecurityEvent> filter(
        String sourceIp,
        String username,
        SecurityEventType eventType,
        Severity severity,
        Instant fromTime,
        Instant toTime) {

        return (root, query, cb) -> {

            List<Predicate> predicates =
                new ArrayList<>();

            if (sourceIp != null && !sourceIp.isBlank()) {
                predicates.add(
                    cb.equal(
                        root.get("sourceIp"),
                        sourceIp.trim()
                    )
                );
            }

            if (username != null && !username.isBlank()) {

                String value = username.trim()
                    .toLowerCase(Locale.ROOT);

                // Escape LIKE wildcards supplied by users.
                value = value.replace("\\", "\\\\")
                    .replace("%", "\\%")
                    .replace("_", "\\_");

                predicates.add(
                    cb.like(
                        cb.lower(root.get("username")),
                        "%" + value + "%",
                        '\\'
                    )
                );
            }

            if (eventType != null) {
                predicates.add(
                    cb.equal(
                        root.get("eventType"),
                        eventType
                    )
                );
            }

            if (severity != null) {
                predicates.add(
                    cb.equal(
                        root.get("severityHint"),
                        severity
                    )
                );
            }

            if (fromTime != null) {
                predicates.add(
                    cb.greaterThanOrEqualTo(
                        root.get("occurredAt"),
                        fromTime
                    )
                );
            }

            if (toTime != null) {
                predicates.add(
                    cb.lessThan(
                        root.get("occurredAt"),
                        toTime
                    )
                );
            }

            return cb.and(
                predicates.toArray(new Predicate[0])
            );
        };
    }
}
