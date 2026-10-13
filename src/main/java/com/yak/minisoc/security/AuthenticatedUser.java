package com.yak.minisoc.security;

import com.yak.minisoc.entity.User;
import com.yak.minisoc.entity.UserStatus;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * The authenticated principal: what Spring Security knows about the logged-in user.
 *
 * <h3>Why not just use Spring Security's {@code org.springframework.security.core.userdetails.User}?</h3>
 * <p>Because that class only carries a username and the authorities. Our application
 * constantly needs a third piece of information: <strong>the database id</strong>.
 * "Show my orders" (Phase 4), "who cancelled this order" in the audit log (Phase 5), or
 * "assign this incident to me" (Phase 8) all need {@code user_id}. With the built-in
 * {@code User} there are only two options, and both are bad: query the database again by
 * username on every call, or parse the username out of the principal. Carrying the id in
 * the principal keeps exactly one source of truth - the one read at login time.</p>
 *
 * <h3>Why the status is carried instead of being checked inside the service</h3>
 * <p>{@link UserStatus} maps onto the four {@code is...()} methods of {@link UserDetails},
 * which Spring Security calls at authentication time. Consequence: a disabled or locked
 * account cannot log in <em>at all</em> - the check happens before any controller runs, and
 * the failure is reported with the same neutral message as a wrong password, so the login
 * form does not become a way to discover which accounts exist.</p>
 *
 * <p><strong>Never print the password hash.</strong> {@link #toString()} is overridden on
 * purpose: objects end up in log files, debugger views and error reports, and a leaked
 * BCrypt hash is still a leaked credential.</p>
 */
public final class AuthenticatedUser implements UserDetails {

    private static final long serialVersionUID = 1L;

    private final Long userId;
    private final String username;
    /** BCrypt hash - required by the authentication provider, never logged (see toString). */
    private final String passwordHash;
    private final UserStatus status;
    private final Set<GrantedAuthority> authorities;

    private AuthenticatedUser(Long userId, String username, String passwordHash, UserStatus status,
                              Set<GrantedAuthority> authorities) {
        this.userId = userId;
        this.username = username;
        this.passwordHash = passwordHash;
        this.status = status;
        this.authorities = authorities;
    }

    /**
     * Builds the principal from a {@link User} entity loaded with its roles.
     *
     * <p>{@code user.getRoleCodes()} is used rather than {@code getRoles()} because the
     * authorities only need the code: turning an entity into an authority here would leak
     * the persistence model into the security layer.</p>
     */
    public static AuthenticatedUser from(User user) {
        Objects.requireNonNull(user, "user");
        Set<GrantedAuthority> authorities = user.getRoleCodes().stream()
                .map(code -> (GrantedAuthority) new SimpleGrantedAuthority(AppRoles.authority(code)))
                // LinkedHashSet: the role order stays stable, so the "roles" shown on the
                // pages and in log lines does not jump around between requests.
                .collect(Collectors.toCollection(LinkedHashSet::new));
        return new AuthenticatedUser(user.getId(), user.getUsername(), user.getPasswordHash(),
                user.getStatus(), authorities);
    }

    /** The database id - the reason this class exists (see the class javadoc). */
    public Long getUserId() {
        return userId;
    }

    /** The account status, e.g. to show "account locked" somewhere in the UI. */
    public UserStatus getStatus() {
        return status;
    }

    /** True when the account holds the given business role code. */
    public boolean hasRole(String roleCode) {
        return authorities.stream()
                .anyMatch(authority -> authority.getAuthority().equals(AppRoles.authority(roleCode)));
    }

    // ------------------------------------------------------------------ UserDetails

    /** Role codes projected to {@code ROLE_*} authorities, e.g. {@code ROLE_ADMIN}. */
    @Override
    public Collection<GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return username;
    }

    /** True: accounts do not expire in this project (no expiry column exists). */
    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    /** False for {@link UserStatus#LOCKED}: blocked after suspicious activity. */
    @Override
    public boolean isAccountNonLocked() {
        return status != UserStatus.LOCKED;
    }

    /** True: password ageing is out of scope; enforced policy lives in validation. */
    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    /** False for {@link UserStatus#DISABLED}: the account was closed on purpose. */
    @Override
    public boolean isEnabled() {
        return status == UserStatus.ACTIVE;
    }

    // ------------------------------------------------------------------ identity

    /** Two principals are the same user when the id is the same (id is never null here). */
    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof AuthenticatedUser that)) {
            return false;
        }
        return userId.equals(that.userId);
    }

    @Override
    public int hashCode() {
        return userId.hashCode();
    }

    /** Deliberately without the password hash - see the class javadoc. */
    @Override
    public String toString() {
        return "AuthenticatedUser{id=" + userId + ", username='" + username + "', roles="
                + authorities.stream().map(GrantedAuthority::getAuthority).toList() + "}";
    }
}
