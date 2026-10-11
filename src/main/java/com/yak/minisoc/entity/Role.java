package com.yak.minisoc.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.Objects;

/**
 * A security role ({@code roles} table): CUSTOMER, ADMIN, SOC_ANALYST.
 *
 * <p><strong>Why a table instead of an enum?</strong> The three roles are fixed by the
 * specification, so an enum would be tempting. A table wins for three practical reasons:
 * roles are granted to users through {@code user_roles} (a foreign key needs a key, not a
 * Java constant), an administrator can add a role later without a redeployment, and the
 * audit trail of "who has this role" lives in the database where it can be queried.</p>
 *
 * <p>Spring Security will read {@code code} and turn it into an authority such as
 * {@code ROLE_SOC_ANALYST} (Phase 3); the mapping between the two happens in the security
 * package, not here - the entity does not know the framework.</p>
 */
@Entity
@Table(name = "roles")
public class Role extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** Machine name used in code and in Spring Security authorities. */
    @Column(name = "code", nullable = false, length = 30)
    private String code;

    /** Human readable name shown in the administration screens. */
    @Column(name = "name", nullable = false, length = 60)
    private String name;

    @Column(name = "description", length = 255)
    private String description;

    protected Role() {
    }

    public static Role create(String code, String name, String description) {
        Role role = new Role();
        role.code = Objects.requireNonNull(code, "code");
        role.name = Objects.requireNonNull(name, "name");
        role.description = description;
        return role;
    }

    /** True when this role describes the given code, null-safe. */
    public boolean hasCode(String otherCode) {
        return code != null && code.equals(otherCode);
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = Objects.requireNonNull(name, "name");
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}
