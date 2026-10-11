/**
 * <h2>Entity &lt;-&gt; DTO mapping</h2>
 *
 * <p><strong>Layer:</strong> cross-cutting conversion helper.</p>
 * <p><strong>Responsibility:</strong> convert an entity into the DTO the view needs (and back),
 * in plain Java that you can read, debug and unit test.</p>
 * <p><strong>Why plain methods instead of a mapping library:</strong> a few dozen explicit
 * lines are easier to follow for somebody learning JPA than generated code that hides when
 * a lazy association is touched. If the mapping grows large, MapStruct can replace these
 * classes without changing the rest of the application.</p>
 * <p><strong>Planned classes:</strong> Phase 4 (product/order), Phase 7-9 (alert/incident).</p>
 */
package com.yak.minisoc.mapper;
