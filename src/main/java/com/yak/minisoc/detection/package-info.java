/**
 * <h2>Detection Engine - turning security events into alerts (Strategy pattern)</h2>
 *
 * <p><strong>Layer:</strong> domain logic of the SOC (business logic, not infrastructure).</p>
 * <p><strong>Responsibility:</strong> decide whether a series of {@code SecurityEvent}s is
 * suspicious. This is the most important package of the project.</p>
 * <p><strong>Belongs here:</strong> the {@code SecurityRule} interface and its five
 * implementations (BruteForce, UnauthorizedAccess, RequestFlood, SqlInjection,
 * SuspiciousAccount), the {@code DetectionEngine} that runs them, and the thread-safe
 * sliding-window state that the counting rules need.</p>
 * <p><strong>Why Strategy pattern:</strong> the engine iterates over
 * {@code List<SecurityRule>} injected by Spring. Adding a new rule means adding one
 * class, not editing the engine (Open/Closed Principle).</p>
 * <p><strong>Planned classes:</strong> Phase 6.</p>
 */
package com.yak.minisoc.detection;
