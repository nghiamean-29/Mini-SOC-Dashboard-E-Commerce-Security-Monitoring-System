package com.yak.minisoc.repository;

import com.yak.minisoc.entity.Cart;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Data access for shopping carts.
 *
 * <p>A cart has no "list all" method: carts are only ever read through their owner or
 * their id. The interface shape follows from the access rules of the application - a
 * repository that offers {@code findAll()} for a private object is a bug waiting for a
 * careless call site.</p>
 */
public interface CartRepository extends JpaRepository<Cart, Long> {

    /** The one cart of a user ({@code uk_carts_user} guarantees there is at most one). */
    Optional<Cart> findByUserId(Long userId);

    /**
     * The cart with its lines <em>and</em> each line's product, for the cart page.
     *
     * <p>Two levels are fetched ({@code items} then {@code items.product}) because the page
     * shows the product name and price for every line. Without the entity graph, rendering
     * a cart of ten lines would run eleven queries - the classic N+1 problem; with it,
     * Hibernate joins them into one.</p>
     */
    @EntityGraph(attributePaths = {"items", "items.product"})
    Optional<Cart> findWithItemsByUserId(Long userId);

    boolean existsByUserId(Long userId);
}
