/**
 * <h2>Presentation layer (Spring MVC controllers)</h2>
 *
 * <p><strong>Layer:</strong> presentation (web). The first and last stop of every HTTP request.</p>
 * <p><strong>Responsibility:</strong> receive the request, bind and validate input,
 * delegate to a service, choose what the user sees (Thymeleaf view or JSON).</p>
 * <p><strong>Belongs here:</strong> MVC controllers for the shop and for the SOC dashboard,
 * REST controllers under the {@code api/} sub-package.</p>
 * <p><strong>Does NOT belong here:</strong> business logic and repository calls.
 * A controller method should stay short enough to read in one screen; anything more
 * belongs in a service, where it can be unit tested without a web server.</p>
 * <p><strong>Planned classes:</strong> Phase 3 (auth), Phase 4 (shop), Phase 7-9 (SOC).</p>
 */
package com.yak.minisoc.controller;
