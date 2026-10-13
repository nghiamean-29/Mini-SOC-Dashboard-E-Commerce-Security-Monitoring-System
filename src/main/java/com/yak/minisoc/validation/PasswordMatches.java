package com.yak.minisoc.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Class-level constraint: the password and its confirmation must be identical.
 *
 * <h3>Why this needs a custom annotation at all</h3>
 * <p>Bean Validation field annotations ({@code @NotBlank}, {@code @Size}, ...) each see one
 * field. "These two values must be equal" is a statement about the <em>object</em>, and the
 * standard way to express that is a constraint on the type - applied here with
 * {@code @Target(ElementType.TYPE)} and implemented by
 * {@link PasswordMatchesValidator}.</p>
 *
 * <p>When a type-level constraint fails, Hibernate Validator reports the violation with an
 * empty property path; Spring MVC turns that into a <em>global</em> error on the
 * {@code BindingResult}. The register page therefore shows it in its own block
 * ({@code #fields.globalErrors()}) instead of under one of the two input boxes - because
 * neither box is individually wrong.</p>
 *
 * <p>The three members below ({@code message}, {@code groups}, {@code payload}) are not
 * decoration: they are part of the Bean Validation contract and every constraint must
 * declare them. {@code message} can be overridden at the point of use
 * ({@code @PasswordMatches(message = "...")}); {@code groups} allows validating different
 * subsets of rules in different situations; {@code payload} lets a caller attach metadata
 * (severity, error code) that a validator can read.</p>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = PasswordMatchesValidator.class)
@Documented
public @interface PasswordMatches {

    /** Shown to the user; Vietnamese because it is part of the interface, not of the code. */
    String message() default "Mật khẩu xác nhận không khớp.";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
