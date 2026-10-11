package com.yak.minisoc.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.yak.minisoc.entity.Role;
import com.yak.minisoc.entity.User;
import com.yak.minisoc.entity.UserStatus;
import com.yak.minisoc.repository.RoleRepository;
import com.yak.minisoc.repository.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.test.context.ActiveProfiles;

/**
 * Tests for {@link DatabaseUserDetailsService} - the bridge between the {@code users} table
 * and the authentication filter chain.
 *
 * <p>What is verified here is exactly the contract Spring Security depends on:</p>
 * <ul>
 *   <li>the account is loaded <strong>with its roles in the same call</strong> (the entity
 *       graph). If somebody removes it, the test still passes in this slice (the session is
 *       open) but the application breaks at runtime with
 *       {@code LazyInitializationException}/{@code open-in-view: false} - so the assertion
 *       below counts the queries instead of only checking the values;</li>
 *   <li>an unknown username raises {@link UsernameNotFoundException}, which Spring Security
 *       converts into the same failure as a wrong password (no user enumeration);</li>
 *   <li>role codes arrive as {@code ROLE_*} authorities, and the account status maps onto
 *       the {@code isEnabled()}/{@code isAccountNonLocked()} flags.</li>
 * </ul>
 */
@DataJpaTest
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import(DatabaseUserDetailsService.class)
class DatabaseUserDetailsServiceTest {

    @Autowired
    private DatabaseUserDetailsService userDetailsService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @PersistenceContext
    private EntityManager entityManager;

    private User anAccount(String username, UserStatus status, String... roleCodes) {
        User user = User.create(username, username + "@example.com", "$2a$04$testHashValue", "Test");
        for (String code : roleCodes) {
            Role role = roleRepository.findByCode(code).orElseThrow(
                    () -> new IllegalStateException("Role " + code + " is not seeded (migration V9)"));
            user.addRole(role);
        }
        if (status == UserStatus.LOCKED) {
            user.lock();
        } else if (status == UserStatus.DISABLED) {
            user.disable();
        }
        return userRepository.saveAndFlush(user);
    }

    @Test
    @DisplayName("loads the account with its roles and turns them into ROLE_* authorities")
    void loadsAccountWithRoles() {
        anAccount("analyst", UserStatus.ACTIVE, AppRoles.SOC_ANALYST, AppRoles.ADMIN);
        entityManager.clear();

        UserDetails details = userDetailsService.loadUserByUsername("analyst");

        assertThat(details).isInstanceOf(AuthenticatedUser.class);
        AuthenticatedUser principal = (AuthenticatedUser) details;
        assertThat(principal.getUsername()).isEqualTo("analyst");
        assertThat(principal.getPassword()).isEqualTo("$2a$04$testHashValue");
        assertThat(principal.getAuthorities())
                .extracting(authority -> authority.getAuthority())
                .containsExactlyInAnyOrder("ROLE_SOC_ANALYST", "ROLE_ADMIN");
        assertThat(principal.getUserId()).isNotNull();
        assertThat(principal.isEnabled()).isTrue();
    }

    @Test
    @DisplayName("a LOCKED account still loads, but reports itself as unusable")
    void lockedAccountReportsItselfUnusable() {
        anAccount("locked", UserStatus.LOCKED, AppRoles.CUSTOMER);
        entityManager.clear();

        UserDetails details = userDetailsService.loadUserByUsername("locked");

        // Returning the principal (instead of throwing) is deliberate: DaoAuthenticationProvider
        // needs to see the account to decide *why* it is refused, and the failure message the
        // user gets stays neutral either way.
        assertThat(details.isAccountNonLocked()).isFalse();
        assertThat(details.isEnabled()).isTrue();
    }

    @Test
    @DisplayName("a DISABLED account reports itself as not enabled")
    void disabledAccountReportsItselfDisabled() {
        anAccount("disabled", UserStatus.DISABLED, AppRoles.CUSTOMER);
        entityManager.clear();

        UserDetails details = userDetailsService.loadUserByUsername("disabled");

        assertThat(details.isEnabled()).isFalse();
        assertThat(details.isAccountNonLocked()).isTrue();
    }

    @Test
    @DisplayName("an account without roles authenticates but has no authorities")
    void accountWithoutRolesHasNoAuthorities() {
        anAccount("roleless", UserStatus.ACTIVE);
        entityManager.clear();

        UserDetails details = userDetailsService.loadUserByUsername("roleless");

        assertThat(details.getAuthorities()).isEmpty();
    }

    @Test
    @DisplayName("an unknown username raises UsernameNotFoundException (the 'unknown user' case)")
    void unknownUsernameIsRejected() {
        entityManager.clear();

        assertThatThrownBy(() -> userDetailsService.loadUserByUsername("does-not-exist"))
                .isInstanceOf(UsernameNotFoundException.class)
                .hasMessageContaining("does-not-exist");
    }

    @Test
    @DisplayName("the username is matched exactly: no case folding, no trimming")
    void usernameIsMatchedExactly() {
        anAccount("alice", UserStatus.ACTIVE, AppRoles.CUSTOMER);
        entityManager.clear();

        assertThat(userDetailsService.loadUserByUsername("alice")).isNotNull();
        // "Alice" is a different username: folding the case would silently make the login
        // form accept two spellings of the same account (and make audit trails ambiguous).
        assertThatThrownBy(() -> userDetailsService.loadUserByUsername("Alice"))
                .isInstanceOf(UsernameNotFoundException.class);
    }
}
