
package com.yak.minisoc.repository;

import com.yak.minisoc.entity.security.AlertStatus;
import com.yak.minisoc.entity.security.AlertType;
import com.yak.minisoc.entity.security.SecurityAlert;
import com.yak.minisoc.entity.security.Severity;

import jakarta.persistence.LockModeType;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Data access for security alerts.
 *
 * Supports alert deduplication, cooldown checks,
 * analyst queues and SOC dashboard statistics.
 */
public interface SecurityAlertRepository
    extends JpaRepository<SecurityAlert, Long> {

    // ==========================================
    // ALERT DETAILS
    // ==========================================

    @EntityGraph(attributePaths = "events")
    Optional<SecurityAlert> findWithEventsById(Long id);

    // ==========================================
    // PHASE 7.2 - INCIDENT DEDUPLICATION
    // ==========================================

    /**
     * Locks an alert while creating an incident.
     *
     * Prevents concurrent transactions from
     * processing the same alert simultaneously.
     *
     * Must be called inside a transaction.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        select a from SecurityAlert a
        where a.id = :alertId
        """)
    Optional<SecurityAlert> findByIdForUpdate(
        @Param("alertId") Long alertId
    );

    // ==========================================
    // ALERT DEDUPLICATION
    // ==========================================

    Optional<SecurityAlert> findByDedupKey(String dedupKey);

    boolean existsByDedupKey(String dedupKey);

    // ==========================================
    // COOLDOWN CHECK
    // ==========================================

    /**
     * Checks whether an alert of the same type
     * and source IP already exists after 'since'.
     */
    boolean existsByAlertTypeAndSourceIpAndDetectedAtAfter(
        AlertType alertType,
        String sourceIp,
        Instant since
    );

    /**
     * Finds the most recent alert of the same type
     * and source IP inside the cooldown window.
     *
     * Used by DetectionEngineService to prevent
     * duplicate alerts near time-bucket boundaries.
     */
    Optional<SecurityAlert>
    findFirstByAlertTypeAndSourceIpAndDetectedAtAfterOrderByDetectedAtDesc(
        AlertType alertType,
        String sourceIp,
        Instant since
    );

    // ==========================================
    // ALERT LISTS
    // ==========================================

    Page<SecurityAlert> findAllByOrderByDetectedAtDesc(
        Pageable pageable
    );

    @Query("""
        select a from SecurityAlert a
        where a.status in :statuses
        order by a.severity desc, a.detectedAt desc
        """)
    Page<SecurityAlert> findActiveOrdered(
        @Param("statuses") List<AlertStatus> statuses,
        Pageable pageable
    );

    Page<SecurityAlert> findByStatusOrderByDetectedAtDesc(
        AlertStatus status,
        Pageable pageable
    );

    Page<SecurityAlert> findBySeverityAndStatusOrderByDetectedAtDesc(
        Severity severity,
        AlertStatus status,
        Pageable pageable
    );

    List<SecurityAlert> findBySourceIpOrderByDetectedAtDesc(
        String sourceIp
    );

    List<SecurityAlert> findByDetectedAtAfterOrderByDetectedAtAsc(
        Instant since
    );

    // ==========================================
    // SOC DASHBOARD STATISTICS
    // ==========================================

    long countByStatus(AlertStatus status);

    long countBySeverity(Severity severity);

    long countByAlertTypeAndDetectedAtAfter(
        AlertType alertType,
        Instant since
    );

    long countByDetectedAtAfter(Instant since);

    // ==========================================
    // RULE QUALITY STATISTICS
    // ==========================================

    /**
     * Measures false-positive counts for each
     * detection rule since the given timestamp.
     */
    @Query("""
        select a.ruleName as ruleName,
               count(a) as total,
               sum(case
                   when a.status = com.yak.minisoc.entity.security.AlertStatus.FALSE_POSITIVE
                   then 1 else 0
               end) as falsePositives
        from SecurityAlert a
        where a.detectedAt >= :since
        group by a.ruleName
        order by count(a) desc
        """)
    List<RuleQuality> ruleQualitySince(
        @Param("since") Instant since
    );

    interface RuleQuality {

        String getRuleName();

        long getTotal();

        long getFalsePositives();

        default double falsePositiveRate() {
            return getTotal() == 0
                ? 0.0
                : 100.0 * getFalsePositives() / getTotal();
        }
    }
}
