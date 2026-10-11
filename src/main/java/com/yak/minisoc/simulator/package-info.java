/**
 * <h2>Attack Simulator - safe, in-process generation of attack-shaped events</h2>
 *
 * <p><strong>Layer:</strong> testing/demo support (a real feature, but not part of the
 * security-critical path).</p>
 * <p><strong>Responsibility:</strong> produce the <em>same</em> security events a real attack
 * would produce, so the whole pipeline (collector -&gt; detection -&gt; alert -&gt; incident
 * -&gt; dashboard) can be demonstrated and tested on demand.</p>
 * <p><strong>Ethics and scope:</strong> simulators only insert data inside this application.
 * They never send a single packet to an external system, and they never target a host
 * that is not this application's own database.</p>
 * <p><strong>Design note:</strong> a simulator calls the same
 * {@code SecurityEventCollector} the real code uses. Writing events straight into the
 * database would test nothing and would let the pipeline rot.</p>
 * <p><strong>Planned classes:</strong> Phase 11.</p>
 */
package com.yak.minisoc.simulator;
