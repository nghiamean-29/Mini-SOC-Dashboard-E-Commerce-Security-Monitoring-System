
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
 * One investigation in the security_incidents table.
 */
@Entity
@Table(name = "security_incidents")
public class SecurityIncident extends VersionedEntity {

    private static final long serialVersionUID = 1L;

    /**
     * Human-readable code generated after insertion.
     */
    @Column(name = "incident_code", nullable = false, length = 20)
    private String incidentCode;

    /**
     * Incident title.
     */
    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(name = "incident_type", nullable = false, length = 50)
    private IncidentType incidentType;

    @Enumerated(EnumType.STRING)
    @Column(name = "severity", nullable = false, length = 20)
    private Severity severity;

    @Column(name = "source_ip", length = 45)
    private String sourceIp;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private IncidentStatus status = IncidentStatus.OPEN;

    /**
     * Analyst responsible for the investigation.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_to")
    private User assignedTo;

    @Column(name = "alert_count", nullable = false)
    private int alertCount = 0;

    @Column(name = "description")
    private String description;

    /**
     * Resolution details.
     */
    @Column(name = "resolution")
    private String resolution;

    @Column(name = "detected_at", nullable = false)
    private Instant detectedAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    /**
     * Alerts linked to this incident.
     */
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "incident_alerts",
        joinColumns = @JoinColumn(name = "incident_id"),
        inverseJoinColumns = @JoinColumn(name = "alert_id"))
    private Set<SecurityAlert> alerts = new LinkedHashSet<>();

    /**
     * Investigation notes.
     */
    @OneToMany(
        mappedBy = "incident",
        cascade = CascadeType.ALL,
        orphanRemoval = true,
        fetch = FetchType.LAZY)
    @OrderBy("createdAt ASC")
    private List<IncidentNote> notes = new ArrayList<>();

    protected SecurityIncident() {
    }

    /**
     * Opens an incident from correlated alerts.
     */
    public static SecurityIncident open(
        IncidentType type,
        Severity severity,
        String sourceIp,
        String title,
        Instant detectedAt) {

        SecurityIncident incident = new SecurityIncident();

        incident.incidentType =
            Objects.requireNonNull(type, "type");
        incident.severity =
            Objects.requireNonNull(severity, "severity");
        incident.sourceIp = sourceIp;
        incident.title =
            Objects.requireNonNull(title, "title");
        incident.detectedAt =
            Objects.requireNonNull(detectedAt, "detectedAt");
        incident.status = IncidentStatus.OPEN;
        incident.alertCount = 0;

        return incident;
    }

    // ------------------------------------------------------------
    // Behaviour
    // ------------------------------------------------------------

    /**
     * Assigns the incident code exactly once.
     */
    public void assignIncidentCode(String code) {
        if (this.incidentCode != null) {
            throw new IllegalStateException(
                "incident " + incidentCode + " already has a code");
        }

        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException(
                "incident code must not be blank");
        }

        this.incidentCode = code;
    }

    /**
     * Links an alert to this incident idempotently.
     *
     * @return true if the link was new, false if it already existed
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

    /**
     * Raises the incident severity without lowering it.
     */
    public void escalateTo(Severity newSeverity) {
        Objects.requireNonNull(newSeverity, "newSeverity");

        if (newSeverity.rank() > this.severity.rank()) {
            this.severity = newSeverity;
        }
    }

    /**
     * Assigns the incident to an analyst.
     */
    public void assignTo(User analyst) {
        if (!status.isActive()) {
            throw new IllegalStateException(
                "cannot assign a " + status + " incident");
        }

        this.assignedTo =
            Objects.requireNonNull(analyst, "analyst");
    }

    public void startInvestigation() {
        if (!status.isActive()) {
            throw new IllegalStateException(
                "incident is already " + status);
        }

        this.status = IncidentStatus.INVESTIGATING;
    }

    /**
     * Adds a note to the investigation notebook.
     */
    public IncidentNote addNote(
        User author,
        String authorUsername,
        String content) {

        if (status.isTerminal()) {
            throw new IllegalStateException(
                "cannot add a note to a " + status
                    + " incident; the investigation is closed");
        }

        // FIX: IncidentNote.of() requires five arguments.
        IncidentNote note = IncidentNote.of(
            this,
            author,
            authorUsername,
            content,
            Instant.now());

        notes.add(note);

        return note;
    }

    /**
     * Resolves the incident.
     */
    public void resolve(String resolution, Instant at) {
        if (status.isTerminal()) {
            throw new IllegalStateException(
                "incident is already " + status);
        }

        if (resolution == null || resolution.isBlank()) {
            throw new IllegalArgumentException(
                "a resolution text is required to close an incident");
        }

        this.status = IncidentStatus.RESOLVED;
        this.resolution = resolution;
        this.resolvedAt = Objects.requireNonNull(at, "at");
    }

    /**
     * Marks the incident as a false positive.
     */
    public void markFalsePositive(String reason, Instant at) {
        if (status.isTerminal()) {
            throw new IllegalStateException(
                "incident is already " + status);
        }

        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException(
                "a reason is required to dismiss an incident");
        }

        this.status = IncidentStatus.FALSE_POSITIVE;
        this.resolution = reason;
        this.resolvedAt = Objects.requireNonNull(at, "at");
    }

    /**
     * Calculates investigation duration.
     */
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

    // ------------------------------------------------------------
    // Getters and setters
    // ------------------------------------------------------------

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

    /**
     * Returns an unmodifiable view of the linked alerts.
     */
    public Set<SecurityAlert> getAlerts() {
        return Collections.unmodifiableSet(alerts);
    }

    /**
     * Returns an unmodifiable view of the investigation notes.
     */
    public List<IncidentNote> getNotes() {
        return Collections.unmodifiableList(notes);
    }
}
