package com.yak.minisoc.entity;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Order lifecycle ({@code orders.status}).
 *
 * <p>This enum is also the <strong>state machine</strong> of an order: it knows which
 * transitions are legal. Putting that knowledge here - instead of scattering
 * {@code if (status == ...)} checks through {@code OrderService} - means:</p>
 * <ul>
 *   <li>there is exactly one source of truth for "what may happen next";</li>
 *   <li>the rule can be unit tested without any database or service;</li>
 *   <li>an illegal transition (for example {@code CANCELLED -> SHIPPED}) is rejected by
 *       one call, not by a chain of conditions somebody may forget to write.</li>
 * </ul>
 *
 * <p><strong>Collections used, and why</strong></p>
 * <ul>
 *   <li>{@link EnumMap} - a map keyed by an enum is implemented as an array indexed by
 *       {@code ordinal()}: no hashing, extremely fast, and it makes the "keyed by a fixed
 *       set of constants" intent obvious.</li>
 *   <li>{@link EnumSet} - the same idea for the value side, and it guarantees that no
 *       duplicate next-state can be listed.</li>
 *   <li>Both are wrapped so the outside world cannot modify them: a state machine that a
 *       caller can edit is not a state machine.</li>
 * </ul>
 *
 * <pre>
 *   PENDING ──▶ CONFIRMED ──▶ PROCESSING ──▶ SHIPPED ──▶ COMPLETED
 *      │            │
 *      └────────────┴────────▶ CANCELLED
 * </pre>
 *
 * <p>Shop rule behind the arrows: once an order has left the warehouse
 * ({@code PROCESSING}) it can no longer be cancelled - the goods are already moving, so
 * the customer must be handled as a return instead of a cancellation.</p>
 */
public enum OrderStatus {

    /** Created by the customer, waiting for the shop to accept it. */
    PENDING,

    /** Accepted by the shop. */
    CONFIRMED,

    /** Being prepared / packed; no longer cancellable. */
    PROCESSING,

    /** Handed to the carrier. */
    SHIPPED,

    /** Delivered; terminal state. */
    COMPLETED,

    /** Cancelled by the customer (only while PENDING/CONFIRMED) or by the shop; terminal state. */
    CANCELLED;

    private static final Map<OrderStatus, Set<OrderStatus>> ALLOWED_TRANSITIONS;

    static {
        Map<OrderStatus, Set<OrderStatus>> transitions = new EnumMap<>(OrderStatus.class);
        transitions.put(PENDING, EnumSet.of(CONFIRMED, CANCELLED));
        transitions.put(CONFIRMED, EnumSet.of(PROCESSING, CANCELLED));
        transitions.put(PROCESSING, EnumSet.of(SHIPPED));
        transitions.put(SHIPPED, EnumSet.of(COMPLETED));
        transitions.put(COMPLETED, EnumSet.noneOf(OrderStatus.class));
        transitions.put(CANCELLED, EnumSet.noneOf(OrderStatus.class));

        // Unmodifiable AND defensive: EnumMap/EnumSet are mutable, the outside must not be.
        Map<OrderStatus, Set<OrderStatus>> readOnly = new EnumMap<>(OrderStatus.class);
        transitions.forEach((status, next) -> readOnly.put(status, Collections.unmodifiableSet(next)));
        ALLOWED_TRANSITIONS = Collections.unmodifiableMap(readOnly);
    }

    /** May an order move from {@code this} state to {@code next}? */
    public boolean canTransitionTo(OrderStatus next) {
        if (next == null) {
            return false;
        }
        return ALLOWED_TRANSITIONS.get(this).contains(next);
    }

    /** The states an order may move to from here (read-only). */
    public Set<OrderStatus> allowedNextStatuses() {
        return ALLOWED_TRANSITIONS.get(this);
    }

    /** A customer may cancel while the shop has not started packing yet. */
    public boolean isCancellable() {
        return this == PENDING || this == CONFIRMED;
    }

    /** No further transition exists from a terminal state. */
    public boolean isTerminal() {
        return allowedNextStatuses().isEmpty();
    }
}
