package com.yak.minisoc.entity.security;

/**
 * Alert handling workflow ({@code security_alerts.status}, SDD section 7).
 *
 * <pre>
 *   OPEN ──▶ INVESTIGATING ──▶ RESOLVED
 *     │            │
 *     └────────────┴────────▶ FALSE_POSITIVE
 * </pre>
 *
 * <p>Why {@link #FALSE_POSITIVE} is a state of its own and not "resolved with a comment":
 * the difference between "we handled a real attack" and "the rule was wrong" is the most
 * valuable feedback a SOC produces. Counting false positives per rule is how rules get
 * tuned (Phase 12) - and it is impossible to count if both outcomes share one state.</p>
 */
public enum AlertStatus {

    /** New alert, nobody has looked at it yet. */
    OPEN,

    /** An analyst has taken it and is working on it. */
    INVESTIGATING,

    /** Confirmed and handled (incident created or already known). */
    RESOLVED,

    /** Checked and found not to be an attack - the rule fired on normal activity. */
    FALSE_POSITIVE;

    /** Terminal states need no further analyst action. */
    public boolean isTerminal() {
        return this == RESOLVED || this == FALSE_POSITIVE;
    }

    /** Still on the analyst's desk (open or being investigated). */
    public boolean isActive() {
        return !isTerminal();
    }
}
