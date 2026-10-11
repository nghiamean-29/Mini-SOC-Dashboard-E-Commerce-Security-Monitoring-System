package com.yak.minisoc.repository;

import com.yak.minisoc.entity.LoginHistory;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Data access for the authentication trail.
 *
 * <p>The second method is the one to look at: it is the query the brute-force rule of
 * Phase 6 runs, and it is written here - not built on the fly - so that it has <em>one</em>
 * definition and can be tuned in one place (with the index
 * {@code idx_login_history_ip_time} in mind).</p>
 */
public interface LoginHistoryRepository extends JpaRepository<LoginHistory, Long> {

    /** Logins of one account, newest first (account detail page). */
    Page<LoginHistory> findByUsernameOrderByOccurredAtDesc(String username, Pageable pageable);

    /**
     * Failed credential attempts from one IP inside a window - the raw material of the
     * brute-force detection.
     *
     * <p>{@code failureReason = BAD_CREDENTIALS} is filtered in the method name, not by the
     * caller, because a locked or disabled account must not count as an attack: those
     * failures repeat forever without anybody trying to break in, and counting them is how
     * a detection rule becomes a false-positive generator.</p>
     */
    @Query("""
            select lh from LoginHistory lh
            where lh.sourceIp = :sourceIp
              and lh.success = false
              and lh.failureReason = com.yak.minisoc.entity.LoginFailureReason.BAD_CREDENTIALS
              and lh.occurredAt >= :since
            order by lh.occurredAt desc
            """)
    List<LoginHistory> findCredentialFailuresFromIp(@Param("sourceIp") String sourceIp,
                                                    @Param("since") Instant since);

    long countByUsernameAndSuccessFalseAndOccurredAtAfter(String username, Instant since);

    long countBySourceIpAndSuccessFalseAndOccurredAtAfter(String sourceIp, Instant since);

    /** Successful logins of an account after a moment - the "attack that worked" signal. */
    List<LoginHistory> findByUserIdAndSuccessTrueAndOccurredAtAfterOrderByOccurredAtAsc(
            Long userId, Instant since);

    List<LoginHistory> findByOccurredAtAfterOrderByOccurredAtDesc(Instant since);

    boolean existsByUsernameAndSuccessTrue(String username);
}
