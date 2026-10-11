package com.yak.minisoc.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.yak.minisoc.entity.Role;
import com.yak.minisoc.entity.User;
import com.yak.minisoc.entity.UserStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.Instant;
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
 * <p><strong>What a slice test is:</strong> {@code @DataJpaTest} starts only the JPA part of
 * the application - entities, repositories, transaction manager - and no web server, no
 * security filter chain. Each test method runs in a transaction that is rolled back
 * afterwards, so tests cannot see each other's data.</p>
 *
 * <p><strong>Why {@code Replace.NONE}:</strong> it tells Spring Boot "use the DataSource I
 * configured, do not swap it for an in-memory database". That is what makes these tests run
 * against real PostgreSQL - see {@code src/test/resources/application-test.yml}.</p>
 *
 * <p><strong>Division of labour with the SQL validation harness:</strong> this class tests
 * the <em>mapping and the queries</em> (does the entity reach the right table, does the
 * query return the right rows). The DDL itself - every CHECK/UNIQUE/FOREIGN KEY rule of the
 * migrations - is verified by {@code tools/sql-validation}, which runs the migration files
 * on PostgreSQL and tries to insert bad data. Both are needed; neither duplicates the
 * other.</p>
 */
@DataJpaTest
// Flyway is NOT part of the @DataJpaTest slice in Spring Boot 4: the slice only
// imports the JPA/JDBC auto-configurations, so the migrations must be imported
// explicitly or the schema would simply not exist when Hibernate validates it.
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
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
        // These three rows are inserted by migration V9, not by the test: if the seed
        // migration ever breaks, this line fails and the test says so.
        customer = roles.findByCode("CUSTOMER").orElseThrow();
        admin = roles.findByCode("ADMIN").orElseThrow();
        roles.findByCode("SOC_ANALYST").orElseThrow();
    }

    @Test
    @DisplayName("migration V9 seeds exactly the three roles of the specification")
    void seedsThreeRoles() {
        assertThat(roles.findAll())
                .extracting(Role::getCode)
                .containsExactlyInAnyOrder("CUSTOMER", "ADMIN", "SOC_ANALYST");
    }

    @Test
    @DisplayName("a user is stored with its roles and loaded back through the entity graph")
    void savesUserWithRoles() {
        User user = User.create("alice", "alice@example.com", "$2a$10$abcdefghijklmnopqrstuv", "Alice Nguyen");
        user.addRole(customer);
        users.saveAndFlush(user);

        em.clear();   // force a real SELECT instead of reading the first-level cache

        User loaded = users.findWithRolesByUsername("alice").orElseThrow();
        assertThat(loaded.getId()).isNotNull();
        assertThat(loaded.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(loaded.getRoleCodes()).containsExactly("CUSTOMER");
        assertThat(loaded.hasRole("ADMIN")).isFalse();
    }

    @Test
    @DisplayName("the roles view is read-only: callers must use addRole/removeRole")
    void rolesViewCannotBeMutatedDirectly() {
        User user = User.create("bob", "bob@example.com", "$2a$10$abcdefghijklmnopqrstuv", null);

        assertThatThrownBy(() -> user.getRoles().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("uk_users_username: two accounts cannot share a username")
    void rejectsDuplicateUsername() {
        users.saveAndFlush(User.create("carol", "carol@example.com", "$2a$10$hash", null));

        User duplicate = User.create("carol", "other@example.com", "$2a$10$hash", null);

        assertThatThrownBy(() -> users.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("uk_users_email: two accounts cannot share an email")
    void rejectsDuplicateEmail() {
        users.saveAndFlush(User.create("dave", "dave@example.com", "$2a$10$hash", null));

        User duplicate = User.create("dave2", "dave@example.com", "$2a$10$hash", null);

        assertThatThrownBy(() -> users.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("ck_users_username_format: the database refuses ugly usernames too")
    void rejectsInvalidUsernameFormat() {
        // 'has space' would be a problem in URLs and log lines; the regex CHECK in V1
        // rejects it even if some future code path forgets to validate.
        User invalid = User.create("has space", "space@example.com", "$2a$10$hash", null);

        assertThatThrownBy(() -> users.saveAndFlush(invalid))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("login recording and password change are persisted")
    void recordsLoginAndPasswordChange() {
        User user = User.create("erin", "erin@example.com", "$2a$10$old", null);
        user.addRole(admin);
        users.saveAndFlush(user);

        Instant now = Instant.now();
        user.recordLogin(now);
        user.changePasswordHash("$2a$10$new");
        users.saveAndFlush(user);
        em.clear();

        User reloaded = users.findByUsername("erin").orElseThrow();
        assertThat(reloaded.getLastLoginAt()).isEqualTo(now);
        assertThat(reloaded.getPasswordHash()).isEqualTo("$2a$10$new");
        assertThat(reloaded.getRoleCodes()).containsExactly("ADMIN");
    }

    @Test
    @DisplayName("countByStatus follows the account lifecycle")
    void countsUsersByStatus() {
        User active = User.create("frank", "frank@example.com", "$2a$10$hash", null);
        User locked = User.create("gina", "gina@example.com", "$2a$10$hash", null);
        locked.lock();
        users.saveAndFlush(active);
        users.saveAndFlush(locked);

        assertThat(users.countByStatus(UserStatus.ACTIVE)).isEqualTo(1);
        assertThat(users.countByStatus(UserStatus.LOCKED)).isEqualTo(1);
        assertThat(users.countByStatus(UserStatus.DISABLED)).isZero();
    }
}
