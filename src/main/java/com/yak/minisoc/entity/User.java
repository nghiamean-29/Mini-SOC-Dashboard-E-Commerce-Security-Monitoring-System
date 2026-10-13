package com.yak.minisoc.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * A user account: customer, administrator or SOC analyst ({@code users} table).
 *
 * <p><strong>Layer:</strong> entity / domain model. No business service logic here, but the
 * class does protect its own invariants (for example {@link #addRole(Role)} keeps the
 * role set consistent).</p>
 *
 * <h3>What to notice in the mapping</h3>
 * <ul>
 *   <li>{@code passwordHash}, never {@code password}: the column stores a BCrypt hash
 *       (60 characters). The field name makes the intent impossible to misread, and the
 *       BCrypt work itself happens in the service layer (Phase 3), never in the entity.</li>
 *   <li>{@code status} is an enum persisted as a <strong>String</strong>
 *       ({@code @Enumerated(EnumType.STRING)}). With the default {@code ORDINAL} the
 *       database would store 0/1/2 - and reordering the enum constants later would
 *       silently reinterpret existing rows.</li>
 *   <li>{@code roles} is a {@code @ManyToMany} mapped through the {@code user_roles} join
 *       table. The relationship is <strong>unidirectional</strong>: a {@code User} knows
 *       its roles, a {@code Role} does not need to know its users. Skipping the back
 *       reference removes a mapping ({@code mappedBy}) and a whole category of
 *       synchronisation bugs.</li>
 *   <li>{@code Set}, not {@code List}, because the same role must not be assigned twice -
 *       the set expresses that rule and the composite primary key of {@code user_roles}
 *       enforces it in the database.</li>
 * </ul>
 */
@Entity
@Table(name = "users")
public class User extends BaseEntity {

    private static final long serialVersionUID = 1L;

    @Column(name = "username", nullable = false, length = 50)
    private String username;

    @Column(name = "email", nullable = false, length = 120)
    private String email;

    /** BCrypt hash (or future algorithm). Never a plaintext password. */
    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Column(name = "full_name", length = 120)
    private String fullName;

    @Column(name = "phone", length = 20)
    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private UserStatus status = UserStatus.ACTIVE;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    /**
     * LAZY on purpose: most requests never need the roles (browsing products, for
     * example). The login path that does need them fetches them explicitly with an entity
     * graph - see {@code UserRepository#findWithRolesByUsername} - so the decision stays
     * visible in the query instead of happening implicitly on every load.
     */
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "user_roles",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "role_id"))
    private Set<Role> roles = new HashSet<>();

    /** Required by JPA; application code uses {@link #create(String, String, String, String)}. */
    protected User() {
    }

    /** Creates a new account; the password must already be hashed by the caller. */
    public static User create(String username, String email, String passwordHash, String fullName) {
        User user = new User();
        user.username = Objects.requireNonNull(username, "username");
        user.email = Objects.requireNonNull(email, "email");
        user.passwordHash = Objects.requireNonNull(passwordHash, "passwordHash");
        user.fullName = fullName;
        user.status = UserStatus.ACTIVE;
        return user;
    }

    // ------------------------------------------------------------------ behaviour

    /** Assigns a role, ignoring duplicates (a Set would too, but this is explicit). */
    public void addRole(Role role) {
        roles.add(Objects.requireNonNull(role, "role"));
    }

    public void removeRole(Role role) {
        roles.remove(role);
    }

    /** True when the account holds the given role code (e.g. "SOC_ANALYST"). */
    public boolean hasRole(String roleCode) {
        return roles.stream().anyMatch(role -> role.getCode().equals(roleCode));
    }

    /** Read-only view of the assigned roles. */
    public Set<Role> getRoles() {
        return Collections.unmodifiableSet(roles);
    }

    /** Role codes only - what a security layer needs to build authorities. */
    public Set<String> getRoleCodes() {
        return roles.stream().map(Role::getCode).collect(Collectors.toUnmodifiableSet());
    }

    /** Records a successful login (called by the authentication listener in Phase 5). */
    public void recordLogin(Instant at) {
        this.lastLoginAt = Objects.requireNonNull(at, "at");
    }

    /** Locks the account (usually after suspicious activity). */
    public void lock() {
        this.status = UserStatus.LOCKED;
    }

    public void activate() {
        this.status = UserStatus.ACTIVE;
    }

    public void disable() {
        this.status = UserStatus.DISABLED;
    }

    // -------------------------------------------------------------- getters/setters

    public String getUsername() {
        return username;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = Objects.requireNonNull(email, "email");
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    /** Replaces the stored hash. The caller hashes the new password - never this setter. */
    public void changePasswordHash(String newHash) {
        this.passwordHash = Objects.requireNonNull(newHash, "newHash");
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public UserStatus getStatus() {
        return status;
    }

    public Instant getLastLoginAt() {
        return lastLoginAt;
    }
}
