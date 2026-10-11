package com.yak.minisoc.validation;

/**
 * Implemented by any form DTO that asks the user to type a password twice.
 *
 * <p><strong>Why an interface, and what it buys:</strong> a class-level Bean Validation
 * constraint has to know how to read the two values. Pointing the validator directly at
 * {@code RegisterRequest} would work today and then fail the day a second form appears
 * (change password in Phase 5, password reset in Phase 8): either the validator is
 * duplicated, or it is rewritten as a pile of {@code if (object instanceof ...)} checks.</p>
 *
 * <p>With this tiny contract the validator only knows "there is a password and a
 * confirmation" - so {@code @PasswordMatches} can be put on any number of DTOs, and the
 * validator stays five lines long. This is the same idea as {@code Comparable} or
 * {@code Closeable}: the constraint needs a capability, not a concrete class.</p>
 */
public interface PasswordConfirmed {

    /** The password the user chose (may be null before binding). */
    String getPassword();

    /** The password typed a second time (may be null before binding). */
    String getConfirmPassword();
}
