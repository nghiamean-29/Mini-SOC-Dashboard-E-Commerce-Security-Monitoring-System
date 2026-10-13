/**
 * <h2>Security alert lifecycle (Factory and Observer patterns)</h2>
 *
 * <p><strong>Layer:</strong> domain logic of the SOC.</p>
 * <p><strong>Responsibility:</strong> create alerts from rule results, store them, apply the
 * duplicate-suppression cooldown, and announce that a new alert exists.</p>
 * <p><strong>Belongs here:</strong> {@code AlertFactory}, {@code AlertService},
 * {@code AlertCreatedEvent} (a Spring application event, i.e. the Observer pattern).</p>
 * <p><strong>Observer in one sentence:</strong> the alert service does not know who cares
 * about alerts - it publishes an event; the WebSocket publisher and the incident
 * correlator subscribe to it. Publishers and subscribers stay independent.</p>
 * <p><strong>Planned classes:</strong> Phase 7.</p>
 */
package com.yak.minisoc.alert;
