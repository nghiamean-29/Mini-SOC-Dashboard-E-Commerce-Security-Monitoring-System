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
 * <p><strong>Implemented (Phase 2):</strong> 14 interfaces, one per table that has an entity
 * (join tables {@code user_roles}, {@code alert_events} and {@code incident_alerts} are
 * mapped as {@code @ManyToMany} and need no repository of their own). Two conventions worth
 * knowing when reading them: list methods return {@code Page} instead of {@code List} so
 * that paging is the default shape, and {@code @EntityGraph} is declared on the methods a
 * screen needs (cart page, order detail, alert evidence) so the required joins are visible
 * in the query instead of happening implicitly. Two native queries exist for things JPQL
 * cannot express: {@code nextval('order_code_seq')} / {@code nextval('incident_code_seq')}
 * for the human readable codes, and {@code metadata @> ...} for the JSONB search.</p>
 */
package com.yak.minisoc.repository;
