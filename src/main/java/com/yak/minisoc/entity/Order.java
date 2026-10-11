package com.yak.minisoc.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * A customer order ({@code orders} table).
 *
 * <p>Extends {@link VersionedEntity}: an order is modified by the customer (cancel) and by
 * administrators (status changes, Phase 4), so a lost update would be a real problem.</p>
 *
 * <h3>Three rules this class protects</h3>
 * <ol>
 *   <li><strong>The state machine.</strong> {@link #changeStatus(OrderStatus)} asks
 *       {@link OrderStatus#canTransitionTo(OrderStatus)} first. An illegal jump such as
 *       {@code CANCELLED -> SHIPPED} fails immediately instead of producing a
 *       nonsensical history.</li>
 *   <li><strong>Prices are snapshots.</strong> Lines are created from the product's
 *       current price and copied into {@code order_items}; the order total is the sum of
 *       the line totals. Later price changes cannot rewrite an existing order.</li>
 *   <li><strong>Cancellation is recorded, not implied.</strong> {@link #cancel(Instant, String)}
 *       sets the status <em>and</em> the timestamp <em>and</em> the reason, which is exactly
 *       what the database constraint {@code ck_orders_cancelled} demands.</li>
 * </ol>
 *
 * <p><strong>Where does {@code orderCode} come from?</strong> From a PostgreSQL sequence
 * ({@code order_code_seq}, migration V4) formatted as {@code ORD-20260921-000042} by
 * Phase 4's {@code OrderService}. Reason: a "nice" running number cannot be produced by
 * {@code COUNT(*) + 1} - two simultaneous orders would read the same count and generate
 * the same code, and the UNIQUE constraint would reject one of them. A sequence never
 * returns the same value twice, so the code is collision-free before the insert, and the
 * human readable part stays.</p>
 *
 * <p>{@link #assignOrderCode(String)} may be called once; changing a code afterwards would
 * break URLs and printed receipts, so the entity forbids it.</p>
 */
@Entity
@Table(name = "orders")
public class Order extends VersionedEntity {

    private static final long serialVersionUID = 1L;

    @Column(name = "order_code", nullable = false, length = 30)
    private String orderCode;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private OrderStatus status = OrderStatus.PENDING;

    @Column(name = "total_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalAmount = BigDecimal.ZERO;

    @Column(name = "shipping_name", length = 120)
    private String shippingName;

    @Column(name = "shipping_phone", length = 20)
    private String shippingPhone;

    @Column(name = "shipping_address", length = 255)
    private String shippingAddress;

    @Column(name = "note", length = 500)
    private String note;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "cancel_reason", length = 255)
    private String cancelReason;

    /** Owned children, same aggregate pattern as {@link Cart#getItems()}. */
    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<OrderItem> items = new ArrayList<>();

    protected Order() {
    }

    public static Order forUser(User user) {
        Order order = new Order();
        order.user = Objects.requireNonNull(user, "user");
        order.status = OrderStatus.PENDING;
        order.totalAmount = BigDecimal.ZERO;
        return order;
    }

    // ------------------------------------------------------------------ behaviour

    /**
     * Assigns the business code exactly once.
     *
     * @throws IllegalStateException when a code was already assigned
     */
    public void assignOrderCode(String code) {
        if (this.orderCode != null) {
            throw new IllegalStateException("order " + orderCode + " already has a code");
        }
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("order code must not be blank");
        }
        this.orderCode = code;
    }

    /** Adds a line, snapshotting the product name and price, and updates the total. */
    public OrderItem addItem(Product product, int quantity) {
        Objects.requireNonNull(product, "product");
        OrderItem item = new OrderItem(this, product, quantity);
        items.add(item);
        recalculateTotal();
        return item;
    }

    /**
     * Moves the order to {@code next}, rejecting illegal transitions.
     *
     * <p>Phase 4 wraps the {@link IllegalStateException} into a domain
     * {@code InvalidOrderStateException} so that the HTTP layer can answer 409 Conflict
     * with a readable message. The entity deliberately throws a plain JDK exception: it
     * has no dependency on the application's exception package, which keeps the domain
     * model independent of the web layer.</p>
     */
    public void changeStatus(OrderStatus next) {
        Objects.requireNonNull(next, "next");
        if (!status.canTransitionTo(next)) {
            throw new IllegalStateException(
                    "illegal order transition " + status + " -> " + next
                            + " (allowed: " + status.allowedNextStatuses() + ")");
        }
        this.status = next;
    }

    /** Customer or admin cancellation, allowed while the shop has not started packing. */
    public void cancel(Instant at, String reason) {
        if (!status.isCancellable()) {
            throw new IllegalStateException("order in state " + status + " can no longer be cancelled");
        }
        this.status = OrderStatus.CANCELLED;
        this.cancelledAt = Objects.requireNonNull(at, "at");
        this.cancelReason = reason;
    }

    public boolean isCancellable() {
        return status.isCancellable();
    }

    public boolean isTerminal() {
        return status.isTerminal();
    }

    /** Number of units ordered (not the number of lines). */
    public int totalQuantity() {
        return items.stream().mapToInt(OrderItem::getQuantity).sum();
    }

    /** Recomputes the total from the lines; called whenever a line is added. */
    private void recalculateTotal() {
        this.totalAmount = items.stream()
                .map(OrderItem::getLineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    // -------------------------------------------------------------- getters/setters

    public String getOrderCode() {
        return orderCode;
    }

    public User getUser() {
        return user;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    /** Read-only: the total is derived from the lines, never set from outside. */
    public List<OrderItem> getItems() {
        return Collections.unmodifiableList(items);
    }

    public String getShippingName() {
        return shippingName;
    }

    public void setShippingName(String shippingName) {
        this.shippingName = shippingName;
    }

    public String getShippingPhone() {
        return shippingPhone;
    }

    public void setShippingPhone(String shippingPhone) {
        this.shippingPhone = shippingPhone;
    }

    public String getShippingAddress() {
        return shippingAddress;
    }

    public void setShippingAddress(String shippingAddress) {
        this.shippingAddress = shippingAddress;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public Instant getCancelledAt() {
        return cancelledAt;
    }

    public String getCancelReason() {
        return cancelReason;
    }
}
