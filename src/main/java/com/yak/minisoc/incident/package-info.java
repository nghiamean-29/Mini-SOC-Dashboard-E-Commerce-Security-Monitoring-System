/**
 * <h2>Incident management - correlation and investigation workflow</h2>
 *
 * <p><strong>Layer:</strong> domain logic of the SOC.</p>
 * <p><strong>Responsibility:</strong> group related alerts into one incident
 * (correlation), then support the analyst workflow: assign, add investigation notes,
 * change status, resolve with a conclusion.</p>
 * <p><strong>Deduplication vs correlation - do not mix them up:</strong>
 * deduplication collapses repeated identical alerts into one alert; correlation groups
 * <em>different</em> alerts that share a source into one incident.</p>
 * <p><strong>Belongs here:</strong> {@code IncidentCorrelationService},
 * {@code IncidentService}.</p>
 * <p><strong>Planned classes:</strong> Phase 8.</p>
 */
package com.yak.minisoc.incident;
