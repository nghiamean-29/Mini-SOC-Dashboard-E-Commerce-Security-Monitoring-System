package com.yak.minisoc.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.Objects;

/**
 * A product for sale ({@code products} table).
 *
 * <p>Extends {@link VersionedEntity} because of the stock: two customers buying the last
 * item at the same moment is the textbook lost-update problem (see that class for the
 * full explanation).</p>
 *
 * <p><strong>Money is {@link BigDecimal}, never {@code double}.</strong> {@code double}
 * cannot represent 0.10 exactly, so totals computed with it drift by cents - unacceptable
 * in a shop and a classic interview question. The column is {@code NUMERIC(12,2)} and
 * Hibernate keeps the scale, so the database and Java agree on two decimal places.</p>
 *
 * <p><strong>The stock rule lives in the entity.</strong> {@link #hasSufficientStock(int)}
 * and {@link #decreaseStock(int)} keep the invariant "stock is never negative" close to the
 * data. The database enforces it as well ({@code ck_products_stock}), the service checks it
 * to produce a friendly message - three layers, three different reasons, none of them
 * redundant.</p>
 */
@Entity
@Table(name = "products")
public class Product extends VersionedEntity {

    private static final long serialVersionUID = 1L;

    /**
     * LAZY: a product list must not drag every category into memory. When the view needs
     * the category name, Phase 4 fetches the page with an entity graph or a projection.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private Category category;

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Column(name = "slug", nullable = false, length = 170)
    private String slug;

    @Column(name = "description")
    private String description;

    @Column(name = "price", nullable = false, precision = 12, scale = 2)
    private BigDecimal price;

    @Column(name = "stock", nullable = false)
    private int stock;

    @Column(name = "image_url", length = 255)
    private String imageUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ProductStatus status = ProductStatus.ACTIVE;

    protected Product() {
    }

    public static Product create(String name, String slug, BigDecimal price, int initialStock,
                                 Category category) {
        Product product = new Product();
        product.name = Objects.requireNonNull(name, "name");
        product.slug = Objects.requireNonNull(slug, "slug");
        product.changePrice(price);
        product.setStock(initialStock);
        product.category = category;
        product.status = ProductStatus.ACTIVE;
        return product;
    }

    // ------------------------------------------------------------------ behaviour

    /** Can {@code quantity} units be sold right now? (status + stock) */
    public boolean hasSufficientStock(int quantity) {
        return quantity > 0 && status.isSellable() && stock >= quantity;
    }

    /** Removes units from stock; throws when the caller did not check first. */
    public void decreaseStock(int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity must be positive, was " + quantity);
        }
        if (stock < quantity) {
            throw new IllegalStateException(
                    "insufficient stock for product " + getId() + ": available " + stock + ", requested " + quantity);
        }
        this.stock -= quantity;
    }

    /** Puts units back (order cancelled, manual correction). */
    public void increaseStock(int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity must be positive, was " + quantity);
        }
        this.stock += quantity;
    }

    /** Absolute stock correction by an administrator (inventory count). */
    public void setStock(int stock) {
        if (stock < 0) {
            throw new IllegalArgumentException("stock must not be negative, was " + stock);
        }
        this.stock = stock;
    }

    public void changePrice(BigDecimal newPrice) {
        Objects.requireNonNull(newPrice, "newPrice");
        if (newPrice.signum() < 0) {
            throw new IllegalArgumentException("price must not be negative, was " + newPrice);
        }
        this.price = newPrice;
    }

    public void discontinue() {
        this.status = ProductStatus.DISCONTINUED;
    }

    public void deactivate() {
        this.status = ProductStatus.INACTIVE;
    }

    public void activate() {
        this.status = ProductStatus.ACTIVE;
    }

    public boolean isSellable() {
        return status.isSellable();
    }

    // -------------------------------------------------------------- getters/setters

    public Category getCategory() {
        return category;
    }

    public void assignCategory(Category category) {
        this.category = category;
    }

    public String getName() {
        return name;
    }

    public void rename(String newName, String newSlug) {
        this.name = Objects.requireNonNull(newName, "newName");
        this.slug = Objects.requireNonNull(newSlug, "newSlug");
    }

    public String getSlug() {
        return slug;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public int getStock() {
        return stock;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public ProductStatus getStatus() {
        return status;
    }
}
