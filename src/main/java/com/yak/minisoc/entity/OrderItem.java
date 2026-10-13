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
 * One line of an order ({@code order_items} table), created by
 * {@link Order#addItem(Product, int)}.
 *
 * <p><strong>This row is a historical document, not a pointer.</strong> {@code productName}
 * and {@code unitPrice} are copied at checkout time. If the product is renamed or its price
 * changes tomorrow - or the product is deleted and {@code product_id} becomes NULL - the
 * invoice still shows exactly what the customer bought and for how much. That is why the
 * snapshot columns are {@code NOT NULL} while {@code product_id} is optional.</p>
 *
 * <p>{@code lineTotal} is stored as well. It equals {@code unitPrice * quantity}, which
 * looks redundant - but it freezes the money of that line: if a rounding rule ever changes,
 * historical invoices keep their original numbers.</p>
 */
@Entity
@Table(name = "order_items")
public class OrderItem extends BaseEntity {

    private static final long serialVersionUID = 1L;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    /** Optional: becomes NULL if the product is hard-deleted; the snapshot stays readable. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id")
    private Product product;

    @Column(name = "product_name", nullable = false, length = 150)
    private String productName;

    @Column(name = "unit_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal unitPrice;

    @Column(name = "quantity", nullable = false)
    private int quantity;

    @Column(name = "line_total", nullable = false, precision = 12, scale = 2)
    private BigDecimal lineTotal;

    protected OrderItem() {
    }

    /** Aggregate-internal constructor; takes the snapshot from the product. */
    OrderItem(Order order, Product product, int quantity) {
        this.order = Objects.requireNonNull(order, "order");
        this.product = Objects.requireNonNull(product, "product");
        this.productName = product.getName();
        this.unitPrice = product.getPrice();
        changeQuantity(quantity);
    }

    public void changeQuantity(int newQuantity) {
        if (newQuantity <= 0) {
            throw new IllegalArgumentException(
                    "quantity must be positive (delete the line instead), was " + newQuantity);
        }
        this.quantity = newQuantity;
        // Keep the frozen money consistent with the quantity.
        this.lineTotal = unitPrice.multiply(BigDecimal.valueOf(newQuantity));
    }

    public Order getOrder() {
        return order;
    }

    public Product getProduct() {
        return product;
    }

    public String getProductName() {
        return productName;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public int getQuantity() {
        return quantity;
    }

    public BigDecimal getLineTotal() {
        return lineTotal;
    }
}
