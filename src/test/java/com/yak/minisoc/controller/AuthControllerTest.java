package com.yak.minisoc.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasProperty;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.yak.minisoc.config.SecurityConfig;
import com.yak.minisoc.dto.RegisterRequest;
import com.yak.minisoc.entity.User;
import com.yak.minisoc.exception.DuplicateAccountException;
import com.yak.minisoc.service.AuthService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.ActiveProfiles;

/**
 * Web-layer tests for {@link AuthController} - a {@code @WebMvcTest} slice.
 *
 * <h3>What "slice" means here, and why it is the right size</h3>
 * <p>Only the MVC layer and the security filter chain start: no database, no
 * {@code AuthService} (it is a mock). So these tests are fast and they fail for exactly one
 * reason - the controller's contract with the browser:</p>
 * <ul>
 *   <li>which view is rendered, and with which model attributes;</li>
 *   <li>what happens to the submitted data when validation fails (errors bound to the right
 *       field, passwords wiped);</li>
 *   <li>post/redirect/get after success, and the business error mapped onto a field error;</li>
 *   <li>CSRF: a POST without a token is refused.</li>
 * </ul>
 *
 * <p><strong>Two deliberate details:</strong></p>
 * <ul>
 *   <li>{@code @Import(SecurityConfig.class)}: the slice deliberately does not include our
 *       filter chain by default, and without it the CSRF assertions would pass for the wrong
 *       reason (no security at all). Importing it means the tests run against the real rules.</li>
 *   <li>{@link MockitoBean} replaced {@code @MockBean} in Spring Boot 3.4 / removed in Boot 4:
 *       the mock is registered as a bean override and is reset between tests automatically.</li>
 * </ul>
 *
 * <p>Thymeleaf is <em>not</em> part of a {@code @WebMvcTest} slice, so the templates are not
 * rendered here - that is why the assertions check the view name and the model instead of the
 * HTML. The HTML itself is verified end to end in {@code SecurityConfigTest}, where the full
 * application context (including Thymeleaf) is started.</p>
 */
