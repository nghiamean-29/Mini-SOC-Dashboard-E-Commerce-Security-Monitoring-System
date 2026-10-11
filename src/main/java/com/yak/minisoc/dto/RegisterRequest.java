package com.yak.minisoc.dto;

import com.yak.minisoc.validation.PasswordConfirmed;
import com.yak.minisoc.validation.PasswordMatches;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.StringJoiner;

/**
 * The registration form (FR-AUTH-01), bound from an HTML POST and validated before any
 * service runs.
 *
 * <h3>Why a DTO and not the {@code User} entity?</h3>
 * <p>Two reasons, both about safety:</p>
 * <ul>
 *   <li><strong>Mass assignment.</strong> If the form were bound directly to {@code User},
 *       a request could contain {@code roles=ADMIN} or {@code status=ACTIVE} and Spring
 *       would happily set it. The DTO has exactly four fields, so exactly four fields can
 *       be supplied - the Admin role is assigned by the service, never by the browser
 *       (A08, integrity failures).</li>
 *   <li><strong>Confirm password is not a database concept.</strong> It exists only to stop
 *       typing mistakes in the form, so it lives in the DTO.</li>
 * </ul>
 *
 * <h3>Why a plain class instead of a Java 21 record</h3>
 * <p>A record would be shorter, but Thymeleaf's {@code th:field="*{username}"} and Spring's
 * {@code BindingResult.rejectValue("username", ...)} both navigate properties through the
 * JavaBeans convention ({@code getUsername()}). A record exposes {@code username()}
 * instead, which those two mechanisms do not see. In a form-backed DTO the boring
 * JavaBean shape is the one that works everywhere; the report keeps the record for the
 * immutable API DTOs of later phases.</p>
 *
 * <h3>The validation rules, and where they come from</h3>
 * <ul>
 *   <li>{@code username}: identical to the database CHECK
 *       {@code ck_users_username_format} in migration V1 ({@code ^[A-Za-z0-9._-]{3,50}$}).
 *       Validating twice is not a duplication of effort: this one produces a friendly
 *       message under the field, the database one is what makes the rule true. The regex is
 *       deliberately narrow because usernames end up in log lines and URLs.</li>
 *   <li>{@code email}: format plus the 120 characters the column allows.</li>
 *   <li>{@code password}: at least 8 characters with a letter and a digit, and at most 72
 *       <em>characters</em> - BCrypt only uses the first 72 <em>bytes</em> of input, so a
 *       longer password would silently make its tail meaningless. Rejecting it is more
 *       honest than accepting something that does not do what the user thinks.</li>
 *   <li>{@code confirmPassword}: only checked against {@code password}, by the class-level
 *       {@link PasswordMatches} constraint (a field-level annotation cannot see two
 *       fields).</li>
 * </ul>
 *
 * <p><strong>{@link #toString()} is overridden to hide the password.</strong> A DTO that
 * prints its fields ends up in logs and error reports; logging a password - even a
 * rejected one - is a data leak (NFR-OBS-02, "never log sensitive data").</p>
 */
@PasswordMatches
public class RegisterRequest implements PasswordConfirmed {

    /** Kept in sync with {@code users.username} (VARCHAR(50)) and its CHECK constraint. */
    public static final String USERNAME_PATTERN = "^[A-Za-z0-9._-]{3,50}$";

    /** At least 8 characters, containing both a letter and a digit. */
    public static final String PASSWORD_PATTERN = "^(?=.*[A-Za-z])(?=.*\\d).*$";

    @NotBlank(message = "Vui lòng nhập tên đăng nhập.")
    @Pattern(regexp = USERNAME_PATTERN,
            message = "Tên đăng nhập 3-50 ký tự, chỉ gồm chữ, số và . _ -")
    private String username;

    @NotBlank(message = "Vui lòng nhập email.")
    @Email(message = "Email không hợp lệ.")
    @Size(max = 120, message = "Email tối đa 120 ký tự.")
    private String email;

    @NotBlank(message = "Vui lòng nhập mật khẩu.")
    @Size(min = 8, max = 72, message = "Mật khẩu phải từ 8 đến 72 ký tự.")
    @Pattern(regexp = PASSWORD_PATTERN, message = "Mật khẩu phải có ít nhất một chữ cái và một chữ số.")
    private String password;

    @NotBlank(message = "Vui lòng nhập lại mật khẩu.")
    private String confirmPassword;

    @Size(max = 120, message = "Họ tên tối đa 120 ký tự.")
    private String fullName;

    /** Required by Spring's data binder (creates the object before binding). */
    public RegisterRequest() {
    }

    /**
     * An empty instance the GET handler puts in the model.
     *
     * <p>Thymeleaf's {@code th:field} reads the current value through the model attribute,
     * so the attribute must exist before the first render - otherwise the form throws.</p>
     */
    public static RegisterRequest empty() {
        return new RegisterRequest();
    }

    /** Convenience factory for tests and for the (rare) programmatic registration. */
    public static RegisterRequest of(String username, String email, String password,
                                     String confirmPassword, String fullName) {
        RegisterRequest request = new RegisterRequest();
        request.username = username;
        request.email = email;
        request.password = password;
        request.confirmPassword = confirmPassword;
        request.fullName = fullName;
        return request;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    @Override
    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    @Override
    public String getConfirmPassword() {
        return confirmPassword;
    }

    public void setConfirmPassword(String confirmPassword) {
        this.confirmPassword = confirmPassword;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    /** Never prints the password or the confirmation (see the class javadoc). */
    @Override
    public String toString() {
        return new StringJoiner(", ", "RegisterRequest{", "}")
                .add("username='" + username + "'")
                .add("email='" + email + "'")
                .add("password=[HIDDEN]")
                .add("fullName='" + fullName + "'")
                .toString();
    }
}
