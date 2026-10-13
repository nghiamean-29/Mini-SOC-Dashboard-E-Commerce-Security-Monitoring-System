package com.yak.minisoc.repository;

import com.yak.minisoc.entity.Order;
import com.yak.minisoc.entity.OrderStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Data access for orders.
 *
 * <p>The last method, {@link #nextOrderNumber()}, is the only write-side helper here and it
 * is worth reading: it is a native query against the PostgreSQL sequence created in
 * migration V4. JPQL cannot express {@code nextval()}, so the native query is not a
 * shortcut but the only correct tool - and it is buried in the repository layer where the
 * rest of the application cannot accidentally depend on PostgreSQL-specific SQL.</p>
 */
public interface OrderRepository extends JpaRepository<Order, Long> {

    Optional<Order> findByOrderCode(String orderCode);

    boolean existsByOrderCode(String orderCode);

    /** Order detail / confirmation page: order plus its lines in one query. */
    @EntityGraph(attributePaths = {"items", "items.product"})
    Optional<Order> findWithItemsByOrderCode(String orderCode);

    /** "My orders", newest first, paged. */
    Page<Order> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    /** Admin view: one status at a time, oldest first (the queue that must be worked). */
    Page<Order> findByStatusOrderByCreatedAtAsc(OrderStatus status, Pageable pageable);

    List<Order> findByUserIdAndStatus(Long userId, OrderStatus status);

    long countByStatus(OrderStatus status);

    long countByCreatedAtAfter(Instant since);

    /**
     * Revenue of a period, counting only orders that actually happened.
     *
     * <p>Cancelled orders are excluded in the query: a shop whose revenue report includes
     * orders that were cancelled is a shop that will have an unpleasant conversation with
     * its accountant. The rule belongs here - next to the data - rather than in a service
     * method that somebody may forget to use.</p>
     */
    @Query("""
            select sum(o.totalAmount) from Order o
            where o.status <> com.yak.minisoc.entity.OrderStatus.CANCELLED
              and o.createdAt >= :from and o.createdAt < :to
            """)
    BigDecimal sumRevenueBetween(@Param("from") Instant from, @Param("to") Instant to);
    /*
     * Note on the return type: a SUM over zero rows is NULL in SQL, not 0 - so this method
     * legitimately returns null for a day without orders. That is left visible instead of
     * being hidden behind coalesce(): the report service must decide what "no sales" means
     * for the screen (0 VND or "no data"), and a silent 0 in the query would make that
     * decision by accident.
     */

    /**
     * Next number for a human readable order code.
     *
     * <p>{@code nextval} is atomic: two simultaneous checkouts get two different numbers,
     * which is what makes the {@code ORD-...} code collision-free <em>before</em> the
     * insert. See the sequence comment in migration V4 for the rejected alternatives.</p>
     */
    @Query(value = "select nextval('order_code_seq')", nativeQuery = true)
    long nextOrderNumber();
}
