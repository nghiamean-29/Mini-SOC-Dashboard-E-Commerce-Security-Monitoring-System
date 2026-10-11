/**
 * <h2>Custom exceptions and the global exception handler</h2>
 *
 * <p><strong>Layer:</strong> cross-cutting.</p>
 * <p><strong>Responsibility:</strong> express failures in the language of the domain
 * ({@code UserNotFoundException}, {@code InsufficientStockException}, ...) and translate
 * them into HTTP responses in exactly one place.</p>
 * <p><strong>Belongs here:</strong> a small base exception plus specific subclasses, and a
 * {@code @ControllerAdvice} class with {@code @ExceptionHandler} methods.</p>
 * <p><strong>Why custom exceptions instead of one big {@code RuntimeException}:</strong>
 * the caller can react differently to different failures, and the error message shown to
 * the user can be accurate without leaking internal details.</p>
 * <p><strong>Planned classes:</strong> Phase 3 (first exceptions) and Phase 4.</p>
 */
package com.yak.minisoc.exception;
