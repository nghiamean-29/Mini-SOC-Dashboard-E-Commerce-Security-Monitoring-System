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
 * <p><strong>Planned classes:</strong> Phase 2.</p>
 */
package com.yak.minisoc.entity;
