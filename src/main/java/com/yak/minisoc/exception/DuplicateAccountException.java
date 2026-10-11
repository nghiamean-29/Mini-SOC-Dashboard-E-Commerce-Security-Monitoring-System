package com.yak.minisoc.exception;

/**
 * Raised when a registration collides with an existing account (FR-AUTH-01).
 *
 * <p><strong>Why a dedicated exception instead of one message string?</strong> The
 * controller has to do two different things depending on which field collided: the error
 * must appear under the right input box ("Tên đăng nhập đã được sử dụng" under
 * {@code username}, "Email đã được đăng ký" under {@code email}). Carrying the field name
 * as data - and not as part of a sentence - is what makes that possible without string
 * parsing:</p>
 *
 * <pre>
 * bindingResult.rejectValue(collision.getField(), "duplicate", collision.getMessage());
 * </pre>
 *
 * <h3>Where the real guarantee lives</h3>
 * <p>The service checks {@code existsByUsername} before inserting, but that check alone
 * would be a race: two requests can both see "no such user" and both insert. The database
 * constraints {@code uk_users_username} and {@code uk_users_email} (migration V1) are the
 * actual guarantee; the service check exists to give a friendly message in the normal case,
 * and this exception is also thrown when the constraint fires (see
 * {@link com.yak.minisoc.service.AuthService}). That is the same "check for the user,
 * constraint for correctness" pattern used by the alert dedup key in Phase 7.</p>
 */
public class DuplicateAccountException extends BusinessException {

    private static final long serialVersionUID = 1L;

    /** The form field that collided: {@code "username"} or {@code "email"}. */
    public static final String FIELD_USERNAME = "username";
    public static final String FIELD_EMAIL = "email";

    private final String field;

    private DuplicateAccountException(String field, String message) {
        super(message);
        this.field = field;
    }

    /** The username is taken (case-sensitive: usernames are stored exactly as typed). */
    public static DuplicateAccountException username(String username) {
        return new DuplicateAccountException(FIELD_USERNAME,
                "Tên đăng nhập '" + username + "' đã được sử dụng.");
    }

    /** The e-mail address is already registered (compared case-insensitively). */
    public static DuplicateAccountException email(String email) {
        return new DuplicateAccountException(FIELD_EMAIL,
                "Email '" + email + "' đã được đăng ký.");
    }

    /** {@code username} or {@code email} - the name of the input the message belongs to. */
    public String getField() {
        return field;
    }
}
