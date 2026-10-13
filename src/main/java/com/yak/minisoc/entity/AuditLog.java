package com.yak.minisoc.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;

/**
 * One administrative or SOC action ({@code audit_logs} table).
 *
 * <p><strong>Who is this for?</strong> Not the detection engine - the security events
 * cover that. The audit log answers the accountability question from the threat model
 * (Phase 0, T3 "Repudiation"): an administrator cannot deny having deleted a product,
 * because the change is recorded with their identity, the moment, and the before/after
 * values.</p>
 *
 * <p><strong>Snapshot fields.</strong> {@link #actorUsername} is stored next to the
 * {@code actor} reference on purpose. If the account is later deleted, {@code actor_id}
 * becomes {@code NULL} (ON DELETE SET NULL) and the reference is gone - but the log line
 * still says who acted. An audit trail that loses its "who" the moment an account is
 * removed is worthless.</p>
 *
 * <p><strong>old_value / new_value are plain text holding JSON</strong>, not JSONB. They
 * are shown to a human investigating a change; nobody queries inside them. Using TEXT
 * avoids a second JSON mapping for no benefit (only {@code security_events.metadata} is
 * JSONB, where containment queries are genuinely useful).</p>
 */
@Entity
@Table(name = "audit_logs")
public class AuditLog extends OccurredEntity {

    private static final long serialVersionUID = 1L;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_id")
    private User actor;

    /** Denormalised copy so the row stays readable after the account disappears. */
    @Column(name = "actor_username", length = 50)
    private String actorUsername;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false, length = 50)
    private AuditAction action;

    /** The kind of affected object, e.g. "Product", "Order", "SecurityIncident". */
    @Column(name = "entity_type", length = 50)
    private String entityType;

    /** The affected object, e.g. "42" or "ORD-20260921-0001" or "INC-0001". */
    @Column(name = "entity_id", length = 64)
    private String entityId;

    @Column(name = "description")
    private String description;

    /** JSON snapshot before the change (null when the row did not exist before). */
    @Column(name = "old_value")
    private String oldValue;

    /** JSON snapshot after the change (null for deletions). */
    @Column(name = "new_value")
    private String newValue;

    @Column(name = "source_ip", length = 45)
    private String sourceIp;

    protected AuditLog() {
    }

    public static AuditLog record(User actor, String actorUsername, AuditAction action,
                                  String entityType, String entityId, String description,
                                  String sourceIp, Instant occurredAt) {
        AuditLog log = new AuditLog();
        log.actor = actor;
        log.actorUsername = actorUsername;
        log.action = Objects.requireNonNull(action, "action");
        log.entityType = entityType;
        log.entityId = entityId;
        log.description = description;
        log.sourceIp = sourceIp;
        log.setOccurredAt(Objects.requireNonNull(occurredAt, "occurredAt"));
        return log;
    }

    /** Adds the before/after snapshots; kept separate because most actions have none. */
    public AuditLog withChange(String oldValueJson, String newValueJson) {
        this.oldValue = oldValueJson;
        this.newValue = newValueJson;
        return this;
    }

    // -------------------------------------------------------------- getters/setters

    public User getActor() {
        return actor;
    }

    public String getActorUsername() {
        return actorUsername;
    }

    public AuditAction getAction() {
        return action;
    }

    public String getEntityType() {
        return entityType;
    }

    public String getEntityId() {
        return entityId;
    }

    public String getDescription() {
        return description;
    }

    public String getOldValue() {
        return oldValue;
    }

    public String getNewValue() {
        return newValue;
    }

    public String getSourceIp() {
        return sourceIp;
    }
}
