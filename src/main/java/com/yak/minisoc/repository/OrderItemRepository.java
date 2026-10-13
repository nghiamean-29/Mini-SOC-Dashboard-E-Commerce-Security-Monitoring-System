package com.yak.minisoc.repository;

import com.yak.minisoc.entity.OrderItem;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Data access for order lines.
 *
 * <p>Lines belong to the {@code Order} aggregate, so they are normally written through the
 * parent. This interface exists for the questions that are about <em>lines</em>, not about
 * orders - best sellers, revenue per product - and those questions are answered with
 * aggregation queries, never by looping over orders in Java.</p>
 *
 * <p><strong>The snapshot columns make the report correct.</strong>
 * {@link #topSellingProducts(Instant, Instant, Pageable)} groups by the product name stored
 * on the line, not by a join to {@code products}: a report for last month must show what
 * customers actually bought that month, even if the product has been renamed or
 * discontinued since. This is the payoff of the "order owns its numbers" rule
 * (migration V4).</p>
 */
public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {

    List<OrderItem> findByOrderId(Long orderId);

    List<OrderItem> findByProductId(Long productId);

    /** Best sellers in a period; {@code Pageable} limits the list (top 10 for the report). */
    @Query("""
            select oi.productName as productName,
                   sum(oi.quantity) as unitsSold,
                   sum(oi.lineTotal) as revenue
            from OrderItem oi
            where oi.order.createdAt >= :from and oi.order.createdAt < :to
              and oi.order.status <> com.yak.minisoc.entity.OrderStatus.CANCELLED
            group by oi.productName
            order by sum(oi.quantity) desc
            """)
    List<ProductSales> topSellingProducts(@Param("from") Instant from, @Param("to") Instant to,
                                          Pageable pageable);

    /** One row of {@link #topSellingProducts(Instant, Instant, Pageable)}. */
    interface ProductSales {
        String getProductName();

        long getUnitsSold();

        java.math.BigDecimal getRevenue();
    }

    long countByProductId(Long productId);
}
