package com.yak.minisoc.entity;

/**
 * What was audited ({@code audit_logs.action}).
 *
 * <p>Auditing answers a question that security events do not: <em>who changed what?</em>
 * A security event says "an order was cancelled"; the audit log says "administrator 4
 * cancelled order ORD-20260921-0007, reason: customer request". Phase 5 writes these rows
 * from the services, Phase 9 shows them, and they are append-only.</p>
 *
 * <p><strong>Why is this an enum when {@code login_history.failure_reason} is one too, but
 * {@code security_events.metadata} is free-form JSON?</strong> Because an action is a
 * closed vocabulary that the application itself defines: any action we can perform can be
 * written down here, and a human reading the log gets a consistent list. Its values are
 * <em>not</em> mirrored by a CHECK constraint, so adding an action later is a
 * Java-only change (see {@link LoginFailureReason} for the same reasoning).</p>
 */
public enum AuditAction {

    // ------------------------------------------------------------------- catalog
    CATEGORY_CREATED,
    CATEGORY_UPDATED,
    CATEGORY_DELETED,
    PRODUCT_CREATED,
    PRODUCT_UPDATED,
    PRODUCT_DELETED,

    // -------------------------------------------------------------------- orders
    /** An administrator moved an order to another state. */
    ORDER_STATUS_CHANGED,
    /** An administrator cancelled a customer order. */
    ORDER_CANCELLED_BY_ADMIN,

    // --------------------------------------------------------------------- users
    USER_CREATED,
    USER_ROLE_CHANGED,
    USER_STATUS_CHANGED,
    USER_PASSWORD_RESET,

    // ----------------------------------------------------------------------- SOC
    ALERT_STATUS_CHANGED,
    INCIDENT_CREATED,
    INCIDENT_ASSIGNED,
    INCIDENT_STATUS_CHANGED,
    INCIDENT_RESOLVED,
    INCIDENT_NOTE_ADDED,

    // ----------------------------------------------------------------- simulator
    /** Somebody ran the Attack Simulator (Phase 11). Always audited: the tool that
     *  creates artificial attacks must never be usable unnoticed. */
    SIMULATION_EXECUTED

}
