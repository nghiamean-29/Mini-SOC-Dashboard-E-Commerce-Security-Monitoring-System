package com.yak.minisoc.entity.security;

/**
 * How serious an alert or an incident is (SDD section 7).
 *
 * <p>The enum carries a numeric {@code rank} so that code can compare severities
 * ({@code severity.isAtLeast(Severity.HIGH)}) instead of writing brittle
 * {@code switch} statements. Escalation logic in Phase 8 ("an incident inherits the
 * highest severity of its alerts") becomes a one-liner built on this method, and the
 * dashboard can sort by severity without a hardcoded order.</p>
 */
public enum Severity {

    LOW(1),
    MEDIUM(2),
    HIGH(3),
    CRITICAL(4);

    private final int rank;

    Severity(int rank) {
        this.rank = rank;
    }

    /** Numeric rank (1 = LOW .. 4 = CRITICAL); useful for sorting and for reports. */
    public int rank() {
        return rank;
    }

    /** True when this severity is equal to or higher than {@code other}. */
    public boolean isAtLeast(Severity other) {
        return other != null && this.rank >= other.rank;
    }
}
