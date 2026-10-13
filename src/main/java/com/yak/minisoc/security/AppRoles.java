package com.yak.minisoc.security;

import java.util.List;

/**
 * The three role codes of the project, in one place.
 *
 * <p><strong>Why a constants class and not just string literals?</strong> These three
 * words appear in a lot of places: the {@code roles} table (seeded by migration V9), the
 * {@code @PreAuthorize} expressions, the URL rules in
 * {@link com.yak.minisoc.config.SecurityConfig}, the navigation menu, the bootstrap of the
 * first accounts and later the detection rules. A typo such as {@code "SOC_ANLAYST"} would
 * not fail at compile time - it would silently produce an empty role set, and the symptom
 * ("this user cannot see anything, but only sometimes") is one of the most expensive kinds
 * of bug to find. With a constant the typo does not compile.</p>
 *
 * <p><strong>Why the {@code ROLE_} prefix is added here and not stored in the database:</strong>
 * Spring Security decides authorization on <em>authorities</em>, and the
 * {@code hasRole("ADMIN")} expression you write in code is internally translated to the
 * authority {@code ROLE_ADMIN}. The prefix is a Spring Security convention, not a fact
 * about our business - so the database keeps the clean business code
 * ({@code ADMIN}) and this class applies the framework convention at the boundary.
 * Mixing them up is a classic mistake: {@code hasRole("ROLE_ADMIN")} silently checks for
 * {@code ROLE_ROLE_ADMIN} and always denies.</p>
 */
public final class AppRoles {

    /** Shopper: browses products, manages a cart, places and cancels their own orders. */
    public static final String CUSTOMER = "CUSTOMER";

    /** Administrator: manages the catalogue, orders, users and can open the SOC screens. */
    public static final String ADMIN = "ADMIN";

    /** Security analyst: monitors events, investigates alerts and incidents. */
    public static final String SOC_ANALYST = "SOC_ANALYST";

    /** Prefix Spring Security expects for role-based authorities ({@code hasRole(...)}). */
    public static final String ROLE_PREFIX = "ROLE_";

    /** All codes, for validation and for the admin screens (Phase 8). */
    public static final List<String> ALL = List.of(CUSTOMER, ADMIN, SOC_ANALYST);

    private AppRoles() {
        // Constants only: never instantiated.
    }

    /**
     * Turns a database role code into a Spring Security authority.
     *
     * @param roleCode e.g. {@code "SOC_ANALYST"} - as stored in {@code roles.code}
     * @return e.g. {@code "ROLE_SOC_ANALYST"}
     */
    public static String authority(String roleCode) {
        if (roleCode == null || !roleCode.startsWith(ROLE_PREFIX)) {
            return ROLE_PREFIX + roleCode;
        }
        // Defensive: calling this twice must not produce "ROLE_ROLE_ADMIN".
        return roleCode;
    }

    /** Is this code one of the three roles the application knows? */
    public static boolean isKnown(String roleCode) {
        return ALL.contains(roleCode);
    }
}
