package com.yak.minisoc.entity;

import jakarta.persistence.Column;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import java.io.Serializable;
import java.time.Instant;
import org.hibernate.Hibernate;

/**
 * Superclass for <strong>append-only records of something that happened</strong>:
 * {@code login_history} and {@code audit_logs}.
 *
 * <p>These tables do not fit {@link BaseEntity}, and that is the interesting part of this
 * class. They have:</p>
 * <ul>
 *   <li>no {@code created_at} / {@code updated_at}, because nobody ever asks "when was
 *       this row written?" - they ask "when did it happen?";</li>
 *   <li>a single business timestamp, {@code occurred_at}, which the
 *       <strong>application</strong> sets (not Hibernate): the moment captured is the
 *       moment of the login attempt or of the administrative action, and tests must be
 *       able to pin it to a fixed value;</li>
 *   <li>no update path at all. A row is written once and read forever; an audit trail
 *       that can be edited is not an audit trail.</li>
 * </ul>
 *
 * <p>So the shape of the data is genuinely different, and the code says so instead of
 * forcing these tables into a superclass that would drag two meaningless columns along.</p>
 */
@MappedSuperclass
public abstract class OccurredEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, updatable = false)
    private Long id;

    /**
     * When the recorded action happened (UTC).
     *
     * <p>On purpose <strong>not</strong> annotated with {@code @CreationTimestamp}:
     * Hibernate would overwrite whatever the application sets, and the application is the
     * only component that knows the real time of the event (it may even be a timestamp
     * taken from a simulated attack in Phase 11, or a fixed value in a unit test).
     * The database column is {@code NOT NULL} without a default, so forgetting to set it
     * fails immediately at flush time instead of silently storing "now".</p>
     */
    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    public Long getId() {
        return id;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public void setOccurredAt(Instant occurredAt) {
        this.occurredAt = occurredAt;
    }

    public boolean isNew() {
        return id == null;
    }

    /** Same identity contract as {@link BaseEntity} - see the explanation there. */
    @Override
    public final boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (other == null || Hibernate.getClass(this) != Hibernate.getClass(other)) {
            return false;
        }
        OccurredEntity that = (OccurredEntity) other;
        return id != null && id.equals(that.id);
    }

    @Override
    public final int hashCode() {
        return getClass().hashCode();
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "{id=" + id + ", occurredAt=" + occurredAt + "}";
    }
}
