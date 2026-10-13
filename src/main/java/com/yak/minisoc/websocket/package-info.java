/**
 * <h2>Realtime dashboard updates over WebSocket/STOMP</h2>
 *
 * <p><strong>Layer:</strong> presentation/infrastructure (transport towards the browser).</p>
 * <p><strong>Responsibility:</strong> push new events, alerts, incidents and statistics to
 * connected dashboards the moment they are created, so the analyst never has to press F5.</p>
 * <p><strong>Belongs here:</strong> the {@code DashboardEventPublisher} abstraction and the
 * STOMP implementation that sends to {@code /topic/alerts} and friends.</p>
 * <p><strong>Design note:</strong> publishers depend on an interface, so the alert logic can
 * be tested with a fake publisher and no WebSocket server.</p>
 * <p><strong>Planned classes:</strong> Phase 10.</p>
 */
package com.yak.minisoc.websocket;
