/**
 * <h2>Spring Security components (authentication and authorization)</h2>
 *
 * <p><strong>Layer:</strong> cross-cutting security infrastructure.</p>
 * <p><strong>Responsibility:</strong> answer two questions: <em>who is this user?</em>
 * (authentication) and <em>is this user allowed to do that?</em> (authorization).</p>
 * <p><strong>Belongs here:</strong> {@code DatabaseUserDetailsService},
 * role/authority builders, authentication success/failure handlers, ownership checks.</p>
 * <p><strong>Careful - two different meanings of the word security:</strong>
 * this package protects <em>our own</em> application with Spring Security.
 * {@code com.yak.minisoc.detection} does something completely different: it detects
 * attacks against the shop. Keeping them apart avoids a very confusing package.</p>
 * <p><strong>Planned classes:</strong> Phase 3.</p>
 */
package com.yak.minisoc.security;
