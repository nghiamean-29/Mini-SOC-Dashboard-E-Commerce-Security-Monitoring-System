package com.yak.minisoc.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.yak.minisoc.dto.RegisterRequest;
import com.yak.minisoc.entity.User;
import com.yak.minisoc.entity.UserStatus;
import com.yak.minisoc.exception.DuplicateAccountException;
import com.yak.minisoc.repository.UserRepository;
import com.yak.minisoc.security.AppRoles;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

/**
 * Tests for {@link AuthService} against a real PostgreSQL database.
 *
 * <h3>How this class is put together, and why</h3>
 * <ul>
 *   <li><strong>{@code @DataJpaTest} + {@code @Import(AuthService.class)}:</strong> the slice
 *       gives us the repositories and the database, the import adds the one class under test.
 *       There is no web server and no security filter chain - so a failure here is about the
 *       registration logic, never about HTTP.</li>
 *   <li><strong>The password encoder is replaced by a weak one (strength 4).</strong>
 *       BCrypt with strength 12 takes ~0.3 s <em>per hash by design</em>; a dozen tests would
 *       spend seconds hashing, and the cost would grow with every future test. Strength 4
 *       produces the same algorithm, salt and format with a cheaper cost factor. That the
 *       production bean really is strength 12 is asserted in {@code SecurityConfigTest} -
 *       exactly one place checks the configuration, so this speed-up cannot hide a mistake.</li>
 *   <li><strong>Flyway is imported explicitly</strong> - Boot 4 slices do not include it
 *       (see docs/PHASE-02-DATABASE.md section 3.3).</li>
 * </ul>
 *
 * <p>The three roles come from migration V9, so {@code customerRole()} inside the service
 * resolves - and if that seed migration ever breaks, these tests fail with the message
 * "Role 'CUSTOMER' is missing from the database" instead of failing mysteriously later.</p>
 */
@DataJpaTest
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import(AuthService.class)
class AuthServiceTest {

    /** Fast BCrypt for tests; the real strength (12) is verified in SecurityConfigTest. */
    @TestConfiguration
    static class FastPasswordEncoderConfig {
        @Bean
        PasswordEncoder passwordEncoder() {
            return new BCryptPasswordEncoder(4);
        }
    }

    @Autowired
    private AuthService authService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private static RegisterRequest request(String username, String email, String password) {
        return RegisterRequest.of(username, email, password, password, "Nguyen Van A");
    }

