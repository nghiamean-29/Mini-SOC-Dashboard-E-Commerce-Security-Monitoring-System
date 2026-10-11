package com.yak.minisoc.entity;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.Version;

/**
 * Adds <strong>optimistic locking</strong> to an entity.
 *
 * <p><strong>The problem it solves.</strong> Two users act on the same row at the same
 * time. Classic example in this project: the last mechanical keyboard is in stock, two
 * customers press "Place order" within the same millisecond. Without protection, both
 * transactions read {@code stock = 1}, both write {@code stock = 0}, and the shop sells
 * two items for one unit - or, with {@code stock = stock - 1} style updates, the stock
 * becomes negative (which the CHECK constraint in V2 would reject, but "database error"
 * is a poor user experience compared to "please try again").</p>
 *
 * <p><strong>How it works.</strong> The {@code version} column starts at 0. On every
 * UPDATE, Hibernate adds {@code ... WHERE id = ? AND version = ?} and increments the
 * value. If another transaction already changed the row, zero rows are updated and
 * Hibernate throws {@link jakarta.persistence.OptimisticLockException} - the second
 * transaction fails instead of overwriting data it never saw.</p>
 *
 * <p><strong>Why "optimistic"?</strong> It assumes conflicts are rare and pays nothing
 * until one happens, unlike a pessimistic lock ({@code SELECT ... FOR UPDATE}) which makes
 * every reader wait. For an e-commerce cart or a SOC incident that is the right trade-off:
 * contention is low, but a lost update would be a real bug.</p>
 *
 * <p><strong>Which entities need it:</strong> only those that many people can modify
 * concurrently AND where a lost update corrupts business state. In this project:
 * {@code Product} (stock), {@code Order} (status), {@code SecurityAlert} and
 * {@code SecurityIncident} (two analysts working on the same incident).</p>
 *
 * <p><strong>The price</strong> (stated honestly, because every choice has one): a
 * conflicting write is <em>rejected</em>, so the calling code must handle
 * {@code OptimisticLockException} and decide what to do - usually "reload and retry" or
 * "tell the user the data changed". A service that ignores this exception turns a
 * detected conflict into a failed request for the user. Phase 4 handles it for
 * {@code OrderService}.</p>
 */
@MappedSuperclass
public abstract class VersionedEntity extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /**
     * Managed <strong>exclusively by Hibernate</strong>: it reads the old value before an
     * update and writes the incremented value afterwards. There is on purpose no setter,
     * because application code must never "invent" a version number.
     */
    @Version
    @Column(name = "version", nullable = false)
    private long version;

    public long getVersion() {
        return version;
    }
}
