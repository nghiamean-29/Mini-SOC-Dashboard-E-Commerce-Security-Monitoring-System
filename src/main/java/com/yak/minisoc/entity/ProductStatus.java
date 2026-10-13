package com.yak.minisoc.entity;

/**
 * Lifecycle of a product ({@code products.status}).
 *
 * <p>Publishing state is an enum rather than a boolean because the three situations mean
 * different things to a customer and to an administrator:</p>
 * <ul>
 *   <li>{@link #ACTIVE} - visible and buyable.</li>
 *   <li>{@link #INACTIVE} - hidden from the shop, but the row and its history stay
 *       intact. This is what an admin normally does instead of deleting a product,
 *       because {@code order_items} reference products.</li>
 *   <li>{@link #DISCONTINUED} - permanently gone from the catalogue; still referenced by
 *       old orders.</li>
 * </ul>
 */
public enum ProductStatus {

    ACTIVE,
    INACTIVE,
    DISCONTINUED;

    /** Only ACTIVE products can be added to a cart or ordered. */
    public boolean isSellable() {
        return this == ACTIVE;
    }
}
