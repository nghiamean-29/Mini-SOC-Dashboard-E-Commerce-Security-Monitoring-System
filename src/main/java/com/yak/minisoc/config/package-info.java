/**
 * <h2>Spring configuration (infrastructure wiring)</h2>
 *
 * <p><strong>Layer:</strong> infrastructure / configuration (not business logic).</p>
 * <p><strong>Responsibility:</strong> define beans and framework settings in Java instead of XML:
 * security filter chain, WebSocket/STOMP broker, thread pools, MVC interceptors.</p>
 * <p><strong>Belongs here:</strong> {@code SecurityConfig}, {@code WebSocketConfig},
 * {@code AsyncConfig}, {@code WebMvcConfig}.</p>
 * <p><strong>Does NOT belong here:</strong> business rules (they live in
 * {@code com.yak.minisoc.service}), and no {@code @Value}-style configuration of
 * individual rule thresholds (those are bound through {@code @ConfigurationProperties}).</p>
 * <p><strong>Planned classes:</strong> Phase 3 (security), Phase 5 (async/thread pools),
 * Phase 10 (websocket).</p>
 */
package com.yak.minisoc.config;