@WebMvcTest(controllers = AuthController.class)
@Import(SecurityConfig.class)
@ActiveProfiles("test")
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthService authService;

    // ------------------------------------------------------------------ GET pages

    @Test
    @DisplayName("GET /login renders the login view")
    void loginPageIsRendered() throws Exception {
        mockMvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(view().name("login"));
    }

    @Test
    @DisplayName("GET /register renders the form with an empty RegisterRequest in the model")
    void registerPageIsRenderedWithAnEmptyForm() throws Exception {
        mockMvc.perform(get("/register"))
                .andExpect(status().isOk())
                .andExpect(view().name("register"))
                .andExpect(model().attributeExists("form"));
    }

    @Test
    @DisplayName("a logged-in user visiting /login or /register is sent to the home page")
    @WithMockUser(username = "alice", roles = "CUSTOMER")
    void loggedInUserIsRedirectedHome() throws Exception {
        mockMvc.perform(get("/login")).andExpect(redirectedUrl("/"));
        mockMvc.perform(get("/register")).andExpect(redirectedUrl("/"));
    }

    // --------------------------------------------------------------- validation

    @Test
    @DisplayName("an invalid form is re-rendered with the error bound to the username field")
    void invalidFormShowsFieldErrors() throws Exception {
        mockMvc.perform(post("/register").with(csrf())
                        .param("username", "no")
                        .param("email", "not-an-email")
                        .param("password", "short")
                        .param("confirmPassword", "short"))
                .andExpect(status().isOk())
                .andExpect(view().name("register"))
                .andExpect(model().attributeHasFieldErrors("form", "username", "email", "password"));
    }

    @Test
    @DisplayName("a mismatched confirmation is reported as a global error (type-level constraint)")
    void mismatchedConfirmationIsAGlobalError() throws Exception {
        mockMvc.perform(post("/register").with(csrf())
                        .param("username", "alice")
                        .param("email", "alice@example.com")
                        .param("password", "MatKhau123")
                        .param("confirmPassword", "MatKhau124"))
                .andExpect(status().isOk())
                .andExpect(view().name("register"))
                .andExpect(model().attributeHasNoFieldErrors("form", "password"))
                .andExpect(model().attributeHasErrors("form"));
    }

    @Test
    @DisplayName("the service is never called when validation fails")
    void serviceIsNotCalledForInvalidInput() throws Exception {
        mockMvc.perform(post("/register").with(csrf())
                        .param("username", "")
                        .param("email", "")
                        .param("password", "")
                        .param("confirmPassword", ""))
                .andExpect(view().name("register"));

        org.mockito.Mockito.verifyNoInteractions(authService);
    }

    @Test
    @DisplayName("the rejected password is wiped before the form is shown again")
    void rejectedPasswordsAreWiped() throws Exception {
        mockMvc.perform(post("/register").with(csrf())
                        .param("username", "alice")
                        .param("email", "alice@example.com")
                        .param("password", "MatKhau123")
                        .param("confirmPassword", "MatKhau124"))
                .andExpect(view().name("register"))
                // The typed username comes back (a courtesy), the secrets do not:
                .andExpect(model().attribute("form", hasProperty("username", is("alice"))))
                .andExpect(model().attribute("form", hasProperty("password", nullValue())))
                .andExpect(model().attribute("form", hasProperty("confirmPassword", nullValue())));
    }

    // ------------------------------------------------------------------ success

    @Test
    @DisplayName("a valid registration redirects to /login?registered (post/redirect/get)")
    void validRegistrationRedirects() throws Exception {
        given(authService.register(any(RegisterRequest.class)))
                .willReturn(User.create("alice", "alice@example.com", "$2a$12$hash", null));

        mockMvc.perform(post("/register").with(csrf())
                        .param("username", "alice")
                        .param("email", "alice@example.com")
                        .param("password", "MatKhau123")
                        .param("confirmPassword", "MatKhau123")
                        .param("fullName", "Nguyen Van A"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?registered"));
    }

    @Test
    @DisplayName("a duplicate username becomes a field error, not an error page")
    void duplicateUsernameBecomesAFieldError() throws Exception {
        willThrow(DuplicateAccountException.username("alice"))
                .given(authService).register(any(RegisterRequest.class));

        mockMvc.perform(post("/register").with(csrf())
                        .param("username", "alice")
                        .param("email", "alice@example.com")
                        .param("password", "MatKhau123")
                        .param("confirmPassword", "MatKhau123"))
                .andExpect(status().isOk())
                .andExpect(view().name("register"))
                .andExpect(model().attributeHasFieldErrors("form", "username"))
                .andExpect(model().attributeHasFieldErrorCode("form", "username", "duplicate"));
    }

    @Test
    @DisplayName("a duplicate e-mail becomes a field error under the email input")
    void duplicateEmailBecomesAFieldError() throws Exception {
        willThrow(DuplicateAccountException.email("alice@example.com"))
                .given(authService).register(any(RegisterRequest.class));

        mockMvc.perform(post("/register").with(csrf())
                        .param("username", "alice")
                        .param("email", "alice@example.com")
                        .param("password", "MatKhau123")
                        .param("confirmPassword", "MatKhau123"))
                .andExpect(view().name("register"))
                .andExpect(model().attributeHasFieldErrors("form", "email"));
    }

    // --------------------------------------------------------------------- CSRF

    @Test
    @DisplayName("POST /register without a CSRF token is refused with 403")
    void registrationWithoutCsrfTokenIsRefused() throws Exception {
        // This is the protection against a foreign page auto-submitting our registration
        // form. It works because every Thymeleaf form includes the hidden token field
        // automatically (th:action) - so real users never notice it exists.
        mockMvc.perform(post("/register")
                        .param("username", "alice")
                        .param("email", "alice@example.com")
                        .param("password", "MatKhau123")
                        .param("confirmPassword", "MatKhau123"))
                .andExpect(status().isForbidden());

        org.mockito.Mockito.verifyNoInteractions(authService);
    }

    @Test
    @DisplayName("the register page carries its own HTML: the controller stays a thin layer")
    void controllerAddsNothingButTheForm() throws Exception {
        var result = mockMvc.perform(get("/register")).andReturn();

        var form = (RegisterRequest) result.getModelAndView().getModel().get("form");
        assertThat(form.getUsername()).isNull();
        assertThat(form.getPassword()).isNull();
        assertThat(result.getModelAndView().getViewName()).isEqualTo("register");
    }
}
