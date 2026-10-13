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
 * One authentication attempt ({@code login_history} table, FR-AUTH-09).
 *
 * <p>Append-only and separate from {@code security_events}, because an authentication
 * attempt has a shape the generic event table cannot express well: <em>which</em> username
 * was tried, whether it succeeded, and why it failed. That "why" is exactly the input the
 * brute-force rule needs in Phase 6.</p>
 *
 * <p><strong>Design detail worth noticing:</strong> {@code user} may be {@code null} while
 * {@code username} is always filled. A failed login for an account that does not exist has
 * no user row to point at - yet that attempt is the most interesting kind of data we
 * collect. Storing the typed username keeps the evidence.</p>
 *
 * <p>The two factory methods below encode the invariant that the database also enforces
 * ({@code ck_login_history_reason} in V1): a success has no failure reason, a failure
 * always has one. Constructing an invalid row is therefore impossible from Java, and if
 * somebody inserted one with raw SQL the database would refuse it too.</p>
 */
@Entity
@Table(name = "login_history")
public class LoginHistory extends OccurredEntity {

    private static final long serialVersionUID = 1L;

    /** Null for an attempt on a username that does not exist (or after the user was deleted). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    /** The username exactly as typed by the visitor. */
    @Column(name = "username", nullable = false, length = 50)
    private String username;

    @Column(name = "success", nullable = false)
    private boolean success;

    @Enumerated(EnumType.STRING)
    @Column(name = "failure_reason", length = 50)
    private LoginFailureReason failureReason;

    @Column(name = "source_ip", length = 45)
    private String sourceIp;

    @Column(name = "user_agent", length = 512)
    private String userAgent;

    protected LoginHistory() {
    }

    /** A successful authentication. */
    public static LoginHistory success(User user, String username, String sourceIp,
                                       String userAgent, Instant occurredAt) {
        LoginHistory history = new LoginHistory();
        history.user = user;
        history.username = Objects.requireNonNull(username, "username");
        history.success = true;
        history.failureReason = null;
        history.sourceIp = sourceIp;
        history.userAgent = userAgent;
        history.setOccurredAt(Objects.requireNonNull(occurredAt, "occurredAt"));
        return history;
    }

    /** A failed authentication, with the reason the SOC needs. */
    public static LoginHistory failure(User user, String username, LoginFailureReason reason,
                                       String sourceIp, String userAgent, Instant occurredAt) {
        LoginHistory history = new LoginHistory();
        history.user = user;
        history.username = Objects.requireNonNull(username, "username");
        history.success = false;
        history.failureReason = Objects.requireNonNull(reason, "reason");
        history.sourceIp = sourceIp;
        history.userAgent = userAgent;
        history.setOccurredAt(Objects.requireNonNull(occurredAt, "occurredAt"));
        return history;
    }

    /**
     * Was this a password-guessing attempt?
     *
     * <p>Distinguishing it from a locked or disabled account matters: only a real
     * credential failure should feed the brute-force counter, otherwise a locked account
     * would alert forever.</p>
     */
    public boolean isCredentialFailure() {
        return !success && failureReason == LoginFailureReason.BAD_CREDENTIALS;
    }

    /** The username did not exist: possible enumeration probing. */
    public boolean isUnknownUserAttempt() {
        return !success && failureReason == LoginFailureReason.UNKNOWN_USER;
    }

    // -------------------------------------------------------------- getters/setters

    public User getUser() {
        return user;
    }

    public String getUsername() {
        return username;
    }

    public boolean isSuccess() {
        return success;
    }

    public LoginFailureReason getFailureReason() {
        return failureReason;
    }

    public String getSourceIp() {
        return sourceIp;
    }

    public String getUserAgent() {
        return userAgent;
    }
}
