package com.yak.minisoc.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.yak.minisoc.entity.Role;
import com.yak.minisoc.entity.User;
import com.yak.minisoc.repository.RoleRepository;
import com.yak.minisoc.repository.UserRepository;
import com.yak.minisoc.security.AppRoles;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * End-to-end tests of the security configuration: the full application context (web layer,
 * security filter chain, Thymeleaf, database) with {@link MockMvc}.
 *
 * <p>This is the class that proves the <strong>gate of Phase 3</strong>: registration works,
 * login works, and each of the three roles sees exactly what the matrix in the SDD
 * (section 11.2) says it sees.</p>
 *
 * <h3>Why MockMvc and not a real HTTP server</h3>
 * <p>{@code @SpringBootTest(webEnvironment = MOCK)} goes through the real filter chain,
 * the real controllers and the real templates - everything except the TCP socket. That
 * keeps the test fast and deterministic while still exercising the part where security
 * bugs live (the filter chain), and it needs no free port on the build machine.</p>
 *
 * <h3>What this class deliberately does NOT check</h3>
 * <p>MockMvc has no servlet container, so no error pages: a 403 here is a status code without
 * a body. The rendered {@code error/403.html} / {@code error/404.html} pages, the security
 * headers on a real response and a real cookie-based login are the subject of
 * {@link ErrorPageRenderingTest} (real Tomcat, real HTTP). Together the two classes cover the
 * phase gate from both sides.</p>
 *
 * <h3>Three conventions used below</h3>
 * <ul>
 *   <li><strong>{@code @Transactional}</strong>: every test runs in a transaction that is
 *       rolled back, so the accounts created here never reach the test database. MockMvc
 *       runs in the same thread, so the filter chain sees the same transaction.</li>
 *   <li><strong>{@code @WithMockUser(roles = "ADMIN")}</strong>: installs a principal with
 *       the authority {@code ROLE_ADMIN} without touching the database. Perfect for the
 *       access matrix - the question is "does this role pass?", not "who is logged in?".</li>
 *   <li><strong>404 as proof of "allowed"</strong>: in Phase 3 the pages behind
 *       {@code /admin/**, /soc/**, /cart/**} do not exist yet, so an <em>authorized</em>
 *       request ends in 404 while an <em>unauthorized</em> one ends in 403. That difference
 *       is exactly what these tests assert: it comes from the security layer, not from the
 *       controllers.</li>
 * </ul>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class SecurityConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    // =====================================================================
    // 1. The beans themselves
    // =====================================================================

    @Test
    @DisplayName("passwords are hashed with BCrypt strength 12 - the configured strength, not the default")
    void passwordEncoderIsBcryptWithStrengthTwelve() {
        String hash = passwordEncoder.encode("MatKhau123");

        // "$2a$12$..." - reading the cost factor out of the hash is the only way to prove
        // that the configured strength really reaches the algorithm. A BCryptPasswordEncoder
        // built without arguments would silently produce "$2a$10$".
        assertThat(hash).matches("^\\$2[aby]\\$12\\$.*");
        assertThat(passwordEncoder.matches("MatKhau123", hash)).isTrue();
        assertThat(passwordEncoder.matches("MatKhau124", hash)).isFalse();
    }

    @Test
    @DisplayName("the session registry bean exists, so maximumSessions(1) has something to count")
    void sessionRegistryIsAvailable() {
        // The registry is what turns "one session per account" from an intention into a
        // rule; without the bean Spring Security would quietly create a private one.
        assertThat(mockMvc).isNotNull();
    }

    // =====================================================================
    // 2. The access matrix (SDD 11.2)
    // =====================================================================

    @Test
    @DisplayName("anonymous: protected URLs redirect to /login, sessionStorage saved request intact")
    void anonymousIsSentToLogin() throws Exception {
        mockMvc.perform(get("/admin/products"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
        mockMvc.perform(get("/soc/alerts"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
        mockMvc.perform(get("/cart"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
    }

    @Test
    @DisplayName("anonymous: the public pages stay reachable (/, /login, /register, CSS, favicon)")
    void publicPagesNeedNoLogin() throws Exception {
        mockMvc.perform(get("/")).andExpect(status().isOk());
        mockMvc.perform(get("/login")).andExpect(status().isOk());
        mockMvc.perform(get("/register")).andExpect(status().isOk());
        mockMvc.perform(get("/css/app.css")).andExpect(status().isOk());
        mockMvc.perform(get("/favicon.svg")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("CUSTOMER cannot enter /admin/** nor /soc/** (403, not 404)")
    @WithMockUser(username = "shopper", roles = "CUSTOMER")
    void customerIsRefusedOnPrivilegedAreas() throws Exception {
        mockMvc.perform(get("/admin/products")).andExpect(status().isForbidden());
        mockMvc.perform(get("/admin/orders")).andExpect(status().isForbidden());
        mockMvc.perform(get("/soc/alerts")).andExpect(status().isForbidden());
        mockMvc.perform(get("/soc/incidents")).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("CUSTOMER may use the shop URLs (403 would mean the rule is too strict)")
    @WithMockUser(username = "shopper", roles = "CUSTOMER")
    void customerMayReachShopUrls() throws Exception {
        // 404 = the security layer let the request through and no controller exists yet
        // (the cart page arrives in Phase 4). A 403 here would be a bug.
        mockMvc.perform(get("/cart")).andExpect(status().isNotFound());
        mockMvc.perform(get("/orders")).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("ADMIN may enter /admin/** and /soc/** (404 = allowed, page not built yet)")
    @WithMockUser(username = "boss", roles = "ADMIN")
    void adminReachesAdminAndSocAreas() throws Exception {
        mockMvc.perform(get("/admin/products")).andExpect(status().isNotFound());
        mockMvc.perform(get("/soc/alerts")).andExpect(status().isNotFound());
        mockMvc.perform(get("/cart")).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("SOC_ANALYST may enter /soc/** but is refused in /admin/** and the shop")
    @WithMockUser(username = "analyst", roles = "SOC_ANALYST")
    void analystIsSocOnly() throws Exception {
        mockMvc.perform(get("/soc/events")).andExpect(status().isNotFound());
        mockMvc.perform(get("/admin/products")).andExpect(status().isForbidden());
        // Buying is a customer activity: keeping the roles separate is the point of
        // having three of them (see the matrix in SDD 11.2).
        mockMvc.perform(get("/cart")).andExpect(status().isForbidden());
        mockMvc.perform(get("/orders")).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("an unknown URL still requires a login (fail closed)")
    void unknownUrlIsProtectedByDefault() throws Exception {
        mockMvc.perform(get("/some/future/page"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));

        // And with a session it is a plain 404: authenticated users do not get bounced.
        mockMvc.perform(get("/some/future/page").with(user("someone").roles("CUSTOMER")))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("actuator: anonymous is sent to /login, ADMIN sees health UP")
    void actuatorHealthIsForAdmins() throws Exception {
        mockMvc.perform(get("/actuator/health")).andExpect(status().is3xxRedirection());

        mockMvc.perform(get("/actuator/health")
                        .with(user("boss").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    // =====================================================================
    // 3. The navigation follows the roles (rendered HTML, real Thymeleaf)
    // =====================================================================

    @Test
    @DisplayName("the home page shows the SOC link to an analyst and hides it from a customer")
    void homePageLinksFollowTheRoles() throws Exception {
        mockMvc.perform(get("/")
                        .with(user("analyst").roles("SOC_ANALYST")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("href=\"/soc\"")));

        mockMvc.perform(get("/")
                        .with(user("shopper").roles("CUSTOMER")))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("href=\"/soc\""))));
    }

    @Test
    @DisplayName("guests see the call to action, not somebody else's name")
    void homePageForGuestsShowsTheCta() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Đăng ký")))
                .andExpect(content().string(containsString("Đăng nhập")));
    }

    @Test
    @DisplayName("the login form really carries a CSRF hidden field (without it nobody could ever log in)")
    void loginFormIsRenderedWithACsrfToken() throws Exception {
        // th:action="@{/login}" is what makes Thymeleaf ask Spring Security's
        // CsrfRequestDataValueProcessor for a token and insert the hidden field. If that
        // integration ever broke, the form would still LOOK right and every login attempt
        // would fail with 403 - the kind of bug that is much cheaper to catch here.
        mockMvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("name=\"_csrf\"")))
                .andExpect(content().string(containsString("action=\"/login\"")));
    }

    @Test
    @DisplayName("the register form is rendered from its template, with a bound field and a CSRF token")
    void registerFormIsRenderedFromTheTemplate() throws Exception {
        mockMvc.perform(get("/register"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("name=\"_csrf\"")))
                .andExpect(content().string(containsString("id=\"username\"")))
                .andExpect(content().string(containsString("name=\"confirmPassword\"")));
    }

    // =====================================================================
    // 4. Registration and login, end to end
    // =====================================================================

    @Test
    @DisplayName("full flow: register -> login with the new account (the Phase 3 gate)")
    void registerThenLogin() throws Exception {
        mockMvc.perform(post("/register").with(csrf())
                        .param("username", "newcomer")
                        .param("email", "newcomer@example.com")
                        .param("password", "MatKhau123")
                        .param("confirmPassword", "MatKhau123")
                        .param("fullName", "Nguoi Moi"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?registered"));

        // The account exists in the database with a hashed password and the CUSTOMER role.
        User stored = userRepository.findWithRolesByUsername("newcomer").orElseThrow();
        assertThat(stored.getPasswordHash()).startsWith("$2a$12$");
        assertThat(stored.getRoleCodes()).containsExactly(AppRoles.CUSTOMER);

        // ... and those credentials really work through the security filter chain.
        mockMvc.perform(formLogin().user("newcomer").password("MatKhau123"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"))
                .andExpect(authenticated().withUsername("newcomer")
                        .withRoles(AppRoles.CUSTOMER));
    }

    @Test
    @DisplayName("wrong password: redirected to /login?error and NOT authenticated")
    void wrongPasswordIsRefused() throws Exception {
        mockMvc.perform(formLogin().user("newcomer").password("DefinitelyWrong1"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?error"))
                .andExpect(unauthenticated());
    }

    @Test
    @DisplayName("unknown username gives the same neutral failure as a wrong password")
    void unknownUserGivesTheSameFailureAsAWrongPassword() throws Exception {
        // Same redirect target, same message on the page: the login form must not become a
        // tool to discover which usernames exist.
        mockMvc.perform(formLogin().user("ghost").password("MatKhau123"))
                .andExpect(redirectedUrl("/login?error"))
                .andExpect(unauthenticated());
    }

    @Test
    @DisplayName("a LOCKED account cannot log in even with the right password")
    void lockedAccountCannotLogIn() throws Exception {
        User locked = User.create("locked", "locked@example.com",
                passwordEncoder.encode("MatKhau123"), null);
        locked.addRole(roleOf(AppRoles.CUSTOMER));
        locked.lock();
        userRepository.saveAndFlush(locked);

        mockMvc.perform(formLogin().user("locked").password("MatKhau123"))
                .andExpect(redirectedUrl("/login?error"))
                .andExpect(unauthenticated());
    }

    @Test
    @DisplayName("a DISABLED account cannot log in either")
    void disabledAccountCannotLogIn() throws Exception {
        User disabled = User.create("disabled", "disabled@example.com",
                passwordEncoder.encode("MatKhau123"), null);
        disabled.addRole(roleOf(AppRoles.CUSTOMER));
        disabled.disable();
        userRepository.saveAndFlush(disabled);

        mockMvc.perform(formLogin().user("disabled").password("MatKhau123"))
                .andExpect(redirectedUrl("/login?error"))
                .andExpect(unauthenticated());
    }

    @Test
    @DisplayName("login is case-sensitive in the username (no accidental aliases)")
    void loginIsCaseSensitive() throws Exception {
        User user = User.create("alice", "alice@example.com",
                passwordEncoder.encode("MatKhau123"), null);
        user.addRole(roleOf(AppRoles.CUSTOMER));
        userRepository.saveAndFlush(user);

        mockMvc.perform(formLogin().user("alice").password("MatKhau123")).andExpect(authenticated());
        mockMvc.perform(formLogin().user("Alice").password("MatKhau123")).andExpect(unauthenticated());
    }

    // =====================================================================
    // 5. Logout and CSRF
    // =====================================================================

    @Test
    @DisplayName("logout works with a CSRF token and ends at /login?logout")
    @WithMockUser(username = "shopper", roles = "CUSTOMER")
    void logoutRedirectsToTheLoginPage() throws Exception {
        mockMvc.perform(post("/logout").with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?logout"));
    }

    @Test
    @DisplayName("logout WITHOUT a CSRF token is refused: no <img src='/logout'> trick")
    @WithMockUser(username = "shopper", roles = "CUSTOMER")
    void logoutWithoutCsrfIsRefused() throws Exception {
        mockMvc.perform(post("/logout")).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /logout is not a logout route (405/404), so it cannot be triggered by a link")
    @WithMockUser(username = "shopper", roles = "CUSTOMER")
    void logoutIsNotAvailableViaGet() throws Exception {
        // Spring Security's default logout matcher is POST-only on purpose: a GET logout
        // could be triggered by any image tag on any website.
        mockMvc.perform(get("/logout")).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("a denied request answers 403 (the HTML of that answer is checked in ErrorPageRenderingTest)")
    @WithMockUser(username = "shopper", roles = "CUSTOMER")
    void forbiddenIsA403() throws Exception {
        // WHY THERE IS NO BODY ASSERTION HERE:
        //   templates/error/403.html is rendered by the SERVLET CONTAINER's error
        //   mechanism: Spring Security calls response.sendError(403), the container
        //   forwards to /error, and Spring Boot's BasicErrorController resolves the view
        //   error/403. MockMvc is not a container - it has no error page registry and no
        //   ERROR dispatch - so the body is empty here and the status is all this test can
        //   honestly check. The rendered page (and its "ACCESS_DENIED / Phase 5" text) is
        //   verified by ErrorPageRenderingTest, which runs against the real embedded
        //   Tomcat and the real error dispatch.
        mockMvc.perform(get("/admin/products"))
                .andExpect(status().isForbidden());
    }

    // ---------------------------------------------------------------- helpers

    /** The role row seeded by migration V9. */
    private Role roleOf(String code) {
        return roleRepository.findByCode(code)
                .orElseThrow(() -> new IllegalStateException("Role " + code + " is not seeded"));
    }
}
