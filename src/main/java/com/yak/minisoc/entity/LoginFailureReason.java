package com.yak.minisoc.entity;

/**
 * Why an authentication attempt failed ({@code login_history.failure_reason}).
 *
 * <p>The distinction is not cosmetic, it drives the detection logic:</p>
 * <ul>
 *   <li>{@link #BAD_CREDENTIALS} for an existing account is the classic signal of a
 *       password guessing attack (rule 1, {@code BruteForceRule}).</li>
 *   <li>{@link #UNKNOWN_USER} for a username that does not exist is what a
 *       username-enumeration scan looks like.</li>
 *   <li>{@link #ACCOUNT_LOCKED} / {@link #ACCOUNT_DISABLED} are expected refusals, not
 *       attacks - a detection rule that alerts on them would drown the analyst in
 *       false positives.</li>
 * </ul>
 *
 * <p><strong>Note on the database:</strong> unlike {@code event_type} or
 * {@code alert_type}, this column has no CHECK constraint. The reason is practical: this
 * value is written by exactly one component (the authentication listener in Phase 5) and
 * is never used to filter or join, so a Java enum already gives all the safety that is
 * needed - and adding a new reason later stays cheap. Constraints are worth their
 * migration cost where data is shared and queried, not everywhere.</p>
 *
 * <p><strong>Security note:</strong> the reason is stored for the SOC, but the message
 * shown to the user stays the same for every failure ("Invalid username or password").
 * Telling an attacker whether the account exists is information disclosure.</p>
 */
public enum LoginFailureReason {

    /** Account exists, password was wrong. */
    BAD_CREDENTIALS,

    /** No such account (or, from the user's point of view, indistinguishable). */
    UNKNOWN_USER,

    /** Account exists but is blocked. */
    ACCOUNT_LOCKED,

    /** Account exists but has been closed. */
    ACCOUNT_DISABLED

}
