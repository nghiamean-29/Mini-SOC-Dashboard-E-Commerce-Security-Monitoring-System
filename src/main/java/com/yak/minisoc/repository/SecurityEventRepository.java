
package com.yak.minisoc.repository;

import com.yak.minisoc.entity.security.SecurityEvent;
import com.yak.minisoc.entity.security.SecurityEventType;
import com.yak.minisoc.entity.security.Severity;

import java.time.Instant;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import org.springframework.data.repository.query.Param;

public interface SecurityEventRepository
    extends JpaRepository<SecurityEvent, Long>,
    JpaSpecificationExecutor<SecurityEvent> {

    // ==========================================
    // PROJECTIONS
    // ==========================================

    interface TypeCount {
        SecurityEventType getEventType();
        long getTotal();
    }

    interface IpCount {
        String getSourceIp();
        long getTotal();
    }

    interface HourlyCount {
        int getHour();
        long getTotal();
    }

    // ==========================================
    // BASIC EVENT QUERIES
    // ==========================================

    Page<SecurityEvent> findAllByOrderByOccurredAtDesc(
        Pageable pageable
    );

    Page<SecurityEvent> findByEventTypeOrderByOccurredAtDesc(
        SecurityEventType eventType,
        Pageable pageable
    );

    Page<SecurityEvent> findBySourceIpOrderByOccurredAtDesc(
        String sourceIp,
        Pageable pageable
    );

    Page<SecurityEvent>
    findByEventTypeAndOccurredAtAfterOrderByOccurredAtDesc(
        SecurityEventType eventType,
        Instant since,
        Pageable pageable
    );

    List<SecurityEvent>
    findByOccurredAtAfterOrderByOccurredAtDesc(
        Instant since
    );

    List<SecurityEvent>
    findBySourceIpAndOccurredAtAfterOrderByOccurredAtDesc(
        String sourceIp,
        Instant since
    );

    List<SecurityEvent> findByRequestIdOrderByOccurredAtAsc(
        String requestId
    );

    // ==========================================
    // JSONB METADATA SEARCH
    // ==========================================

    @Query(
        value = """
            SELECT *
            FROM security_events
            WHERE metadata @> CAST(:json AS jsonb)
            ORDER BY occurred_at DESC
            """,
        nativeQuery = true
    )
    List<SecurityEvent> findByMetadataContaining(
        @Param("json") String json
    );

    // ==========================================
    // EVENT TYPE STATISTICS
    // ==========================================

    @Query("""
        SELECT
            e.eventType AS eventType,
            COUNT(e) AS total
        FROM SecurityEvent e
        WHERE e.occurredAt >= :from
          AND e.occurredAt < :to
        GROUP BY e.eventType
        ORDER BY COUNT(e) DESC
        """)
    List<TypeCount> countByEventTypeBetween(
        @Param("from") Instant from,
        @Param("to") Instant to
    );

    // ==========================================
    // TOP SOURCE IP ADDRESSES
    // ==========================================

    @Query("""
        SELECT
            e.sourceIp AS sourceIp,
            COUNT(e) AS total
        FROM SecurityEvent e
        WHERE e.occurredAt >= :from
          AND e.occurredAt < :to
          AND e.sourceIp IS NOT NULL
        GROUP BY e.sourceIp
        ORDER BY COUNT(e) DESC
        """)
    List<IpCount> findTopSourceIpsBetween(
        @Param("from") Instant from,
        @Param("to") Instant to,
        Pageable pageable
    );

    // ==========================================
    // EVENTS PER HOUR
    // ==========================================

    @Query(
        value = """
            SELECT
                CAST(
                    date_part('hour', occurred_at)
                    AS integer
                ) AS hour,
                COUNT(*) AS total
            FROM security_events
            WHERE occurred_at >= :from
              AND occurred_at < :to
            GROUP BY
                CAST(
                    date_part('hour', occurred_at)
                    AS integer
                )
            ORDER BY hour
            """,
        nativeQuery = true
    )
    List<HourlyCount> countByHourOfDayBetween(
        @Param("from") Instant from,
        @Param("to") Instant to
    );

    // ==========================================
    // DETECTION ENGINE QUERIES
    // ==========================================

    @Query("""
        SELECT COUNT(e)
        FROM SecurityEvent e
        WHERE e.eventType = :type
          AND e.occurredAt >= :since
        """)
    long countByTypeSince(
        @Param("type") SecurityEventType type,
        @Param("since") Instant since
    );

    @Query("""
        SELECT COUNT(e)
        FROM SecurityEvent e
        WHERE e.eventType = :type
          AND e.statusCode = :status
          AND e.occurredAt >= :since
        """)
    long countByTypeAndStatusSince(
        @Param("type") SecurityEventType type,
        @Param("status") int status,
        @Param("since") Instant since
    );

    long countByOccurredAtAfter(
        Instant since
    );

    long countBySeverityHintAndOccurredAtAfter(
        Severity severityHint,
        Instant since
    );

    // ==========================================
    // RETENTION JOB
    // ==========================================

    @Modifying
    @Query("""
        DELETE FROM SecurityEvent e
        WHERE e.occurredAt < :cutoff
        """)
    int deleteOlderThan(
        @Param("cutoff") Instant cutoff
    );
    interface TimelineCount {
        long getEpochSecond();
        long getTotal();
    }

    interface SeverityCount {
        Severity getSeverity();
        long getTotal();
    }

    @Query(value = """
        SELECT CAST(EXTRACT(EPOCH FROM date_trunc(:bucket, occurred_at, 'UTC')) AS bigint) AS "epochSecond",
               COUNT(*) AS total
        FROM security_events
        WHERE occurred_at >= :from AND occurred_at < :to
        GROUP BY 1
        """, nativeQuery = true)
    List<TimelineCount> countTimelineBetween(
        @Param("from") Instant from, @Param("to") Instant to, @Param("bucket") String bucket
    );

    @Query("""
        SELECT e.severityHint AS severity, COUNT(e) AS total
        FROM SecurityEvent e
        WHERE e.occurredAt >= :from AND e.occurredAt < :to
        GROUP BY e.severityHint
        """)
    List<SeverityCount> countBySeverityBetween(@Param("from") Instant from, @Param("to") Instant to);

}
