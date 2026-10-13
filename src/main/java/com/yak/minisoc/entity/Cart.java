package com.yak.minisoc.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The shopping cart of one customer ({@code carts} table; at most one per user - the
 * {@code uk_carts_user} unique constraint guarantees it, so a race between two requests
 * cannot create a second cart).
 *
 * <h3>Aggregate boundary</h3>
 *
 * <p>A cart owns its items: they are created, changed and deleted only together with the
 * cart. That is expressed in two ways:</p>
 * <ul>
 *   <li>{@code cascade = ALL, orphanRemoval = true} - saving the cart saves its items,
 *       and removing an item from the list deletes its row;</li>
 *   <li>{@link CartItem}'s constructor is package-private, so no code outside this package
 *       can create a detached item. The only way to put something into a cart is
 *       {@link #addItem(Product, int)}, which keeps the two sides of the relationship and
 *       the price snapshot consistent.</li>
 * </ul>
 *
 * <p><strong>Why the price is copied into the item</strong> ({@code cart_items.unit_price}):
 * the customer must see the price they added, not whatever the product costs after an
 * admin changed it. Phase 4 re-checks the snapshot at checkout and warns when it differs -
 * a real shop behaviour and a lesson in not trusting mutable prices.</p>
 */
@Entity
@Table(name = "carts")
public class Cart extends BaseEntity {

    private static final long serialVersionUID = 1L;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /**
     * {@code List} (not {@code Set}): the customer sees the items in the order they were
     * added, and PostgreSQL keeps the insertion order for the {@code cart_id} index.
     * Duplicates are impossible anyway thanks to {@code uk_cart_items_cart_product} and
     * the upsert logic in {@link #addItem(Product, int)}.
     */
    @OneToMany(mappedBy = "cart", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<CartItem> items = new ArrayList<>();

    protected Cart() {
    }

    public static Cart forUser(User user) {
        Cart cart = new Cart();
        cart.user = Objects.requireNonNull(user, "user");
        return cart;
    }

    // ------------------------------------------------------------------ behaviour

    /**
     * Adds a product, or increases the quantity when it is already in the cart.
     *
     * <p>Upsert semantics mirror the database rule {@code UNIQUE (cart_id, product_id)}:
     * "one line per product" is the invariant, and the code expresses it directly instead
     * of relying on the caller to search first.</p>
     *
     * @return the affected line, so the caller can show the new quantity
     */
    public CartItem addItem(Product product, int quantity) {
        Objects.requireNonNull(product, "product");
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity must be positive, was " + quantity);
        }

        Optional<CartItem> existing = findItem(product);
        if (existing.isPresent()) {
            CartItem item = existing.get();
            item.changeQuantity(item.getQuantity() + quantity);
            return item;
        }

        CartItem item = new CartItem(this, product, quantity, product.getPrice());
        items.add(item);
        return item;
    }

    /** Sets an absolute quantity for a product already in the cart. */
    public CartItem changeQuantity(Product product, int quantity) {
        CartItem item = findItem(product).orElseThrow(() -> new IllegalStateException(
                "product " + product.getId() + " is not in this cart"));
        item.changeQuantity(quantity);
        return item;
    }

    /** Removes a product from the cart. {@code orphanRemoval} deletes the row. */
    public void removeItem(Product product) {
        findItem(product).ifPresent(items::remove);
    }

    /** Empties the cart (used after a successful checkout). */
    public void clear() {
        items.clear();
    }

    public Optional<CartItem> findItem(Product product) {
        return items.stream()
                .filter(item -> item.getProduct().getId() != null
                        && item.getProduct().getId().equals(product.getId()))
                .findFirst();
    }

    /** Current value of the cart, based on the price snapshots the customer saw. */
    public BigDecimal totalPrice() {
        return items.stream()
                .map(CartItem::lineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** Total number of units in the cart (not the number of lines). */
    public int totalQuantity() {
        return items.stream().mapToInt(CartItem::getQuantity).sum();
    }

    public boolean isEmpty() {
        return items.isEmpty();
    }

    // -------------------------------------------------------------- getters/setters

    public User getUser() {
        return user;
    }

    /** Read-only view: mutations must go through the methods above. */
    public List<CartItem> getItems() {
        return Collections.unmodifiableList(items);
    }
}
