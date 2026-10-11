package com.yak.minisoc.entity.security;

import com.yak.minisoc.entity.User;
import com.yak.minisoc.entity.VersionedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/**
 * A judgement produced by a detection rule ({@code security_alerts} table, FR-ALERT-01).
 *
 * <p>Extends {@link VersionedEntity}: an analyst changes the status (Phase 9) while the
 * cooldown logic may raise {@code related_event_count} at the same moment - two writers,
 * one row, hence optimistic locking.</p>
 *
 * <h3>The three fields that make the SOC work</h3>
 * <ul>
 *   <li><strong>{@code relatedEventCount}</strong> - how many raw events produced this
 *       alert. It is denormalised (the alternative would be a {@code COUNT(*)} per row on
 *       the dashboard) and it is what makes the alert's story readable: "BRUTE_FORCE, 7
 *       events" says far more than "BRUTE_FORCE".</li>
 *   <li><strong>{@code dedupKey}</strong> - {@code alertType + sourceIp + time bucket}.
 *       A database UNIQUE constraint backs it. Reason: the cooldown rule ("do not create a
 *       second alert of the same type from the same IP within N minutes") is a
 *       read-then-write operation, and two request threads running in parallel can both
 *       read "no alert yet". The UNIQUE constraint turns that race into a rejected insert
 *       the detector can interpret as "somebody else already alerted" - correct behaviour
 *       without a lock.</li>
 *   <li><strong>{@code ruleName}</strong> - which rule fired. Without it, rule tuning
 *       (Phase 12) cannot answer "which rule produces our false positives?".</li>
 * </ul>
 *
 * <p><strong>The evidence link.</strong> {@code alert_events} is mapped as a
 * {@code @ManyToMany}; the pair is a primary key, so the same event cannot be attached to
 * the same alert twice. Events are read-only here (nothing cascades to
 * {@link SecurityEvent}), because an alert must never be able to modify the facts it was
 * derived from.</p>
 */
@Entity
@Table(name = "security_alerts")
public class SecurityAlert extends VersionedEntity {

    private static final long serialVersionUID = 1L;

    @Enumerated(EnumType.STRING)
    @Column(name = "alert_type", nullable = false, length = 50)
    private AlertType alertType;

    @Enumerated(EnumType.STRING)
    @Column(name = "severity", nullable = false, length = 20)
    private Severity severity;

    @Column(name = "source_ip", length = 45)
    private String sourceIp;

    /** The account the alert is about, when it is about an account. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "username", length = 50)
    private String username;

    @Column(name = "description")
    private String description;

    /** When the rule decided to raise this alert (should equal the newest linked event). */
    @Column(name = "detected_at", nullable = false)
    private Instant detectedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private AlertStatus status = AlertStatus.OPEN;

    @Column(name = "related_event_count", nullable = false)
    private int relatedEventCount = 1;

    /** {@code alertType|sourceIp|bucket} - unique in the database (see class javadoc). */
    @Column(name = "dedup_key", nullable = false, length = 150)
    private String dedupKey;

    @Column(name = "rule_name", length = 50)
    private String ruleName;

    /**
     * LAZY and read-only in practice: the alert detail page asks for the events, the list
     * page never touches them. {@code LinkedHashSet} keeps them in insertion order so the
     * "evidence" panel reads chronologically instead of in hash order.
     */
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "alert_events",
            joinColumns = @JoinColumn(name = "alert_id"),
            inverseJoinColumns = @JoinColumn(name = "event_id"))
    private Set<SecurityEvent> events = new LinkedHashSet<>();

    protected SecurityAlert() {
    }

    /**
     * Creates an alert for a rule.
     *
     * <p>The severity defaults to the one the specification assigns to the alert type
     * ({@link AlertType#defaultSeverity()}) and can be raised afterwards with
     * {@link #escalateTo(Severity)} - a rule that finds an administrator account under
     * attack deserves more attention than the default.</p>
     */
    public static SecurityAlert raise(AlertType alertType, String sourceIp, Instant detectedAt,
                                      String dedupKey) {
        SecurityAlert alert = new SecurityAlert();
        alert.alertType = Objects.requireNonNull(alertType, "alertType");
        alert.severity = alertType.defaultSeverity();
        alert.sourceIp = sourceIp;
        alert.detectedAt = Objects.requireNonNull(detectedAt, "detectedAt");
        alert.dedupKey = Objects.requireNonNull(dedupKey, "dedupKey");
        alert.status = AlertStatus.OPEN;
        alert.relatedEventCount = 1;
        return alert;
    }

    // ------------------------------------------------------------------ behaviour

    /** Attaches the evidence that produced this alert (the join row is idempotent). */
    public void addEvidence(SecurityEvent event) {
        events.add(Objects.requireNonNull(event, "event"));
        if (events.size() > relatedEventCount) {
            relatedEventCount = events.size();
        }
    }

    /**
     * Records that the same rule fired again inside the dedup window.
     *
     * <p>The alert is not duplicated; instead it becomes "bigger" - this is what makes a
     * brute-force alert show {@code 7} instead of three separate alerts of 2, 3 and 2
     * events. The severity is re-asserted at the type's default, never lowered: a rule that
     * keeps firing certainly does not deserve less attention than it got the first time.</p>
     */
    public void recordAdditionalMatch(int matchedEvents) {
        if (matchedEvents <= 0) {
            throw new IllegalArgumentException("matchedEvents must be positive, was " + matchedEvents);
        }
        this.relatedEventCount += matchedEvents;
        escalateTo(alertType.defaultSeverity());
    }

    /** Raises severity to {@code newSeverity}, never lowers it. */
    public void escalateTo(Severity newSeverity) {
        Objects.requireNonNull(newSeverity, "newSeverity");
        if (newSeverity.rank() > this.severity.rank()) {
            this.severity = newSeverity;
        }
    }

    // ---------------------------------------------------------- analyst workflow

    /** An analyst takes the alert. */
    public void startInvestigation() {
        requireNotTerminal("start investigation on");
        this.status = AlertStatus.INVESTIGATING;
    }

    /** Confirmed and handled (usually linked to an incident). */
    public void resolve() {
        requireNotTerminal("resolve");
        this.status = AlertStatus.RESOLVED;
    }

    /** Investigated and dismissed: the rule fired on normal activity. */
    public void markFalsePositive() {
        requireNotTerminal("mark false positive");
        this.status = AlertStatus.FALSE_POSITIVE;
    }

    private void requireNotTerminal(String action) {
        if (status.isTerminal()) {
            throw new IllegalStateException(
                    "cannot " + action + " alert " + getId() + ": status is already " + status);
        }
    }

    public boolean isActive() {
        return status.isActive();
    }

    public boolean isCritical() {
        return severity == Severity.CRITICAL;
    }

    // -------------------------------------------------------------- getters/setters

    public AlertType getAlertType() {
        return alertType;
    }

    public Severity getSeverity() {
        return severity;
    }

    public String getSourceIp() {
        return sourceIp;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Instant getDetectedAt() {
        return detectedAt;
    }

    public AlertStatus getStatus() {
        return status;
    }

    public int getRelatedEventCount() {
        return relatedEventCount;
    }

    public String getDedupKey() {
        return dedupKey;
    }

    public String getRuleName() {
        return ruleName;
    }

    public void setRuleName(String ruleName) {
        this.ruleName = ruleName;
    }

    /** Read-only: evidence is attached with {@link #addEvidence(SecurityEvent)}. */
    public Set<SecurityEvent> getEvents() {
        return Collections.unmodifiableSet(events);
    }
}
