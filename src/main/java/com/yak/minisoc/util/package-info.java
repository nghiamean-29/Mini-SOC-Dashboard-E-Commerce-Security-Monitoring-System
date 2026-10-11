/**
 * <h2>Small helpers with no framework dependency</h2>
 *
 * <p><strong>Layer:</strong> cross-cutting utility.</p>
 * <p><strong>Responsibility:</strong> pure functions with no side effects and no Spring
 * dependency: IP normalisation/masking, time-zone conversion, log sanitising, page mapping.</p>
 * <p><strong>Rule:</strong> because these classes depend on nothing, they are the easiest
 * code in the project to unit test - keep them that way (no {@code @Autowired} here).</p>
 * <p><strong>Planned classes:</strong> Phase 5 ({@code IpAddressUtil}, {@code LogSanitizer}).</p>
 */
package com.yak.minisoc.util;
