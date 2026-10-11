package com.yak.minisoc.security;

import com.yak.minisoc.entity.User;
import com.yak.minisoc.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tells Spring Security who a user is: the bridge between our {@code users} table and the
 * authentication filter chain.
 *
 * <p><strong>The important design decision: users come from the database, not from
 * configuration.</strong> Spring Security can be given a fixed list of users in
 * {@code application.yml} (or an in-memory {@code InMemoryUserDetailsManager}). That is
 * fine for a hello-world, and wrong here for two reasons: a password written in a
 * configuration file is a hard-coded credential (NFR-SEC-02), and a SOC that cannot
 * deactivate an account without restarting the application is not a SOC. Reading the user
 * from the same table the rest of the application uses also means registration, account
 * status and role changes take effect immediately.</p>
 *
 * <p><strong>What is deliberately NOT here:</strong> password comparison. This class only
 * <em>loads</em> the account; {@code DaoAuthenticationProvider} compares the submitted
 * password with the stored BCrypt hash (the {@code PasswordEncoder} bean in
 * {@link com.yak.minisoc.config.SecurityConfig}). Splitting the two is what allows the
 * hash algorithm to change without touching this class.</p>
 *
 * <p><strong>Why the generic {@link UsernameNotFoundException} message:</strong> it is
 * never shown to the user. Spring Security converts "no such user" and "wrong password"
 * into the same {@code BadCredentialsException} at the filter level, so an attacker cannot
 * use the login form to find out which usernames exist (user enumeration). The message
 * lives in the server log, where it is useful for the SOC - and Phase 5 records it in
 * {@code login_history} with the {@code UNKNOWN_USER} reason.</p>
 */
@Service
public class DatabaseUserDetailsService implements UserDetailsService {

    private static final Logger log = LoggerFactory.getLogger(DatabaseUserDetailsService.class);

    private final UserRepository userRepository;

    public DatabaseUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * Loads an account together with its roles.
     *
     * <p>{@code findWithRolesByUsername} uses an entity graph, so the roles arrive in the
     * same query. Without it the mapping would be lazy: authentication runs inside a filter
     * (not inside a controller transaction), and a lazy association there would either
     * trigger a surprise extra query or fail with {@code LazyInitializationException} -
     * {@code spring.jpa.open-in-view} is {@code false} in this project on purpose.</p>
     *
     * @throws UsernameNotFoundException when no account has this username
     */
    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userRepository.findWithRolesByUsername(username)
                .orElseThrow(() -> {
                    // Logged at debug level: a burst of these is exactly what an
                    // enumeration scan looks like, and Phase 6 turns it into an alert.
                    log.debug("Authentication attempt for unknown username '{}'", username);
                    return new UsernameNotFoundException("No account with username '" + username + "'");
                });

        log.debug("Loaded account '{}' with roles {} (status {})",
                user.getUsername(), user.getRoleCodes(), user.getStatus());
        return AuthenticatedUser.from(user);
    }
}
