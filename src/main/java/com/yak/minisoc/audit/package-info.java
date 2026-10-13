/**
 * <h2>Security event collection and audit trail</h2>
 *
 * <p><strong>Layer:</strong> cross-cutting (used by web filters, Spring Security listeners
 * and services).</p>
 * <p><strong>Responsibility:</strong> turn something happened into a stored
 * {@code SecurityEvent} or audit record, asynchronously, without slowing down the user
 * request that caused it.</p>
 * <p><strong>Belongs here:</strong> the {@code SecurityEventCollector} interface and its
 * asynchronous implementation, the HTTP filter that records requests, the listener for
 * Spring Security login/logout events, and the audit/login-history services.</p>
 * <p><strong>Three different records, three different purposes:</strong>
 * {@code security_events} (raw facts used by detection), {@code audit_logs}
 * (who changed what, for accountability) and {@code login_history}
 * (authentication history). Mixing them into one table always ends in a table that
 * serves nobody well.</p>
 * <p><strong>Planned classes:</strong> Phase 5.</p>
 */
package com.yak.minisoc.audit;
