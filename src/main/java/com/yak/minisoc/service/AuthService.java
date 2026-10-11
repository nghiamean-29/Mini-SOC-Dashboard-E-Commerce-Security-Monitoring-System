package com.yak.minisoc.service;

import com.yak.minisoc.dto.RegisterRequest;
import com.yak.minisoc.entity.Role;
import com.yak.minisoc.entity.User;
import com.yak.minisoc.exception.DuplicateAccountException;
import com.yak.minisoc.repository.RoleRepository;
import com.yak.minisoc.repository.UserRepository;
import com.yak.minisoc.security.AppRoles;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Account use cases: registration, role assignment and the bootstrap of the first accounts
 * (FR-AUTH-01).
 *
 * <p><strong>Layer contract:</strong> a service owns the transaction, calls repositories,
 * applies business rules, and knows nothing about HTTP. The controller hands it a validated
 * {@link RegisterRequest}; the service returns a {@link User} entity. No {@code Model}, no
 * {@code HttpServletRequest}, no view name in this class - which is exactly why it can be
 * tested with a mocked repository and no web server (see {@code AuthServiceTest}).</p>
 *
 * <h3>Three rules this class enforces</h3>
 * <ol>
 *   <li><strong>Passwords are hashed here, never earlier.</strong> The DTO carries the
 *       plaintext the user typed (it has to - it is what the form sent), and the very first
 *       thing this service does with it is {@link PasswordEncoder#encode}. Nothing else in
 *       the application ever sees the plaintext, and the entity only ever holds the hash.</li>
 *   <li><strong>A new account is always a CUSTOMER.</strong> The role is looked up from the
 *       database and assigned by this service. There is no code path in which the browser
 *       can choose its own role - self-registration that grants ADMIN is the classic
 *       privilege-escalation hole (A01 in the OWASP list).</li>
 *   <li><strong>Uniqueness is checked, then guaranteed by the database.</strong> The
 *       {@code exists...} checks give a friendly message in the normal case; the UNIQUE
 *       constraints {@code uk_users_username}/{@code uk_users_email} (migration V1) are
 *       what makes the rule true when two requests race. Both paths are handled below, and
 *       the second one is not a bug - it is the same pattern used for the alert dedup key
 *       in Phase 7 ("check for the user, let the constraint be the referee").</li>
 * </ol>
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthService(UserRepository userRepository, RoleRepository roleRepository,
                       PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Registers a new customer account.
     *
     * @param request a <em>validated</em> registration form (Bean Validation runs at the
     *                controller boundary, before this method is called)
     * @return the persisted user, already carrying its role
     * @throws DuplicateAccountException when the username or the e-mail address is taken
     */
    @Transactional
    public User register(RegisterRequest request) {
        String username = request.getUsername().trim();
        // E-mail addresses are case-insensitive in practice, so the comparison is too -
        // "Alice@Example.com" and "alice@example.com" must not become two accounts.
        String email = request.getEmail().trim().toLowerCase(java.util.Locale.ROOT);

        if (userRepository.existsByUsername(username)) {
            // Deliberately not logged with the password, and at INFO rather than WARN:
            // a duplicate registration is a normal user mistake, not an incident.
            log.info("Registration rejected: username '{}' already exists", username);
            throw DuplicateAccountException.username(username);
        }
        if (userRepository.existsByEmail(email)) {
            log.info("Registration rejected: e-mail '{}' already exists", email);
            throw DuplicateAccountException.email(email);
        }

        User user = User.create(username, email, passwordEncoder.encode(request.getPassword()),
                trimToNull(request.getFullName()));
        user.addRole(customerRole());

        User saved = saveTranslatingTheUniqueConstraint(user, username, email);
        log.info("Registered new account '{}' (id={}) with role {}", saved.getUsername(),
                saved.getId(), AppRoles.CUSTOMER);
        return saved;
    }

    /**
     * Creates an account with an explicit list of roles - the ONLY way to produce an
     * administrator or a SOC analyst.
     *
     * <p>Called by the bootstrap component at startup (credentials from environment
     * variables) and, from Phase 8, by the admin screens. It is not reachable from any
     * public form, which is what keeps requirement FR-AUTH-08 ("only an ADMIN creates
     * accounts") true.</p>
     *
     * @param roleCodes business codes, e.g. {@code ["ADMIN"]} or
     *                  {@code ["ADMIN", "SOC_ANALYST"]}
     */
    @Transactional
    public User createAccount(String username, String email, String rawPassword, String fullName,
                             List<String> roleCodes) {
        String normalizedEmail = email.trim().toLowerCase(java.util.Locale.ROOT);
        User user = User.create(username.trim(), normalizedEmail,
                passwordEncoder.encode(rawPassword), trimToNull(fullName));

        for (String code : roleCodes) {
            user.addRole(role(code));
        }

        User saved = saveTranslatingTheUniqueConstraint(user, username, normalizedEmail);
        // Account creation is an administrative action: the log line is the minimum audit
        // trail until Phase 5 writes it into audit_logs with the acting administrator.
        log.info("Created account '{}' (id={}) with roles {}", saved.getUsername(), saved.getId(),
                saved.getRoleCodes());
        return saved;
    }

    /**
     * Does the account already exist? Used by the bootstrap to stay idempotent: restarting
     * the application must not create a second administrator (and must not fail either).
     */
    @Transactional(readOnly = true)
    public boolean accountExists(String username) {
        return userRepository.existsByUsername(username);
    }

    // ------------------------------------------------------------------ internals

    /**
     * Saves the user and turns a database constraint violation into the same business
     * exception the pre-checks throw.
     *
     * <p>Why this is needed: between {@code existsByUsername} and the INSERT there is a
     * window of a few milliseconds. Two requests from the same person (a double click, a
     * retry) or from an attacker can both pass the check. The database refuses the second
     * one - correct - but without this translation the user would see a 500 error page for
     * a situation that is really "that username is taken". The message is the same as the
     * pre-check's, so the caller has one code path.</p>
     *
     * <p><strong>The constraint name is checked, not guessed.</strong> Only the two UNIQUE
     * constraints of this table are translated; anything else (a CHECK the validation layer
     * should have caught, a column that is too long, a broken connection) is rethrown
     * unchanged. Reporting "username already taken" for a database problem would send the
     * user chasing a phantom - and would hide a real bug behind a friendly sentence.</p>
     */
    private User saveTranslatingTheUniqueConstraint(User user, String username, String email) {
        try {
            return userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException ex) {
            String detail = ex.getMostSpecificCause().getMessage();
            log.info("Unique constraint refused registration of '{}': {}", username, detail);

            if (mentions(detail, "uk_users_email")) {
                throw DuplicateAccountException.email(email);
            }
            if (mentions(detail, "uk_users_username")) {
                throw DuplicateAccountException.username(username);
            }
            // Unknown database failure: let it surface as a real error (500 + log line),
            // because it is not a duplicate account and must not be reported as one.
            throw ex;
        }
    }

    private static boolean mentions(String detail, String constraintName) {
        return detail != null && detail.contains(constraintName);
    }

    /** The CUSTOMER role as seeded by migration V9. */
    private Role customerRole() {
        return role(AppRoles.CUSTOMER);
    }

    /**
     * Looks up a role by code, failing loudly when the seed data is missing.
     *
     * <p>A missing role is not a user error: it means migration V9 did not run. Throwing
     * here turns that into an immediate, descriptive failure instead of an account that
     * quietly has no rights - a symptom somebody would debug for hours.</p>
     */
    private Role role(String code) {
        return roleRepository.findByCode(code)
                .orElseThrow(() -> new IllegalStateException(
                        "Role '" + code + "' is missing from the database. Migration V9 "
                                + "(db/migration/V9__seed_reference_data.sql) seeds the three roles."));
    }

    /** {@code "  "} becomes null, so an empty optional field is stored as "not provided". */
    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
