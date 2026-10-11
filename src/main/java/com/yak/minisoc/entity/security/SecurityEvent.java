package com.yak.minisoc.entity.security;

import com.yak.minisoc.entity.BaseEntity;
import com.yak.minisoc.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * One raw security fact ({@code security_events} table) - the raw material of the whole
 * SOC and the largest table of the project.
 *
 * <p><strong>Event versus alert.</strong> An event is something that <em>happened</em>
 * ("a login failed"). An alert is a <em>judgement</em> about events ("these failures are a
 * brute-force attack"). Only events are written here; the judgement lives in
 * {@link SecurityAlert}. Keeping the two apart is what makes the SOC explainable: every
 * alert can be traced back to the exact rows that produced it.</p>
 *
 * <p><strong>Append-only.</strong> There is no update path in this class - no setters for
 * the payload fields, no status to change. A fact cannot become untrue because a program
 * changed its mind. The only thing that ever happens in the database is
 * {@code DELETE} by the retention job (Phase 9).</p>
 *
 * <p><strong>Why {@code occurred_at} and {@code created_at} coexist:</strong>
 * {@code occurred_at} is when the action happened; {@code created_at} is when this row was
 * written. In normal operation they differ by milliseconds. They can differ by a lot when
 * events are collected asynchronously, or when the Attack Simulator (Phase 11) generates a
 * burst of events with backdated timestamps to reproduce an attack pattern. A detection
 * rule must use {@code occurred_at}; "when did we write it down" is a debugging question.</p>
 *
 * <p><strong>{@code metadata} is {@code Map<String, Object>} stored as JSONB.</strong>
 * {@code @JdbcTypeCode(SqlTypes.JSON)} lets Hibernate serialise the map with Jackson (the
 * JSON mapper is auto-detected in Hibernate 7 - no extra dependency is needed) and gives a
 * GIN index over the JSON document for free (migration V8). One flexible column instead of
 * ten nullable ones, without losing the ability to query inside:</p>
 * <pre>
 *   SELECT * FROM security_events WHERE metadata @&gt; '{"rule": "BRUTE_FORCE"}';
 * </pre>
 * <p>The trade-off is deliberate and documented: {@code metadata} is for
 * <em>supplementary</em> data (order code, rule parameters, simulator run id), never for
 * values that detection must filter on - those get a real column, which is why
 * {@code source_ip}, {@code event_type} and {@code occurred_at} are not inside it.</p>
 */
@Entity
@Table(name = "security_events")
public class SecurityEvent extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** When the action happened (see the class javadoc). Always set by the collector. */
    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 60)
    private SecurityEventType eventType;

    /**
     * Optional hint from the collector. The real severity decision belongs to the
     * detection rules; this column only lets a rule store "how bad it looked at
     * collection time" while preserving the option to disagree later.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "severity_hint", length = 20)
    private Severity severityHint;

    /** IPv6 fits in 45 characters (the DB column is sized with that in mind). */
    @Column(name = "source_ip", length = 45)
    private String sourceIp;

    /** NULL is normal: a failed login for a non-existing account has no user row. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    /** The username as typed/supplied; kept even when {@link #user} is null (forensics). */
    @Column(name = "username", length = 50)
    private String username;

    @Column(name = "endpoint", length = 255)
    private String endpoint;

    @Column(name = "http_method", length = 10)
    private String httpMethod;

    @Column(name = "status_code")
    private Integer statusCode;

    @Column(name = "user_agent", length = 512)
    private String userAgent;

    @Column(name = "description")
    private String description;

    /**
     * Supplementary data as JSONB. {@code columnDefinition} is not needed (Hibernate derives
     * it), but it is kept so the DDL intent is visible next to the mapping; the migration
     * remains the authority.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb")
    private Map<String, Object> metadata;

    /** Correlates this event with the application log lines of the same HTTP request. */
    @Column(name = "request_id", length = 36)
    private String requestId;

    /** Required by JPA. */
    protected SecurityEvent() {
    }

    /**
     * Records a fact.
     *
     * <p>Static factory instead of a public constructor: every event must carry a type and
     * a moment, and this signature makes that requirement impossible to forget.</p>
     */
    public static SecurityEvent of(SecurityEventType eventType, Instant occurredAt,
                                   String sourceIp, String endpoint) {
        SecurityEvent event = new SecurityEvent();
        event.eventType = Objects.requireNonNull(eventType, "eventType");
        event.occurredAt = Objects.requireNonNull(occurredAt, "occurredAt");
        event.sourceIp = sourceIp;
        event.endpoint = endpoint;
        return event;
    }

    // ------------------------------------------------- fluent description of the fact

    /** Who triggered the event (null for anonymous actions such as a failed login). */
    public SecurityEvent by(User user, String username) {
        this.user = user;
        this.username = username;
        return this;
    }

    /** The HTTP context of the request that produced the event. */
    public SecurityEvent withRequest(String httpMethod, Integer statusCode, String userAgent) {
        this.httpMethod = httpMethod;
        this.statusCode = statusCode;
        this.userAgent = userAgent;
        return this;
    }

    /** Human readable sentence for the event monitor ("Failed login for user 'alice'"). */
    public SecurityEvent withDescription(String description) {
        this.description = description;
        return this;
    }

    public SecurityEvent withSeverityHint(Severity severityHint) {
        this.severityHint = severityHint;
        return this;
    }

    public SecurityEvent withMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
        return this;
    }

    public SecurityEvent withRequestId(String requestId) {
        this.requestId = requestId;
        return this;
    }

    // ------------------------------------------------------------------ queries

    /** Was this request refused by the security layer? */
    public boolean isAccessDenied() {
        return eventType == SecurityEventType.ACCESS_DENIED;
    }

    /** Did this event come from the Attack Simulator (useful to separate demo from reality)? */
    public boolean isSimulated() {
        return eventType == SecurityEventType.SIMULATION_RUN;
    }

    /** True when the request reached the application and failed (4xx/5xx). */
    public boolean isFailedRequest() {
        return statusCode != null && statusCode >= 400;
    }

    // -------------------------------------------------------------- getters/setters

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public SecurityEventType getEventType() {
        return eventType;
    }

    public Severity getSeverityHint() {
        return severityHint;
    }

    public String getSourceIp() {
        return sourceIp;
    }

    public User getUser() {
        return user;
    }

    public String getUsername() {
        return username;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public String getHttpMethod() {
        return httpMethod;
    }

    public Integer getStatusCode() {
        return statusCode;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public String getDescription() {
        return description;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
    }

    public String getRequestId() {
        return requestId;
    }
}
