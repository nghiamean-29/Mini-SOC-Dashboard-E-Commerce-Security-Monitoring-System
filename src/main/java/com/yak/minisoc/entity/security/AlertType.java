package com.yak.minisoc.entity.security;

/**
 * What kind of attack an alert describes - produced by exactly one detection rule each
 * (SDD section 6).
 *
 * <p><strong>Each type carries its default severity.</strong> The specification fixes the
 * severity of every rule (brute force = HIGH, SQL injection = CRITICAL, ...), so the value
 * belongs with the type instead of being repeated in five rule classes. A rule may still
 * raise the severity for a specific event (for example an administrator account under
 * brute force attack), which is why this is called the <em>default</em>.</p>
 *
 * <p>The database mirrors the values with a CHECK constraint ({@code V6}), so an alert
 * with a type that no rule can produce cannot be stored.</p>
 */
public enum AlertType {

    /** Rule 1: many failed logins from one IP inside a short window. */
    BRUTE_FORCE(Severity.HIGH),

    /** Rule 2: a customer repeatedly hitting administrative endpoints. */
    UNAUTHORIZED_ACCESS(Severity.HIGH),

    /** Rule 3: an abnormal number of requests from one IP. */
    REQUEST_FLOOD(Severity.HIGH),

    /** Rule 4: a known injection pattern in request parameters. */
    SQL_INJECTION(Severity.CRITICAL),

    /** Rule 5: the account-takeover chain (failed logins, then success, then changes). */
    SUSPICIOUS_ACCOUNT_ACTIVITY(Severity.HIGH);

    private final Severity defaultSeverity;

    AlertType(Severity defaultSeverity) {
        this.defaultSeverity = defaultSeverity;
    }

    /** Severity defined by the specification for this kind of attack. */
    public Severity defaultSeverity() {
        return defaultSeverity;
    }
}
