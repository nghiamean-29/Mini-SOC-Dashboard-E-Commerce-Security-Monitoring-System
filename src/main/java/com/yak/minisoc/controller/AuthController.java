package com.yak.minisoc.controller;

import com.yak.minisoc.dto.RegisterRequest;
import com.yak.minisoc.entity.User;
import com.yak.minisoc.exception.DuplicateAccountException;
import com.yak.minisoc.service.AuthService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Authentication screens: the login form and the registration form (FR-AUTH-01, FR-AUTH-02).
 *
 * <h3>What this controller does NOT do - and why that matters</h3>
 * <p>There is no POST handler for {@code /login}. Login is processed by Spring Security's
 * own {@code UsernamePasswordAuthenticationFilter} (wired in
 * {@link com.yak.minisoc.config.SecurityConfig} with
 * {@code loginProcessingUrl("/login")}), which runs <em>before</em> MVC. Trying to write it
 * by hand - reading the two fields, comparing hashes, putting the user in the session - is
 * the classic way to build a login form that forgets session fixation protection, CSRF, or
 * the difference between "unknown user" and "wrong password". The framework already does
 * those things correctly; this controller only has to render the form it posts to.</p>
 *
 * <p>The GET handler for {@code /login} exists for the same reason in reverse: without it,
 * Spring Security would generate its own unstyled page, and the {@code ?error} /
 * {@code ?logout} / {@code ?expired} flags could not be shown in the layout.</p>
 *
 * <h3>The registration flow (post/redirect/get)</h3>
 * <pre>
 *   GET  /register            -> empty form
 *   POST /register   invalid  -> same form + field errors (HTTP 200, the POST is NOT
 *                                redirected: the browser must be able to resubmit)
 *   POST /register   valid    -> 302 to /login?registered
 * </pre>
 * <p>The redirect after success is not decoration: without it, pressing F5 would resend the
 * registration POST and the user would get "username already exists" for the account they
 * just created. The registered flag is passed as a query parameter rather than as a flash
 * attribute so the success message survives a manual reload - it is a statement about what
 * happened, not about the current request.</p>
 */
@Controller
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /**
     * Shows the login form.
     *
     * <p>A user who is already logged in is redirected to the home page instead of being
     * shown a second login form - otherwise the application offers a way to "log in again"
     * that silently invalidates the current session (our rule is one session per account).</p>
     */
    @GetMapping("/login")
    public String loginForm(Authentication authentication) {
        if (isLoggedIn(authentication)) {
            return "redirect:/";
        }
        return "login";
    }

    /** Shows the registration form. The empty object is required by {@code th:field}. */
    @GetMapping("/register")
    public String registerForm(Authentication authentication, Model model) {
        if (isLoggedIn(authentication)) {
            return "redirect:/";
        }
        model.addAttribute("form", RegisterRequest.empty());
        return "register";
    }

    /**
     * Handles the registration form.
     *
     * <p>Order of events, which is the point of the method:</p>
     * <ol>
     *   <li>{@code @Valid} runs Bean Validation <em>before</em> the body - a malformed
     *       request never reaches the service;</li>
     *   <li>if there are errors, the form is re-rendered with the field messages. Note that
     *       the model still contains the submitted object: the username the user typed is
     *       not thrown away (except the password fields, cleared below - see
     *       {@link #clearSecrets});</li>
     *   <li>only then the service is called.</li>
     * </ol>
     *
     * <p><strong>What the {@link DuplicateAccountException} handler does:</strong> it turns
     * the business failure into a validation error on the right input box
     * ({@code rejectValue(collision.getField(), ...)}) and re-renders the form. From the
     * user's point of view this is the same experience as a {@code @Pattern} failure - and
     * that is exactly the point: whether a rule is checked by an annotation or by a service
     * must not be visible in the user interface.</p>
     */
    @PostMapping("/register")
    public String register(@Valid @ModelAttribute("form") RegisterRequest form,
                           BindingResult bindingResult,
                           RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            clearSecrets(form);
            // No log line here on purpose: a mistyped confirmation password is noise, and
            // logging form input is how sensitive data ends up in log files.
            return "register";
        }

        try {
            User created = authService.register(form);
            log.info("Account '{}' registered from the public form", created.getUsername());
        } catch (DuplicateAccountException collision) {
            // Attach the message to the field that caused it (username or email), so the
            // user sees it under the right input box.
            bindingResult.rejectValue(collision.getField(), "duplicate", collision.getMessage());
            clearSecrets(form);
            return "register";
        }

        redirectAttributes.addFlashAttribute("registeredUsername", form.getUsername());
        return "redirect:/login?registered";
    }

    /**
     * Wipes the two password fields before the form is rendered again.
     *
     * <p>Two reasons, one for each direction the value would travel:</p>
     * <ul>
     *   <li>the browser would receive the rejected password back inside the HTML (it is
     *       already in the browser tab, but there is no reason to re-send it, and a
     *       shared/recorded screen would show it);</li>
     *   <li>the object stays in the request attributes, where a careless
     *       {@code log.info("{}", form)} or a debugger breakpoint would print it - wiping it
     *       makes that impossible rather than merely discouraged.</li>
     * </ul>
     */
    private static void clearSecrets(RegisterRequest form) {
        form.setPassword(null);
        form.setConfirmPassword(null);
    }

    /** Same helper as in {@link HomeController} (see the explanation there). */
    private static boolean isLoggedIn(Authentication authentication) {
        return authentication != null
                && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken);
    }
}
