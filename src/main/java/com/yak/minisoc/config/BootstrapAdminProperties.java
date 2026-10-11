package com.yak.minisoc.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Environment-driven settings for the first administrator account.
 *
 * <p>Bound from the {@code app.bootstrap.admin.*} keys, which in
 * {@code application.yml} read their values from environment variables. This is the
 * project's standard way of handling secrets (NFR-SEC-02): the repository contains the
 * <em>name</em> of the variable, the operator's machine contains the value, and
 * {@code git grep} can never find a password.</p>
 *
 * <table border="1">
 *   <caption>Keys and environment variables</caption>
 *   <tr><th>Key</th><th>Environment variable</th><th>Default</th></tr>
 *   <tr><td>{@code app.bootstrap.admin.username}</td>
 *       <td>{@code APP_BOOTSTRAP_ADMIN_USERNAME}</td><td>none</td></tr>
 *   <tr><td>{@code app.bootstrap.admin.password}</td>
 *       <td>{@code APP_BOOTSTRAP_ADMIN_PASSWORD}</td><td>none</td></tr>
 *   <tr><td>{@code app.bootstrap.admin.email}</td>
 *       <td>{@code APP_BOOTSTRAP_ADMIN_EMAIL}</td><td>none</td></tr>
 *   <tr><td>{@code app.bootstrap.admin.full-name}</td>
 *       <td>{@code APP_BOOTSTRAP_ADMIN_FULL_NAME}</td><td>none</td></tr>
 * </table>
 *
 * <p><strong>Why every field is a {@code String} with {@code null} meaning "not set", even
 * for the username:</strong> Spring Boot binds missing values to {@code null} for objects,
 * which gives a natural way to express "not configured" ({@link #isConfigured()}). An empty
 * default ({@code ""}) would make "not set" and "deliberately empty" indistinguishable, and
 * the bootstrap would try to create an account with an empty username - which the database
 * CHECK would then reject with a much less helpful message.</p>
 *
 * <p><strong>Relaxed binding:</strong> {@code full-name} in YAML, {@code fullName} in Java.
 * Spring Boot maps the kebab-case form automatically, which is why the class needs no
 * {@code @ConstructorBinding} gymnastics.</p>
 */
@ConfigurationProperties(prefix = "app.bootstrap.admin")
public class BootstrapAdminProperties {

    /** Login name of the first administrator, e.g. {@code "yakadmin"}. */
    private String username;

    /** Plaintext password from the environment; hashed by {@link com.yak.minisoc.service.AuthService}. */
    private String password;

    /** Contact address; required, because {@code users.email} is NOT NULL and unique. */
    private String email;

    /** Optional display name shown in the admin area. */
    private String fullName;

    /**
     * True when enough information was supplied to create the account.
     *
     * <p>Checked with {@code isBlank()} rather than {@code == null} on purpose: a variable
     * exported as {@code APP_BOOTSTRAP_ADMIN_PASSWORD=""} is "not configured" for our
     * purposes, and trying to create an account with an empty password would be worse than
     * skipping the bootstrap - it would put an unusable account in the database.</p>
     */
    public boolean isConfigured() {
        return isPresent(username) && isPresent(password) && isPresent(email);
    }

    private static boolean isPresent(String value) {
        return value != null && !value.isBlank();
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    /** Never prints the password. */
    @Override
    public String toString() {
        return "BootstrapAdminProperties{username='" + username + "', email='" + email
                + "', password=[HIDDEN], configured=" + isConfigured() + "}";
    }
}