    @Test
    @DisplayName("registration stores a BCrypt hash, never the password, and assigns CUSTOMER")
    void registersACustomer() {
        User created = authService.register(request("alice", "Alice@Example.com", "MatKhau123"));

        assertThat(created.getId()).isNotNull();
        assertThat(created.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(created.getRoleCodes()).containsExactly(AppRoles.CUSTOMER);

        // The stored value is a hash that verifies against the submitted password...
        assertThat(created.getPasswordHash()).isNotEqualTo("MatKhau123");
        assertThat(created.getPasswordHash()).startsWith("$2");
        assertThat(passwordEncoder.matches("MatKhau123", created.getPasswordHash())).isTrue();

        // ...and the database row (not just the in-memory object) has the same properties.
        User reloaded = userRepository.findWithRolesByUsername("alice").orElseThrow();
        assertThat(reloaded.getEmail())
                .as("e-mail is normalised to lower case so it cannot be registered twice")
                .isEqualTo("alice@example.com");
        assertThat(reloaded.getFullName()).isEqualTo("Nguyen Van A");
        assertThat(passwordEncoder.matches("MatKhau123", reloaded.getPasswordHash())).isTrue();
    }

    @Test
    @DisplayName("two users with the same password get different hashes (BCrypt salt)")
    void samePasswordGivesDifferentHashes() {
        User alice = authService.register(request("alice", "alice@example.com", "MatKhau123"));
        User bob = authService.register(request("bob", "bob@example.com", "MatKhau123"));

        assertThat(alice.getPasswordHash()).isNotEqualTo(bob.getPasswordHash());
        assertThat(passwordEncoder.matches("MatKhau123", alice.getPasswordHash())).isTrue();
        assertThat(passwordEncoder.matches("MatKhau123", bob.getPasswordHash())).isTrue();
    }

    @Test
    @DisplayName("the username is trimmed and the e-mail lower-cased before storing")
    void normalisesInput() {
        User created = authService.register(request("  carol  ", "  CAROL@Example.COM  ", "MatKhau123"));

        assertThat(created.getUsername()).isEqualTo("carol");
        assertThat(created.getEmail()).isEqualTo("carol@example.com");
    }

    @Test
    @DisplayName("an omitted full name is stored as NULL, not as an empty string")
    void blankFullNameBecomesNull() {
        User created = authService.register(
                RegisterRequest.of("dave", "dave@example.com", "MatKhau123", "MatKhau123", "   "));

        assertThat(created.getFullName()).isNull();
    }

    @Test
    @DisplayName("duplicate username -> DuplicateAccountException pointing at the username field")
    void rejectsDuplicateUsername() {
        authService.register(request("erin", "erin@example.com", "MatKhau123"));

        assertThatThrownBy(() -> authService.register(request("erin", "other@example.com", "MatKhau123")))
                .isInstanceOf(DuplicateAccountException.class)
                .hasMessageContaining("erin")
                .extracting(exception -> ((DuplicateAccountException) exception).getField())
                .isEqualTo(DuplicateAccountException.FIELD_USERNAME);
    }

    @Test
    @DisplayName("duplicate e-mail (any case) -> DuplicateAccountException on the email field")
    void rejectsDuplicateEmailIgnoringCase() {
        authService.register(request("frank", "frank@example.com", "MatKhau123"));

        assertThatThrownBy(() -> authService.register(request("frank2", "FRANK@Example.com", "MatKhau123")))
                .isInstanceOf(DuplicateAccountException.class)
                .extracting(exception -> ((DuplicateAccountException) exception).getField())
                .isEqualTo(DuplicateAccountException.FIELD_EMAIL);
    }

    @Test
    @DisplayName("a rejected registration leaves nothing behind")
    void rejectedRegistrationIsNotPersisted() {
        authService.register(request("gina", "gina@example.com", "MatKhau123"));
        long countBefore = userRepository.count();

        assertThatThrownBy(() -> authService.register(request("gina", "gina2@example.com", "MatKhau123")))
                .isInstanceOf(DuplicateAccountException.class);

        assertThat(userRepository.count()).isEqualTo(countBefore);
    }

    @Test
    @DisplayName("createAccount() assigns exactly the roles it is given - the only path to ADMIN")
    void createAccountAssignsExplicitRoles() {
        User admin = authService.createAccount("yakadmin", "yakadmin@example.com", "MatKhau123",
                "Quản trị viên", List.of(AppRoles.ADMIN, AppRoles.SOC_ANALYST));

        assertThat(admin.getRoleCodes())
                .containsExactlyInAnyOrder(AppRoles.ADMIN, AppRoles.SOC_ANALYST);
        assertThat(admin.getStatus()).isEqualTo(UserStatus.ACTIVE);

        User reloaded = userRepository.findWithRolesByUsername("yakadmin").orElseThrow();
        assertThat(reloaded.getRoleCodes()).hasSize(2);
    }

    @Test
    @DisplayName("accountExists() tells the bootstrap whether to create the admin (idempotent start-up)")
    void accountExistsDrivesTheBootstrap() {
        assertThat(authService.accountExists("yakadmin")).isFalse();

        authService.createAccount("yakadmin", "yakadmin@example.com", "MatKhau123", null,
                List.of(AppRoles.ADMIN));

        assertThat(authService.accountExists("yakadmin")).isTrue();
        // Calling createAccount again would now fail; the bootstrap checks first, which is
        // what makes restarting the application harmless.
        assertThatThrownBy(() -> authService.createAccount("yakadmin", "other@example.com",
                "MatKhau123", null, List.of(AppRoles.ADMIN)))
                .isInstanceOf(DuplicateAccountException.class);
    }

    @Test
    @DisplayName("an unknown role code fails loudly instead of creating a rightless account")
    void unknownRoleCodeFailsLoudly() {
        assertThatThrownBy(() -> authService.createAccount("nobody", "nobody@example.com",
                "MatKhau123", null, List.of("SUPERUSER")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SUPERUSER")
                .hasMessageContaining("V9");
    }

    @Test
    @DisplayName("the database still knows the account with status ACTIVE and a null last_login_at")
    void newAccountHasNoLoginHistoryYet() {
        User created = authService.register(request("henry", "henry@example.com", "MatKhau123"));

        assertThat(created.getLastLoginAt())
                .as("login_history and last_login_at are written by Phase 5, not by registration")
                .isNull();
    }
}
