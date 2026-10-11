package com.yak.minisoc.entity.security;

/**
 * Incident handling workflow ({@code security_incidents.status}, SDD sections 8 and 9).
 *
 * <p>Same four states as {@link AlertStatus}, on purpose: the analyst sees one mental
 * model on the dashboard ("open, investigating, or closed - and closed either as handled
 * or as a false alarm") instead of two different vocabularies.</p>
 *
 * <p>Why a separate enum instead of reusing {@code AlertStatus}: they are different
 * concepts that today happen to share four names. Coupling them would mean that adding a
 * state to alerts (for example {@code ESCALATED}) silently adds it to incidents too. Small
 * duplication of constants is cheaper than accidental coupling.</p>
 */
public enum IncidentStatus {

    /** An incident was opened automatically by correlation; nobody owns it yet. */
    OPEN,

    /** An analyst has assigned themselves and is investigating. */
    INVESTIGATING,

    /** Confirmed and contained; a resolution note exists. */
    RESOLVED,

    /** Investigated and dismissed: the correlated alerts were not a real attack. */
    FALSE_POSITIVE;

    /** Terminal states: the incident is closed and only readable afterwards. */
    public boolean isTerminal() {
        return this == RESOLVED || this == FALSE_POSITIVE;
    }

    /** Counted as "active incident" on the dashboard. */
    public boolean isActive() {
        return !isTerminal();
    }
}
