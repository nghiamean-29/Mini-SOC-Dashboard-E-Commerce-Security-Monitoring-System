package com.yak.minisoc.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.Objects;

/**
 * One line of a shopping cart ({@code cart_items} table).
 *
 * <p><strong>The constructor is package-private on purpose.</strong> An item has no meaning
 * without its cart, so only {@link Cart} (same package) may create one. This is the
 * aggregate boundary expressed in Java: a compile error is a better teacher than a
 * convention written in a wiki.</p>
 *
 * <p>There is no public setter for {@code quantity} either: changing it must recompute the
 * line total and must respect the "positive quantity" rule, so the only door is
 * {@link #changeQuantity(int)}.</p>
 */
@Entity
@Table(name = "cart_items")
public class CartItem extends BaseEntity {

    private static final long serialVersionUID = 1L;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cart_id", nullable = false)
    private Cart cart;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "quantity", nullable = false)
    private int quantity;

    /** Price snapshot taken when the item was added (see {@link Cart}). */
    @Column(name = "unit_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal unitPrice;

    /** Required by JPA. */
    protected CartItem() {
    }

    /** Aggregate-internal constructor; use {@link Cart#addItem(Product, int)}. */
    CartItem(Cart cart, Product product, int quantity, BigDecimal unitPrice) {
        this.cart = Objects.requireNonNull(cart, "cart");
        this.product = Objects.requireNonNull(product, "product");
        this.unitPrice = Objects.requireNonNull(unitPrice, "unitPrice");
        changeQuantity(quantity);
    }

    public void changeQuantity(int newQuantity) {
        if (newQuantity <= 0) {
            throw new IllegalArgumentException(
                    "quantity must be positive (use removeItem to delete the line), was " + newQuantity);
        }
        this.quantity = newQuantity;
    }

    /** The value of this line at the snapshot price. */
    public BigDecimal lineTotal() {
        return unitPrice.multiply(BigDecimal.valueOf(quantity));
    }

    public Cart getCart() {
        return cart;
    }

    public Product getProduct() {
        return product;
    }

    public int getQuantity() {
        return quantity;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }
}
