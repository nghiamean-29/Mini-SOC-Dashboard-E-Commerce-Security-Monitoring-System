/**
 * <h2>Business logic and transaction boundaries</h2>
 *
 * <p><strong>Layer:</strong> service (business logic). The heart of the application.</p>
 * <p><strong>Responsibility:</strong> implement use cases by combining repositories, domain
 * rules, security events and notifications. Transactions start and end here
 * ({@code @Transactional}).</p>
 * <p><strong>Belongs here:</strong> {@code OrderService}, {@code CartService},
 * {@code AlertService}, {@code IncidentService}, {@code DashboardService}.</p>
 * <p><strong>Does NOT belong here:</strong> HTTP concepts ({@code HttpServletRequest},
 * {@code Model}) - services must stay usable and testable without a web server.</p>
 * <p><strong>Dependency rule:</strong> service may call repository, detection, alert,
 * incident, audit and util - never the controller layer.</p>
 * <p><strong>Planned classes:</strong> Phase 3 onwards.</p>
 */
package com.yak.minisoc.service;
