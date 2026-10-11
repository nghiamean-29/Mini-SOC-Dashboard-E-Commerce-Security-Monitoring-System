package com.yak.minisoc.entity.security;

import com.yak.minisoc.entity.User;
import com.yak.minisoc.entity.VersionedEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * One investigation ({@code security_incidents} table, FR-INC-01).
 *
 * <p>An alert is a suspicion; an incident is the work an analyst does about it.
 * Correlation (Phase 8) merges related alerts - same source, close in time - into a single
 * incident, because a human cannot work twelve alerts whose only difference is the minute
 * they were raised in.</p>
 *
 * <p><strong>Extends {@link VersionedEntity}</strong> because an incident has concurrent
 * writers: an analyst assigns themselves, another adds a note, the correlation job raises
 * {@code alertCount}. Optimistic locking makes the rare conflict visible (a 409 instead of
 * a silently lost assignment).</p>
 *
 * <h3>Why the counters are denormalised</h3>
 * <p>{@code alertCount} is stored on the row although it can be derived from
 * {@code incident_alerts}. The dashboard lists incidents with their alert count on every
 * row (NFR-PERF-01: page load under one second); deriving it would mean a
 * {@code GROUP BY} join per row. The cost is keeping the counter in sync - and the
 * correlation service has exactly one place that links an alert, so the counter is updated
 * there: {@link #linkAlert(SecurityAlert)}.</p>
 *
 * <p>That method is also the idempotency point chosen at Phase 0 (DB-8): the composite
 * primary key of {@code incident_alerts} plus the "already linked?" check mean the
 * correlation job can run twice over the same alerts without creating a duplicate link or
 * inflating the counter.</p>
 */
@Entity
@Table(name = "security_incidents")
public class SecurityIncident extends VersionedEntity {

    private static final long serialVersionUID = 1L;

    /** Human readable code (INC-0001) generated from the id after insert. */
    @Column(name = "incident_code", nullable = false, length = 20)
    private String incidentCode;

    /** e.g. "Possible brute force attack from 203.0.113.45 (7 alerts)". */
    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(name = "incident_type", nullable = false, length = 50)
    private IncidentType incidentType;

    /** Inherited from the highest severity among the linked alerts (Phase 8). */
    @Enumerated(EnumType.STRING)
    @Column(name = "severity", nullable = false, length = 20)
    private Severity severity;

    @Column(name = "source_ip", length = 45)
    private String sourceIp;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private IncidentStatus status = IncidentStatus.OPEN;

    /** The analyst who owns the investigation. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_to")
    private User assignedTo;

    @Column(name = "alert_count", nullable = false)
    private int alertCount = 0;

    @Column(name = "description")
    private String description;

    /** What was done about it; mandatory before closing (see {@link #resolve(String, Instant)}). */
    @Column(name = "resolution")
    private String resolution;

    @Column(name = "detected_at", nullable = false)
    private Instant detectedAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    /**
     * The alerts that formed this incident. {@code LinkedHashSet} keeps insertion order
     * (oldest first) for the timeline view.
     */
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "incident_alerts",
            joinColumns = @JoinColumn(name = "incident_id"),
            inverseJoinColumns = @JoinColumn(name = "alert_id"))
    private Set<SecurityAlert> alerts = new LinkedHashSet<>();

    /**
     * The investigation notebook (FR-INC-05). A comment is part of the incident, so it is
     * saved and deleted with it ({@code cascade = ALL, orphanRemoval = true}) - unlike a
     * note, which is never edited, only added.
     */
    @OneToMany(mappedBy = "incident", cascade = CascadeType.ALL, orphanRemoval = true,
            fetch = FetchType.LAZY)
    @OrderBy("createdAt ASC")
    private List<IncidentNote> notes = new ArrayList<>();

    protected SecurityIncident() {
    }

    /** Opens an incident from correlated alerts. */
    public static SecurityIncident open(IncidentType type, Severity severity, String sourceIp,
                                        String title, Instant detectedAt) {
        SecurityIncident incident = new SecurityIncident();
        incident.incidentType = Objects.requireNonNull(type, "type");
        incident.severity = Objects.requireNonNull(severity, "severity");
        incident.sourceIp = sourceIp;
        incident.title = Objects.requireNonNull(title, "title");
        incident.detectedAt = Objects.requireNonNull(detectedAt, "detectedAt");
        incident.status = IncidentStatus.OPEN;
        incident.alertCount = 0;
        return incident;
    }

    // ------------------------------------------------------------------ behaviour

    /** Assigns the business code exactly once (see {@code Order.assignOrderCode}). */
    public void assignIncidentCode(String code) {
        if (this.incidentCode != null) {
            throw new IllegalStateException("incident " + incidentCode + " already has a code");
        }
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("incident code must not be blank");
        }
        this.incidentCode = code;
    }

    /**
     * Links an alert to this incident - idempotently.
     *
     * @return {@code true} when the link was new, {@code false} when it already existed
     *         (which is how the caller knows not to increment anything else)
     */
    public boolean linkAlert(SecurityAlert alert) {
        Objects.requireNonNull(alert, "alert");
        if (!alerts.add(alert)) {
            return false;
        }
        alertCount = alerts.size();
        escalateTo(alert.getSeverity());
        return true;
    }

    /** Raises the incident severity to at least {@code newSeverity} (never lowers it). */
    public void escalateTo(Severity newSeverity) {
        Objects.requireNonNull(newSeverity, "newSeverity");
        if (newSeverity.rank() > this.severity.rank()) {
            this.severity = newSeverity;
        }
    }

    /** An analyst takes ownership. */
    public void assignTo(User analyst) {
        if (!status.isActive()) {
            throw new IllegalStateException("cannot assign a " + status + " incident");
        }
        this.assignedTo = Objects.requireNonNull(analyst, "analyst");
    }

    public void startInvestigation() {
        if (!status.isActive()) {
            throw new IllegalStateException("incident is already " + status);
        }
        this.status = IncidentStatus.INVESTIGATING;
    }

    /** Adds a note to the investigation notebook. */
    public IncidentNote addNote(User author, String authorUsername, String content) {
        if (status.isTerminal()) {
            throw new IllegalStateException(
                    "cannot add a note to a " + status + " incident; the investigation is closed");
        }
        IncidentNote note = IncidentNote.of(this, author, authorUsername, content);
        notes.add(note);
        return note;
    }

    /**
     * Closes the incident as handled.
     *
     * <p>A resolution text is required. The database also demands a non-null
     * {@code resolved_at} for a closed incident ({@code ck_security_incidents_resolved}),
     * and this method makes the equivalent rule true in Java: closing without saying what
     * was done produces an incident that tells the next reader nothing.</p>
     */
    public void resolve(String resolution, Instant at) {
        if (status.isTerminal()) {
            throw new IllegalStateException("incident is already " + status);
        }
        if (resolution == null || resolution.isBlank()) {
            throw new IllegalArgumentException("a resolution text is required to close an incident");
        }
        this.status = IncidentStatus.RESOLVED;
        this.resolution = resolution;
        this.resolvedAt = Objects.requireNonNull(at, "at");
    }

    /** Closes the incident as a false alarm; a reason is likewise required. */
    public void markFalsePositive(String reason, Instant at) {
        if (status.isTerminal()) {
            throw new IllegalStateException("incident is already " + status);
        }
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("a reason is required to dismiss an incident");
        }
        this.status = IncidentStatus.FALSE_POSITIVE;
        this.resolution = reason;
        this.resolvedAt = Objects.requireNonNull(at, "at");
    }

    /** How long the investigation took (or has been running, when still open). */
    public Duration timeToResolution(Instant now) {
        Instant end = resolvedAt != null ? resolvedAt : now;
        return Duration.between(detectedAt, end);
    }

    public boolean isActive() {
        return status.isActive();
    }

    public boolean isResolved() {
        return status == IncidentStatus.RESOLVED;
    }

    // -------------------------------------------------------------- getters/setters

    public String getIncidentCode() {
        return incidentCode;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = Objects.requireNonNull(title, "title");
    }

    public IncidentType getIncidentType() {
        return incidentType;
    }

    public Severity getSeverity() {
        return severity;
    }

    public String getSourceIp() {
        return sourceIp;
    }

    public IncidentStatus getStatus() {
        return status;
    }

    public User getAssignedTo() {
        return assignedTo;
    }

    public int getAlertCount() {
        return alertCount;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getResolution() {
        return resolution;
    }

    public Instant getDetectedAt() {
        return detectedAt;
    }

    public Instant getResolvedAt() {
        return resolvedAt;
    }

    /** Read-only: alerts are linked with {@link #linkAlert(SecurityAlert)}. */
    public Set<SecurityAlert> getAlerts() {
        return Collections.unmodifiableSet(alerts);
    }

    /** Read-only: notes are added with {@link #addNote(User, String, String, Instant)}. */
    public List<IncidentNote> getNotes() {
        return Collections.unmodifiableList(notes);
    }
}
