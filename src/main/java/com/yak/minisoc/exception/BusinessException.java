package com.yak.minisoc.exception;

/**
 * Base class for the failures this application raises <strong>on purpose</strong>.
 *
 * <p><strong>The problem it solves:</strong> without a common base, "something went wrong"
 * and "the email address is already used" both reach the @ControllerAdvice as the same
 * generic {@code RuntimeException}, and the only options left are a vague message or
 * leaking a stack trace. A small hierarchy lets the web layer answer precisely:
 * a business failure becomes a readable message on the form or a 4xx status, while an
 * unexpected {@code NullPointerException} becomes a 500 and a stack trace in the log
 * (never on the screen - see the {@code server.error.*} settings).</p>
 *
 * <p><strong>Design rules of this hierarchy:</strong></p>
 * <ul>
 *   <li>Messages are short, factual and safe to show to a user - they are part of the
 *       user interface contract, not a debugging aid.</li>
 *   <li>The class carries <em>data</em> about the failure (for example the field name that
 *       caused a duplicate), never a formatted sentence the caller has to parse.</li>
 *   <li>{@code RuntimeException} rather than a checked exception: a duplicate username is
 *       an expected outcome of a use case that the caller must handle, and the controller
 *       does; forcing every intermediate method to declare it adds noise without adding
 *       safety. (This is the "unchecked domain exception" convention used by Spring
 *       itself.)</li>
 * </ul>
 */
public abstract class BusinessException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * @param message a message that is safe to show to the end user
     */
    protected BusinessException(String message) {
        super(message);
    }

    /**
     * @param message safe to show the user
     * @param cause   the technical cause, kept for the log (never shown to the user)
     */
    protected BusinessException(String message, Throwable cause) {
        super(message, cause);
    }
}
