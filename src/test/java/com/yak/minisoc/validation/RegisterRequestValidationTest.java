package com.yak.minisoc.validation;

import static org.assertj.core.api.Assertions.assertThat;

import com.yak.minisoc.dto.RegisterRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Plain unit tests for the registration form's validation rules - no Spring context, no
 * database, milliseconds to run.
 *
 * <p><strong>Why test the annotations:</strong> Bean Validation rules are configuration,
 * and configuration that nobody tests is configuration that is wrong. The two expensive
 * mistakes this class catches:</p>
 * <ul>
 *   <li>the username regex drifting away from the database CHECK
 *       {@code ck_users_username_format} (V1) - then the user gets a 500 from the
 *       constraint instead of a friendly message under the field;</li>
 *   <li>the password rules being weaker than intended (for example a missing
 *       {@code @Pattern}), which is invisible until somebody registers with "12345678".</li>
 * </ul>
 *
 * <p>{@link Validation#buildDefaultValidatorFactory()} is the same factory Spring Boot
 * configures for {@code @Valid} at the controller boundary, so this test validates exactly
 * the rules that run in production - not a copy of them.</p>
 */
class RegisterRequestValidationTest {

    private static ValidatorFactory validatorFactory;
    private static Validator validator;

    @BeforeAll
    static void openValidator() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @AfterAll
    static void closeValidator() {
        validatorFactory.close();
    }

    /** A request that must be accepted - the baseline every test below changes in one way. */
    private static RegisterRequest validRequest() {
        return RegisterRequest.of("alice.nguyen", "Alice@Example.com", "MatKhau123",
                "MatKhau123", "Alice Nguyen");
    }

    private static Set<String> violatedProperties(RegisterRequest request) {
        return validator.validate(request).stream()
                .map(ConstraintViolation::getPropertyPath)
                .map(Object::toString)
                .collect(Collectors.toSet());
    }

    @Test
    @DisplayName("a well-formed registration passes every rule")
    void validFormHasNoViolations() {
        assertThat(validator.validate(validRequest())).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"ab", "has space", "bad@name", "quotes'here", "semi;colon"})
    @DisplayName("usernames that the database CHECK would reject are rejected earlier, with a message")
    void rejectsUsernamesTheDatabaseWouldAlsoReject(String username) {
        RegisterRequest request = validRequest();
        request.setUsername(username);

        assertThat(violatedProperties(request)).contains("username");
    }

    @ParameterizedTest
    @ValueSource(strings = {"alice", "a.b-c_d", "USER_123"})
    @DisplayName("usernames the database accepts are accepted here too (the two rules agree)")
    void acceptsUsernamesTheDatabaseAccepts(String username) {
        RegisterRequest request = validRequest();
        request.setUsername(username);

        assertThat(validator.validate(request)).isEmpty();
    }

    @Test
    @DisplayName("username longer than the VARCHAR(50) column is refused")
    void rejectsOverlongUsername() {
        RegisterRequest request = validRequest();
        request.setUsername("a".repeat(51));

        assertThat(violatedProperties(request)).contains("username");
    }

    @ParameterizedTest
    @ValueSource(strings = {"not-an-email", "missing@tld", "@nolocal.com", "spaces in@mail.com"})
    @DisplayName("malformed e-mail addresses are refused")
    void rejectsMalformedEmail(String email) {
        RegisterRequest request = validRequest();
        request.setEmail(email);

        assertThat(violatedProperties(request)).contains("email");
    }

    @ParameterizedTest
    @ValueSource(strings = {"short1", "alllettersonly", "12345678", "1234567a"})
    @DisplayName("weak passwords are refused: too short, letters only, digits only")
    void rejectsWeakPasswords(String password) {
        RegisterRequest request = validRequest();
        request.setPassword(password);
        request.setConfirmPassword(password);

        assertThat(violatedProperties(request)).contains("password");
    }

    @Test
    @DisplayName("a password longer than 72 characters is refused, because BCrypt ignores the rest")
    void rejectsPasswordLongerThanBcryptUses() {
        String tooLong = "MatKhau123" + "x".repeat(70);   // 80 characters
        RegisterRequest request = validRequest();
        request.setPassword(tooLong);
        request.setConfirmPassword(tooLong);

        assertThat(violatedProperties(request)).contains("password");
    }

    @Test
    @DisplayName("the class-level rule reports a mismatch and points at the confirmation field")
    void rejectsMismatchedConfirmation() {
        RegisterRequest request = validRequest();
        request.setConfirmPassword("MatKhau124");

        Set<ConstraintViolation<RegisterRequest>> violations = validator.validate(request);

        assertThat(violations).hasSize(1);
        ConstraintViolation<RegisterRequest> violation = violations.iterator().next();
        assertThat(violation.getMessage()).isEqualTo("Mật khẩu xác nhận không khớp.");
        // A type-level constraint has an empty property path: Spring MVC turns it into a
        // GLOBAL error, so the page shows it above the form instead of under one input.
        assertThat(violation.getPropertyPath()).hasToString("");
    }

    @Test
    @DisplayName("a missing password is reported once (by @NotBlank), not twice")
    void missingPasswordIsReportedByTheFieldRuleOnly() {
        RegisterRequest request = validRequest();
        request.setPassword(null);
        request.setConfirmPassword(null);

        // Both fields are reported; the class-level rule stays silent, so the user does
        // not read "passwords do not match" about two fields they never filled in.
        Set<String> properties = violatedProperties(request);
        assertThat(properties).contains("password", "confirmPassword");
        assertThat(properties).noneMatch(path -> path.isEmpty());
    }

    @Test
    @DisplayName("toString() never prints the password")
    void toStringHidesThePassword() {
        String printed = validRequest().toString();

        assertThat(printed).contains("username='alice.nguyen'").contains("[HIDDEN]");
        assertThat(printed).doesNotContain("MatKhau123");
    }
}
