package com.yak.minisoc.repository;

import com.yak.minisoc.entity.CartItem;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Data access for cart lines.
 *
 * <p>Most of the time a line is reached through its {@link Cart} (that is the aggregate
 * boundary). This repository exists for two reasons: the checkout step needs to read lines
 * independently of the parent entity, and the maintenance queries below are far cheaper as
 * SQL than as a Java loop over carts.</p>
 */
public interface CartItemRepository extends JpaRepository<CartItem, Long> {

    List<CartItem> findByCartId(Long cartId);

    Optional<CartItem> findByCartIdAndProductId(Long cartId, Long productId);

    /**
     * How many customers currently want this product - an operational question ("is it in
     * demand?") and a building block of the request-flood analysis in Phase 6.</p>
     */
    @Query("select coalesce(sum(ci.quantity), 0) from CartItem ci where ci.product.id = :productId")
    long sumQuantityByProductId(@Param("productId") Long productId);

    /** Remove every line of a cart at once (checkout); returns the number of rows deleted. */
    long deleteByCartId(Long cartId);
}
