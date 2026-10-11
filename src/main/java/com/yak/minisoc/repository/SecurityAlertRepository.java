package com.yak.minisoc.repository;

import com.yak.minisoc.entity.security.AlertStatus;
import com.yak.minisoc.entity.security.AlertType;
import com.yak.minisoc.entity.security.SecurityAlert;
import com.yak.minisoc.entity.security.Severity;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Data access for alerts.
 *
 * <p>The interesting part of this interface is {@link #findByDedupKey(String)} and the
 * {@code existsByDedupKey...} variants: they are what the cooldown logic of Phase 7 calls
 * <em>before</em> inserting. They do not replace the UNIQUE constraint on
 * {@code dedup_key} - they make the normal path fast, while the constraint remains the
 * guarantee that survives two simultaneous requests.</p>
 */
public interface SecurityAlertRepository extends JpaRepository<SecurityAlert, Long> {

    /** Alert detail page: the alert with the evidence that produced it. */
    @EntityGraph(attributePaths = "events")
    Optional<SecurityAlert> findWithEventsById(Long id);

    Optional<SecurityAlert> findByDedupKey(String dedupKey);

    boolean existsByDedupKey(String dedupKey);

    /** Cooldown check: is there already an alert of this type from this IP since :since? */
    boolean existsByAlertTypeAndSourceIpAndDetectedAtAfter(AlertType alertType, String sourceIp,
                                                           Instant since);

    Page<SecurityAlert> findAllByOrderByDetectedAtDesc(Pageable pageable);

    /** The analyst's working queue: only alerts that still need attention. */
    @Query("""
            select a from SecurityAlert a
            where a.status in :statuses
            order by a.severity desc, a.detectedAt desc
            """)
    Page<SecurityAlert> findActiveOrdered(@Param("statuses") List<AlertStatus> statuses, Pageable pageable);

    Page<SecurityAlert> findByStatusOrderByDetectedAtDesc(AlertStatus status, Pageable pageable);

    Page<SecurityAlert> findBySeverityAndStatusOrderByDetectedAtDesc(Severity severity, AlertStatus status,
                                                                    Pageable pageable);

    List<SecurityAlert> findBySourceIpOrderByDetectedAtDesc(String sourceIp);

    List<SecurityAlert> findByDetectedAtAfterOrderByDetectedAtAsc(Instant since);

    long countByStatus(AlertStatus status);

    long countBySeverity(Severity severity);

    long countByAlertTypeAndDetectedAtAfter(AlertType alertType, Instant since);

    long countByDetectedAtAfter(Instant since);

    /**
     * False-positive rate per rule - the number that drives rule tuning (Phase 12).
     * Returns {@code [ruleName, total, falsePositives]} rows.
     */
    @Query("""
            select a.ruleName as ruleName,
                   count(a) as total,
                   sum(case when a.status = com.yak.minisoc.entity.security.AlertStatus.FALSE_POSITIVE
                            then 1 else 0 end) as falsePositives
            from SecurityAlert a
            where a.detectedAt >= :since
            group by a.ruleName
            order by count(a) desc
            """)
    List<RuleQuality> ruleQualitySince(@Param("since") Instant since);

    /** One row of {@link #ruleQualitySince(Instant)}. */
    interface RuleQuality {
        String getRuleName();

        long getTotal();

        long getFalsePositives();

        /** Share of alerts of this rule that were dismissed, in percent. */
        default double falsePositiveRate() {
            return getTotal() == 0 ? 0.0 : (100.0 * getFalsePositives() / getTotal());
        }
    }
}
