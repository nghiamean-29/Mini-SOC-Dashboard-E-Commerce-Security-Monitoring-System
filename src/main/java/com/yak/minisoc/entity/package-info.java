/**
 * <h2>JPA entities - the domain model mapped to PostgreSQL tables</h2>
 *
 * <p><strong>Layer:</strong> persistence / domain model.</p>
 * <p><strong>Responsibility:</strong> describe persistent state as Java objects and their
 * relationships (many-to-one, one-to-many, many-to-many).</p>
 * <p><strong>Belongs here:</strong> shop entities ({@code User}, {@code Product},
 * {@code Order}, ...) and, in the {@code security/} sub-package, SOC entities
 * ({@code SecurityEvent}, {@code SecurityAlert}, {@code SecurityIncident}, ...).</p>
 * <p><strong>Rules:</strong> entities are not services - they may protect their own invariants
 * but never coordinate other objects; they must not depend on anything above them.
 * Schema changes are made by Flyway migrations first, then in the entity (see
 * {@code src/main/resources/db/migration}).</p>
 * <p><strong>Implemented (Phase 2):</strong> three base classes decide which technical
 * columns an entity has - {@code BaseEntity} (id + created_at/updated_at, for {@code User},
 * {@code Role}, {@code Cart}, {@code CartItem}, {@code Category}, {@code IncidentNote}),
 * {@code VersionedEntity} (adds {@code @Version} for optimistic locking, for {@code Product},
 * {@code Order}, {@code SecurityAlert}, {@code SecurityIncident}) and {@code OccurredEntity}
 * (a business {@code occurred_at} instead of technical timestamps, for {@code LoginHistory},
 * {@code AuditLog}). Shop entities live in this package; SOC entities in
 * {@code com.yak.minisoc.entity.security}, together with the enums that mirror the CHECK
 * constraints of the migrations.</p>
 */
package com.yak.minisoc.entity;
