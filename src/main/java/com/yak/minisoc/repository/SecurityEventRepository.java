package com.yak.minisoc.repository;

import com.yak.minisoc.entity.security.SecurityEvent;
import com.yak.minisoc.entity.security.SecurityEventType;
import com.yak.minisoc.entity.security.Severity;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Data access for security events - the busiest repository of the project.
 *
 * <h3>How the query methods were chosen</h3>
 * <p>Every method corresponds to a question a SOC analyst actually asks, and every one of
 * them is written to match an index of migration V5/V8 ("filter + newest first"). A method
 * that could not use an index was not added: a SOC dashboard that needs a sequential scan
 * over a million events is a dashboard nobody opens twice.</p>
 *
 * <h3>Two Spring Data details worth pointing out</h3>
 * <ul>
 *   <li><strong>{@code deleteOlderThan} is a bulk delete</strong> ({@code @Modifying}) and
 *       returns the number of deleted rows. It runs one SQL {@code DELETE} instead of
 *       loading entities and removing them one by one - the retention job (Phase 9) deletes
 *       tens of thousands of rows, so the difference is minutes versus milliseconds. The
 *       links in {@code alert_events} disappear automatically thanks to
 *       {@code ON DELETE CASCADE} in the database.</li>
 *   <li><strong>Interface projections</strong> ({@link TypeCount}, {@link IpCount},
 *       {@link HourlyCount}) return aggregated data without loading a single entity. The
 *       method names of the interface are matched against the aliases in the query - a
 *       contract the compiler cannot check, which is why every alias is written to match
 *       exactly and why the aggregated queries are covered by tests in this phase.</li>
 * </ul>
 *
 * <p><strong>Transactions:</strong> no annotation here on purpose. Reads run fine without
 * an explicit transaction, and the modifying query must be executed inside the caller's
 * transaction (the retention service) so that "count what I deleted" and the deletion stay
 * atomic.</p>
 */
public interface SecurityEventRepository extends JpaRepository<SecurityEvent, Long> {

    // ------------------------------------------------------------------ projections

    /** "Which event types dominate the traffic?" */
    interface TypeCount {
        SecurityEventType getEventType();

        long getTotal();
    }

    /** "Which IP addresses are the noisiest?" */
    interface IpCount {
        String getSourceIp();

        long getTotal();
    }

    /** "When do attacks happen?" (hour of day, UTC) */
    interface HourlyCount {
        int getHour();

        long getTotal();
    }

    // ---------------------------------------------------------------- plain queries

    /** The event monitor, newest first. */
    Page<SecurityEvent> findAllByOrderByOccurredAtDesc(Pageable pageable);

    Page<SecurityEvent> findByEventTypeOrderByOccurredAtDesc(SecurityEventType eventType, Pageable pageable);

    /** "What did this IP do?" - the first question of every investigation. */
    Page<SecurityEvent> findBySourceIpOrderByOccurredAtDesc(String sourceIp, Pageable pageable);

    Page<SecurityEvent> findByEventTypeAndOccurredAtAfterOrderByOccurredAtDesc(
            SecurityEventType eventType, Instant since, Pageable pageable);

    List<SecurityEvent> findByOccurredAtAfterOrderByOccurredAtDesc(Instant since);

    /**
     * Raw material of the flooding rules: everything one IP did in a window, newest first.
     * The order matches {@code idx_security_events_ip_time} exactly.
     */
    List<SecurityEvent> findBySourceIpAndOccurredAtAfterOrderByOccurredAtDesc(String sourceIp, Instant since);

    /**
     * The event type of the page currently open, correlated by request id - the "why did
     * the application log say that?" question that ties the SOC back to the app logs.
     */
    List<SecurityEvent> findByRequestIdOrderByOccurredAtAsc(String requestId);

    /**
     * Finds events whose JSONB {@code metadata} contains the given document.
     *
     * <p>This is the method the GIN index of migration V8 exists for: PostgreSQL answers
     * {@code metadata @> ...} from the index instead of parsing every JSON document in the
     * table. Use case: "show me every event produced by simulator run 42" or "every event
     * that carries rule parameters of type X" - questions about supplementary data that
     * deliberately has no column of its own.</p>
     *
     * <p>{@code cast(:json as jsonb)} is required because JDBC sends the parameter as text;
     * the explicit cast keeps the query independent of how the driver guesses its type.</p>
     *
     * @param json a JSON document, e.g. {@code {"rule":"BRUTE_FORCE"}}
     */
    @Query(value = "select * from security_events where metadata @> cast(:json as jsonb)"
            + " order by occurred_at desc", nativeQuery = true)
    List<SecurityEvent> findByMetadataContaining(@Param("json") String json);

    // ------------------------------------------------------------------- aggregates

    @Query("""
            select e.eventType as eventType, count(e) as total
            from SecurityEvent e
            where e.occurredAt >= :from and e.occurredAt < :to
            group by e.eventType
            order by count(e) desc
            """)
    List<TypeCount> countByEventTypeBetween(@Param("from") Instant from, @Param("to") Instant to);

    @Query("""
            select e.sourceIp as sourceIp, count(e) as total
            from SecurityEvent e
            where e.occurredAt >= :from and e.occurredAt < :to and e.sourceIp is not null
            group by e.sourceIp
            order by count(e) desc
            """)
    List<IpCount> findTopSourceIpsBetween(@Param("from") Instant from, @Param("to") Instant to,
                                         Pageable pageable);

    /**
     * Events per hour. Written as a native query because extracting the hour from a
     * timestamp is PostgreSQL specific ({@code date_part}). The projection interface keeps
     * the result readable in Java all the same.
     */
    @Query(value = """
            select cast(date_part('hour', occurred_at) as integer) as hour, count(*) as total
            from security_events
            where occurred_at >= :from and occurred_at < :to
            group by cast(date_part('hour', occurred_at) as integer)
            order by hour
            """, nativeQuery = true)
    List<HourlyCount> countByHourOfDayBetween(@Param("from") Instant from, @Param("to") Instant to);

    @Query("select count(e) from SecurityEvent e where e.eventType = :type and e.occurredAt >= :since")
    long countByTypeSince(@Param("type") SecurityEventType type, @Param("since") Instant since);

    @Query("select count(e) from SecurityEvent e where e.eventType = :type and e.statusCode = :status"
            + " and e.occurredAt >= :since")
    long countByTypeAndStatusSince(@Param("type") SecurityEventType type,
                                   @Param("status") int status, @Param("since") Instant since);

    long countByOccurredAtAfter(Instant since);

    long countBySeverityHintAndOccurredAtAfter(Severity severityHint, Instant since);

    // -------------------------------------------------------------------- retention

    /**
     * Deletes events older than the retention window (Phase 9, NFR-DATA-02).
     *
     * @return number of deleted rows, for the job's log line
     */
    @Modifying
    @Query("delete from SecurityEvent e where e.occurredAt < :cutoff")
    int deleteOlderThan(@Param("cutoff") Instant cutoff);
}
