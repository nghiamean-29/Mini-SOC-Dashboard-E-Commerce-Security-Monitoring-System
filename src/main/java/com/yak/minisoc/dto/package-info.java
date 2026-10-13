/**
 * <h2>Data Transfer Objects (request input and response output)</h2>
 *
 * <p><strong>Layer:</strong> boundary between layers, used by both controller and service.</p>
 * <p><strong>Responsibility:</strong> carry data across a boundary with exactly the fields
 * that boundary needs - never more. Request DTOs carry Bean Validation annotations;
 * response DTOs shape what the view or the REST client receives.</p>
 * <p><strong>Why not return JPA entities to the view:</strong> an entity may hold fields the
 * user must not see (password hash), it may trigger lazy loading during rendering, and
 * changing the database schema would silently change the public API.</p>
 * <p><strong>Planned classes:</strong> {@code request/RegisterRequest} in Phase 3,
 * {@code request/ProductRequest} in Phase 4, {@code response/AlertView} in Phase 7.</p>
 */
package com.yak.minisoc.dto;
