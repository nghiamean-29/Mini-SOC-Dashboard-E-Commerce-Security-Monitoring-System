package com.yak.minisoc.repository;

import com.yak.minisoc.entity.AuditAction;
import com.yak.minisoc.entity.AuditLog;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Data access for the audit trail.
 *
 * <p>Every method is a read: nothing in the application is allowed to modify or delete an
 * audit entry, so a setter or a {@code delete} call simply does not exist in this
 * interface. The absence is the enforcement - a developer cannot "just fix" a log line
 * without editing this file, which is exactly the moment to stop and think.</p>
 */
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    Page<AuditLog> findAllByOrderByOccurredAtDesc(Pageable pageable);

    Page<AuditLog> findByActionOrderByOccurredAtDesc(AuditAction action, Pageable pageable);

    /** What did this administrator do? (accountability view, threat model T3). */
    Page<AuditLog> findByActorIdOrderByOccurredAtDesc(Long actorId, Pageable pageable);

    /** History of one object: {@code entityType = "Product"}, {@code entityId = "42"}. */
    List<AuditLog> findByEntityTypeAndEntityIdOrderByOccurredAtDesc(String entityType, String entityId);

    List<AuditLog> findByOccurredAtAfterOrderByOccurredAtDesc(Instant since);

    long countByActionAndOccurredAtAfter(AuditAction action, Instant since);
}
