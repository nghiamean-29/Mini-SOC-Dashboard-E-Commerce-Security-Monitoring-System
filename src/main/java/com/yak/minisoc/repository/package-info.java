/**
 * <h2>Data access layer (Spring Data JPA repositories)</h2>
 *
 * <p><strong>Layer:</strong> data access (repository).</p>
 * <p><strong>Responsibility:</strong> query the database and return objects. Nothing else.</p>
 * <p><strong>Belongs here:</strong> interfaces extending {@code JpaRepository} or
 * {@code CrudRepository}, derived query methods ({@code findBySourceIpAndDetectedAtAfter}),
 * and JPQL/native queries for the dashboard aggregations.</p>
 * <p><strong>Does NOT belong here:</strong> {@code if}/{@code for} business decisions,
 * transactions, or calls back into services (that would create a dependency cycle).</p>
 * <p><strong>Planned classes:</strong> Phase 2 (shop) and Phase 5-8 (security domain).</p>
 */
package com.yak.minisoc.repository;
