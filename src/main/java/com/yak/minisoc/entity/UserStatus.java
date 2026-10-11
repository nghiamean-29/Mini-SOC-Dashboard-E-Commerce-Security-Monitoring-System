package com.yak.minisoc.entity;

/**
 * Lifecycle of a user account ({@code users.status}).
 *
 * <p>Deliberately an enum instead of {@code boolean active}, because "not active" and
 * "blocked" are different situations with different consequences:</p>
 * <ul>
 *   <li>{@link #ACTIVE} - can authenticate.</li>
 *   <li>{@link #LOCKED} - blocked after suspicious activity or by an administrator;
 *       the reason belongs in {@code audit_logs}. A locked account must still be
 *       detectable: the failed login is recorded with reason
 *       {@link LoginFailureReason#ACCOUNT_LOCKED}, which is what an analyst needs to see.</li>
 *   <li>{@link #DISABLED} - closed on purpose (former employee, customer request).</li>
 * </ul>
 *
 * <p>The database mirrors these three values with a CHECK constraint (V1), so a typo in a
 * migration or a manual SQL statement cannot introduce a fourth state that the application
 * would not understand. That guarantee is also the reason why the enum values use the same
 * spelling as the SQL values and are persisted with {@code @Enumerated(EnumType.STRING)}:
 * the database stores {@code 'LOCKED'}, not the ordinal number {@code 1} (which would break
 * the day somebody reorders the constants).</p>
 */
public enum UserStatus {

    /** Account is usable. */
    ACTIVE,

    /** Temporarily blocked (e.g. detected compromise); an admin must unlock it. */
    LOCKED,

    /** Permanently closed. */
    DISABLED;

    /** Only an active account may log in. */
    public boolean canAuthenticate() {
        return this == ACTIVE;
    }
}
