package com.yak.minisoc.entity.security;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * The vocabulary of the Security Event System (SDD section 5).
 *
 * <p>Every important action in the application becomes one of these values. The
 * collection is deliberately small and closed: a detection rule can only be written
 * against a known vocabulary, and an analyst reading the event table sees consistent
 * values instead of free text.</p>
 *
 * <p><strong>An enum with behaviour.</strong> {@link #isAuthenticationEvent()},
 * {@link #isFreeTextQueryCandidate()} and friends mean that "which events belong to the
 * authentication family?" is answered in one place. Phase 6 rules and Phase 9 filters ask
 * the enum instead of repeating a list of constants - and if a new authentication event is
 * added, the answer changes in exactly one file.</p>
 *
 * <p><strong>Groups are {@link EnumSet}s</strong>, wrapped unmodifiable: fast membership
 * checks, no duplicates possible, and no caller can modify the definition.</p>
 *
 * <p><strong>Database agreement.</strong> The same list exists as a CHECK constraint on
 * {@code security_events.event_type} (migration V5). Adding a value therefore means
 * <em>two</em> coordinated edits - the enum and a new migration (<code>V10__...</code>) -
 * which is intentional: the database refuses values the application does not know, so a
 * forgotten enum value can never silently fill the table with data no rule understands.</p>
 */
public enum SecurityEventType {

    // -------------------------------------------------------------- authentication
    /** Successful authentication. */
    LOGIN_SUCCESS,
    /** Failed authentication attempt (with the reason in login_history). */
    LOGIN_FAILED,
    /** Explicit logout. */
    LOGOUT,
    /** A new account was created. */
    USER_REGISTERED,
    /** An authenticated user changed their own password. */
    PASSWORD_CHANGED,

    // ------------------------------------------------------- authorization / access
    /** Request refused by Spring Security (403) - a probe against forbidden URLs. */
    ACCESS_DENIED,
    /** An administrator used an administrative function. */
    ADMIN_ACCESS,

    // --------------------------------------------------------------------- catalog
    PRODUCT_CREATED,
    PRODUCT_UPDATED,
    PRODUCT_DELETED,

    // ---------------------------------------------------------------------- orders
    ORDER_CREATED,
    ORDER_CANCELLED,
    ORDER_STATUS_CHANGED,

    // ---------------------------------------------------------------- generic request
    /** Ordinary HTTP request (the high-volume event; see the retention note below). */
    API_REQUEST,
    /** A request whose shape looked suspicious (bad parameters, odd method, ...). */
    SUSPICIOUS_REQUEST,

    // -------------------------------------------------------------------- simulator
    /** The Attack Simulator produced this event (Phase 11); flagged so a demo can be
     *  separated from real activity during analysis. */
    SIMULATION_RUN;

    /** Events that describe authentication, not business activity. */
    private static final Set<SecurityEventType> AUTHENTICATION_EVENTS =
            Collections.unmodifiableSet(EnumSet.of(LOGIN_SUCCESS, LOGIN_FAILED, LOGOUT));

    /** Events caused by an administrator acting on the system. */
    private static final Set<SecurityEventType> ADMINISTRATIVE_EVENTS =
            Collections.unmodifiableSet(EnumSet.of(ADMIN_ACCESS, PRODUCT_CREATED,
                    PRODUCT_UPDATED, PRODUCT_DELETED));

    /** Business events of a customer (used for the account-takeover chain in Phase 6). */
    private static final Set<SecurityEventType> BUSINESS_EVENTS =
            Collections.unmodifiableSet(EnumSet.of(ORDER_CREATED, ORDER_CANCELLED,
                    ORDER_STATUS_CHANGED));

    /** Does this event belong to the authentication family? */
    public boolean isAuthenticationEvent() {
        return AUTHENTICATION_EVENTS.contains(this);
    }

    /** Was this event produced by an administrator? */
    public boolean isAdministrativeEvent() {
        return ADMINISTRATIVE_EVENTS.contains(this);
    }

    /** Is this a customer business action (order related)? */
    public boolean isBusinessEvent() {
        return BUSINESS_EVENTS.contains(this);
    }

    /**
     * Is this event type the "background noise"?
     *
     * <p>{@code API_REQUEST} is emitted for every HTTP request, so it dominates the table.
     * The retention job (Phase 9) and the default event monitor filter use this flag to
     * keep the interesting signal readable - and it documents the decision instead of
     * hiding it in a query somewhere.</p>
     */
    public boolean isFreeTextQueryCandidate() {
        return this == API_REQUEST;
    }
}
