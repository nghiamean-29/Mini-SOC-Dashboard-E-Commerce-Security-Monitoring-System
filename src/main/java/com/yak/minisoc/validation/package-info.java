/**
 * <h2>Custom Bean Validation constraints</h2>
 *
 * <p><strong>Layer:</strong> cross-cutting input validation.</p>
 * <p><strong>Responsibility:</strong> hold constraints that Bean Validation does not provide
 * out of the box, for example password must be strong or
 * value must be a valid IP address, together with their validator classes.</p>
 * <p><strong>Belongs here:</strong> constraint annotations and the {@code ConstraintValidator}
 * implementations; standard annotations ({@code @NotBlank}, {@code @Size}, ...) are used
 * directly on DTOs and do not need a class here.</p>
 * <p><strong>Planned classes:</strong> Phase 3 ({@code @StrongPassword}).</p>
 */
package com.yak.minisoc.validation;
