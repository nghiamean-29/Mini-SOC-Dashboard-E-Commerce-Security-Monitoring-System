package com.yak.minisoc.repository;

import com.yak.minisoc.entity.User;
import com.yak.minisoc.entity.UserStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Data access for user accounts.
 *
 * <p>Extending {@link JpaRepository} (not {@code CrudRepository}) buys three things the
 * project actually uses: {@code Page}/{@code Pageable} for the admin screens,
 * {@code flush()}/{@code saveAndFlush()} to obtain the generated id inside a transaction,
 * and batch {@code deleteAllInBatch()} for test cleanup.</p>
 */
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUsername(String username);

    Optional<User> findByEmail(String email);

    /**
     * Loads a user together with the roles, for authentication.
     *
     * <p>Spring Security needs the authorities in the same request that verifies the
     * password. Without the entity graph the mapping would be lazy and the roles would be
     * fetched by a second query - or, worse, fail with a
     * {@code LazyInitializationException} if the session were already closed (a trap when
     * authentication runs inside a filter rather than a {@code @Transactional} service).
     * Asking for exactly what is needed keeps it one query and no surprise.</p>
     */
    @EntityGraph(attributePaths = "roles")
    @Query("select u from User u where u.username = :username")
    Optional<User> findWithRolesByUsername(@Param("username") String username);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    long countByStatus(UserStatus status);

    /** Newest accounts first, for the admin console. */
    List<User> findAllByOrderByCreatedAtDesc();
}
