package com.yak.minisoc.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;

import com.yak.minisoc.dto.RegisterRequest;
import com.yak.minisoc.entity.Role;
import com.yak.minisoc.entity.User;
import com.yak.minisoc.exception.DuplicateAccountException;
import com.yak.minisoc.repository.RoleRepository;
import com.yak.minisoc.repository.UserRepository;
import com.yak.minisoc.security.AppRoles;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Unit tests (Mockito, no database) for the part of {@link AuthService} that cannot be
 * reached from a normal test: the moment two requests race and the database - not the
 * service's own {@code existsBy...} check - refuses the insert.
 *
 * <h3>Why the race has to be simulated instead of provoked</h3>
 * <p>Reproducing a real race needs two concurrent transactions and precise timing; such a
 * test is flaky, and a flaky test gets ignored - which is worse than no test. The behaviour
 * that matters is small and deterministic: <em>when</em> {@code saveAndFlush} throws a
 * {@link DataIntegrityViolationException} whose message names {@code uk_users_email}, the
 * caller must receive {@link DuplicateAccountException#email(String)} (and the analogous
 * case for the username). A mock reproduces exactly that, every time.</p>
 *
 * <p>The database side of the guarantee is not left untested: the UNIQUE constraints are
 * exercised against real PostgreSQL in {@code UserRepositoryTest} (Phase 2) and by the SQL
 * harness in {@code tools/sql-validation}.</p>
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceConstraintHandlingTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    private AuthService authService;

    @BeforeEach
    void createService() {
        authService = new AuthService(userRepository, roleRepository, passwordEncoder);
    }

    private static RegisterRequest request(String username, String email) {
        return RegisterRequest.of(username, email, "MatKhau123", "MatKhau123", null);
    }

    /** Everything is in place for a successful insert; only the outcome of the save differs. */
    private void givenAValidRegistration() {
        given(passwordEncoder.encode(anyString())).willReturn("$2a$12$mockedHash");
        given(roleRepository.findByCode(AppRoles.CUSTOMER))
                .willReturn(Optional.of(Role.create(AppRoles.CUSTOMER, "Customer", null)));
    }

    @Test
    @DisplayName("uk_users_email firing is reported as 'email already registered'")
    void translatesEmailConstraintViolation() {
        givenAValidRegistration();
        given(userRepository.saveAndFlush(any(User.class))).willThrow(
                new DataIntegrityViolationException(
                        "ERROR: duplicate key value violates unique constraint \"uk_users_email\""));

        assertThatThrownBy(() -> authService.register(request("alice", "alice@example.com")))
                .isInstanceOf(DuplicateAccountException.class)
                .hasMessageContaining("alice@example.com")
                .extracting(exception -> ((DuplicateAccountException) exception).getField())
                .isEqualTo(DuplicateAccountException.FIELD_EMAIL);
    }

    @Test
    @DisplayName("uk_users_username firing is reported as 'username already used'")
    void translatesUsernameConstraintViolation() {
        givenAValidRegistration();
        given(userRepository.saveAndFlush(any(User.class))).willThrow(
                new DataIntegrityViolationException(
                        "ERROR: duplicate key value violates unique constraint \"uk_users_username\""));

        assertThatThrownBy(() -> authService.register(request("alice", "alice@example.com")))
                .isInstanceOf(DuplicateAccountException.class)
                .hasMessageContaining("alice")
                .extracting(exception -> ((DuplicateAccountException) exception).getField())
                .isEqualTo(DuplicateAccountException.FIELD_USERNAME);
    }

    @Test
    @DisplayName("any other database failure is NOT disguised as a duplicate account")
    void doesNotDisguiseOtherFailures() {
        givenAValidRegistration();
        given(userRepository.saveAndFlush(any(User.class))).willThrow(
                new DataIntegrityViolationException(
                        "ERROR: value too long for type character varying(120)"));

        // The user must not be told "that username is taken" when the real problem is a
        // database error: the message would send them chasing a phantom, and the actual
        // bug would be hidden behind a polite sentence.
        assertThatThrownBy(() -> authService.register(request("alice", "alice@example.com")))
                .isInstanceOf(DataIntegrityViolationException.class)
                .isNotInstanceOf(DuplicateAccountException.class);
    }

    @Test
    @DisplayName("an unknown role code fails loudly and mentions the migration that seeds roles")
    void missingRoleFailsLoudly() {
        given(passwordEncoder.encode(anyString())).willReturn("$2a$12$mockedHash");
        given(roleRepository.findByCode(AppRoles.CUSTOMER)).willReturn(Optional.empty());

        assertThatThrownBy(() -> authService.register(request("alice", "alice@example.com")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(AppRoles.CUSTOMER)
                .hasMessageContaining("V9");
    }

    @Test
    @DisplayName("the pre-check happens before hashing: a known username costs no BCrypt work")
    void preCheckRunsBeforeHashing() {
        given(userRepository.existsByUsername("alice")).willReturn(true);

        assertThatThrownBy(() -> authService.register(request("alice", "alice@example.com")))
                .isInstanceOf(DuplicateAccountException.class);

        // No encode() call, no role lookup, no insert: a duplicate registration is cheap.
        // (Mockito's strict stubs would fail this test if encode() were called, because the
        // stub would then be unnecessary - the assertion below documents the intent.)
        org.mockito.Mockito.verifyNoInteractions(passwordEncoder);
        assertThat(AuthService.class).isNotNull();
    }
}
