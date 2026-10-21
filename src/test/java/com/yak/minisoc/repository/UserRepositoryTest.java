
    package com.yak.minisoc.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import com.yak.minisoc.entity.Role;
import com.yak.minisoc.entity.User;
import com.yak.minisoc.entity.UserStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

/**
 * Persistence tests for {@link UserRepository}.
 *
 * <p>
 * @DataJpaTest starts the JPA part of the application:
 * entities, repositories and transaction management.
 * Each test runs in a transaction that is rolled back afterwards.
 * </p>
 *
 * <p>
 * Replace.NONE ensures that the configured PostgreSQL DataSource
 * is used instead of an in-memory database.
 * </p>
 */
@DataJpaTest
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@AutoConfigureTestDatabase(
    replace = AutoConfigureTestDatabase.Replace.NONE
)
@ActiveProfiles("test")
class UserRepositoryTest {

    @Autowired
    private UserRepository users;

    @Autowired
    private RoleRepository roles;

    @PersistenceContext
    private EntityManager em;

    private Role customer;
    private Role admin;

    @BeforeEach
    void loadSeededRoles() {
        customer = roles.findByCode("CUSTOMER").orElseThrow();
        admin = roles.findByCode("ADMIN").orElseThrow();
        roles.findByCode("SOC_ANALYST").orElseThrow();
    }

    @Test
    @DisplayName("migration V9 seeds exactly the three roles of the specification")
    void seedsThreeRoles() {
        assertThat(roles.findAll())
            .extracting(Role::getCode)
            .containsExactlyInAnyOrder(
                "CUSTOMER",
                "ADMIN",
                "SOC_ANALYST"
            );
    }

    @Test
    @DisplayName("a user is stored with its roles and loaded back through the entity graph")
    void savesUserWithRoles() {
        User user = User.create(
            "alice",
            "alice@example.com",
            "$2a$10$abcdefghijklmnopqrstuv",
            "Alice Nguyen"
        );

        user.addRole(customer);
        users.saveAndFlush(user);

        em.clear();

        User loaded = users.findWithRolesByUsername("alice")
            .orElseThrow();

        assertThat(loaded.getId()).isNotNull();
        assertThat(loaded.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(loaded.getRoleCodes()).containsExactly("CUSTOMER");
        assertThat(loaded.hasRole("ADMIN")).isFalse();
    }

    @Test
    @DisplayName("the roles view is read-only: callers must use addRole/removeRole")
    void rolesViewCannotBeMutatedDirectly() {
        User user = User.create(
            "bob",
            "bob@example.com",
            "$2a$10$abcdefghijklmnopqrstuv",
            null
        );

        assertThatThrownBy(() -> user.getRoles().clear())
            .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("uk_users_username: two accounts cannot share a username")
    void rejectsDuplicateUsername() {
        users.saveAndFlush(
            User.create(
                "carol",
                "carol@example.com",
                "$2a$10$hash",
                null
            )
        );

        User duplicate = User.create(
            "carol",
            "other@example.com",
            "$2a$10$hash",
            null
        );

        assertThatThrownBy(() -> users.saveAndFlush(duplicate))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("uk_users_email: two accounts cannot share an email")
    void rejectsDuplicateEmail() {
        users.saveAndFlush(
            User.create(
                "dave",
                "dave@example.com",
                "$2a$10$hash",
                null
            )
        );

        User duplicate = User.create(
            "dave2",
            "dave@example.com",
            "$2a$10$hash",
            null
        );

        assertThatThrownBy(() -> users.saveAndFlush(duplicate))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("ck_users_username_format: the database refuses invalid usernames")
    void rejectsInvalidUsernameFormat() {
        User invalid = User.create(
            "has space",
            "space@example.com",
            "$2a$10$hash",
            null
        );

        assertThatThrownBy(() -> users.saveAndFlush(invalid))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("login recording and password change are persisted")
    void recordsLoginAndPasswordChange() {
        User user = User.create(
            "erin",
            "erin@example.com",
            "$2a$10$old",
            null
        );

        user.addRole(admin);
        users.saveAndFlush(user);

        Instant now = Instant.now();

        user.recordLogin(now);
        user.changePasswordHash("$2a$10$new");

        users.saveAndFlush(user);
        em.clear();

        User reloaded = users.findByUsername("erin")
            .orElseThrow();

        assertThat(reloaded.getLastLoginAt())
            .isCloseTo(now, within(1, ChronoUnit.MICROS));

        assertThat(reloaded.getPasswordHash())
            .isEqualTo("$2a$10$new");

        assertThat(reloaded.getRoleCodes())
            .containsExactly("ADMIN");
    }

    @Test
    @DisplayName("countByStatus follows the account lifecycle")
    void countsUsersByStatus() {
        User active = User.create(
            "frank",
            "frank@example.com",
            "$2a$10$hash",
            null
        );

        User locked = User.create(
            "gina",
            "gina@example.com",
            "$2a$10$hash",
            null
        );

        locked.lock();

        users.saveAndFlush(active);
        users.saveAndFlush(locked);

        assertThat(users.countByStatus(UserStatus.ACTIVE))
            .isEqualTo(1);

        assertThat(users.countByStatus(UserStatus.LOCKED))
            .isEqualTo(1);

        assertThat(users.countByStatus(UserStatus.DISABLED))
            .isZero();
    }
}
