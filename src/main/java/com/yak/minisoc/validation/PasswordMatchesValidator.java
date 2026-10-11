package com.yak.minisoc.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * The logic behind {@link PasswordMatches}.
 *
 * <p><strong>Two rules make this implementation correct, and both are about not doing
 * somebody else's job:</strong></p>
 * <ol>
 *   <li><strong>null is valid here.</strong> If the browser did not send a password at all,
 *       {@code @NotBlank} on the field reports it - accurately, under the right input box.
 *       If this validator also reported "passwords do not match", the user would see two
 *       messages for one mistake, and the second one would be misleading.</li>
 *   <li><strong>No trimming, no case folding, no length check.</strong> A password is an
 *       opaque byte sequence: " abc123 " and "abc123" are different passwords, and silently
 *       trimming would make the login fail later in a way nobody can reproduce. Length and
 *       character rules belong to {@code @Size}/{@code @Pattern} on the field.</li>
 * </ol>
 *
 * <p>The comparison is <strong>not</strong> constant-time, and that is fine: this code runs
 * on values the same user just typed into their own form, so there is no secret to extract
 * by timing it. (Contrast with comparing a submitted password against a stored one, which
 * {@code PasswordEncoder.matches} handles.)</p>
 *
 * <p>Spring instantiates validators itself (they are not beans), so this class has no
 * dependencies and needs no annotation - which is exactly why the constraint stays cheap to
 * reuse in Phase 5 and 8.</p>
 */
public class PasswordMatchesValidator implements ConstraintValidator<PasswordMatches, PasswordConfirmed> {

    @Override
    public boolean isValid(PasswordConfirmed value, ConstraintValidatorContext context) {
        if (value == null) {
            // The object itself is missing: nothing to compare (and @NotNull would say so).
            return true;
        }

        String password = value.getPassword();
        String confirmation = value.getConfirmPassword();

        if (password == null || confirmation == null) {
            // Reported by @NotBlank on the field, see rule 1 above.
            return true;
        }

        return password.equals(confirmation);
    }
}
