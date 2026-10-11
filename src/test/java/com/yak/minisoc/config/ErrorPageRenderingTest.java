package com.yak.minisoc.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.yak.minisoc.dto.RegisterRequest;
import com.yak.minisoc.service.AuthService;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * The page-level tests: everything a browser would actually receive, over real HTTP, from the
 * real embedded Tomcat.
 *
 * <h3>Why this class exists next to {@link SecurityConfigTest}</h3>
 * <p>MockMvc stops short of one important thing: the <strong>error mechanism belongs to the
 * servlet container</strong>. When access is denied, Spring Security calls
 * {@code response.sendError(403)}; the container forwards to {@code /error}; Spring Boot's
 * {@code BasicErrorController} then resolves the view {@code error/403} and Thymeleaf renders
 * {@code templates/error/403.html}. MockMvc has no container and no error page registry, so it
 * can prove the <em>status</em> of a denial but never the <em>page</em>.</p>
 *
 * <p>This class starts the application for real ({@code RANDOM_PORT}) and drives it with the
 * JDK's own {@link HttpClient} - no extra test library, and full control over the three things
 * that matter here: cookies (a session), redirects (never followed, so the 302 itself is
 * visible) and the {@code Accept} header (a browser sends {@code text/html}).</p>
 *
 * <p>This is the automated version of the Phase 3 gate check "log in, then open
 * {@code /admin} with a customer account and see the 403 page".</p>
 *
 * <h3>Housekeeping</h3>
 * <p>The class cannot be {@code @Transactional}: Tomcat serves the requests on its own threads
 * with their own transactions, so the account it creates is really committed. It cleans up
 * before and after every test instead ({@code users} cascades to {@code user_roles}, see
 * {@code V1__create_users_and_roles.sql}), which also makes the class safe to re-run after a
 * failure.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class ErrorPageRenderingTest {

    private static final String USERNAME = "e2e-customer";
    private static final String EMAIL = "e2e-customer@example.com";
    private static final String PASSWORD = "MatKhau2026";

    /** The hidden field Thymeleaf + Spring Security insert into every th:action form. */
    private static final Pattern CSRF_INPUT =
            Pattern.compile("<input[^>]*name=\"_csrf\"[^>]*>");
    private static final Pattern VALUE_ATTRIBUTE = Pattern.compile("value=\"([^\"]+)\"");

    @LocalServerPort
    private int port;

    @Autowired
    private AuthService authService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private HttpClient browser;

    @BeforeEach
    void openFreshBrowser() {
        // A new cookie jar per test: "am I logged in?" is part of the test state, and one
        // test must never inherit the session of another.
        browser = HttpClient.newBuilder()
                .cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL))
                .followRedirects(HttpClient.Redirect.NEVER)
                .connectTimeout(Duration.ofSeconds(5))
                .build();

        jdbcTemplate.update("DELETE FROM users WHERE username = ?", USERNAME);
        authService.register(RegisterRequest.of(USERNAME, EMAIL, PASSWORD, PASSWORD, "Khach E2E"));
    }

    @AfterEach
    void removeTheTestAccount() {
        // user_roles.user_id is ON DELETE CASCADE, so the role link disappears with the user.
        jdbcTemplate.update("DELETE FROM users WHERE username = ?", USERNAME);
    }

    // =====================================================================
    // 1. The responses themselves
    // =====================================================================

    @Test
    @DisplayName("every page arrives with the security headers, and the CSP allows no inline script")
    void securityHeadersAreSent() throws Exception {
        HttpResponse<String> response = get("/login");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("Content-Security-Policy").orElse(""))
                .contains("default-src 'self'")
                .contains("frame-ancestors 'none'")
                .doesNotContain("'unsafe-inline'");
        assertThat(response.headers().firstValue("X-Frame-Options")).contains("DENY");
        assertThat(response.headers().firstValue("Referrer-Policy")).contains("same-origin");
        // A missing Content-Type header is what turns an uploaded file into a script in old
        // browsers; Spring Security sends this one by default and nothing here disables it.
        assertThat(response.headers().firstValue("X-Content-Type-Options")).contains("nosniff");
    }

    @Test
    @DisplayName("anonymous requests to a protected URL are redirected to /login by the real server")
    void anonymousIsRedirectedByTheRealServer() throws Exception {
        for (String path : new String[] { "/admin/products", "/soc/alerts", "/cart", "/orders" }) {
            HttpResponse<String> response = get(path);
            assertThat(response.statusCode())
                    .as("anonymous GET %s must be redirected", path)
                    .isEqualTo(302);
            assertThat(response.headers().firstValue("Location").orElse(""))
                    .as("anonymous GET %s must end at the login page", path)
                    .endsWith("/login");
        }
    }

    @Test
    @DisplayName("a missing page renders templates/error/404.html, not the white-label page")
    void missingStaticPageRendersTheNotFoundTemplate() throws Exception {
        // /css/** is public (the login page needs its stylesheet), so this is the one kind of
        // URL that can reach a 404 without being sent to the login page first.
        HttpResponse<String> response = get("/css/khong-ton-tai.css");

        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(response.body())
                .as("the custom 404 template must be rendered")
                .contains("Không tìm thấy trang")
                .contains("Phase 4");
    }

    // =====================================================================
    // 2. The Phase 3 gate: log in and be refused - in the browser
    // =====================================================================

    @Test
    @DisplayName("gate: register -> login -> home page greets the user -> /admin answers 403 with the custom page")
    void loggedInCustomerSeesTheForbiddenPage() throws Exception {
        HttpResponse<String> login = login(USERNAME, PASSWORD);
        assertThat(login.statusCode()).as("a successful login redirects").isEqualTo(302);
        assertThat(login.headers().firstValue("Location").orElse("")).endsWith("/");

        // The session cookie in the cookie jar is now trusted: the home page knows the name.
        HttpResponse<String> home = get("/");
        assertThat(home.statusCode()).isEqualTo(200);
        assertThat(home.body())
                .contains(USERNAME)
                // ... and shows the shop links, not the SOC ones (the customer role).
                .contains("Giỏ hàng");

        // The gate itself: a CUSTOMER opening /admin/**.
        HttpResponse<String> forbidden = get("/admin/products");
        assertThat(forbidden.statusCode()).isEqualTo(403);
        assertThat(forbidden.body())
                .as("the 403 page must be our template, not the white-label error page")
                .contains("Bạn không có quyền truy cập trang này")
                .contains("403")
                // The page also states the truth about detection: the attempt becomes an
                // ACCESS_DENIED security event in Phase 5.
                .contains("ACCESS_DENIED");
    }

    @Test
    @DisplayName("a wrong password comes back to /login?error and the account stays anonymous")
    void wrongPasswordIsRefused() throws Exception {
        HttpResponse<String> login = login(USERNAME, "SaiMatKhau2026");

        assertThat(login.statusCode()).isEqualTo(302);
        assertThat(login.headers().firstValue("Location").orElse("")).endsWith("/login?error");

        // Still anonymous: /cart (a customer URL) must send this browser to the login page.
        assertThat(get("/cart").statusCode()).isEqualTo(302);
    }

    @Test
    @DisplayName("logout needs the CSRF token, ends the session and really closes access")
    void logoutEndsTheSession() throws Exception {
        assertThat(login(USERNAME, PASSWORD).statusCode()).isEqualTo(302);

        // The token has to come from a page that belongs to the logged-in session - the nav
        // bar's logout form (th:action) is the only place it appears.
        String token = csrfTokenFrom(get("/").body());

        // Without the token the request must be refused, exactly like a forged <img> would be.
        HttpResponse<String> withoutToken = postForm("/logout", form("_csrf", "khong-hop-le"));
        assertThat(withoutToken.statusCode()).isEqualTo(403);

        HttpResponse<String> logout = postForm("/logout", form("_csrf", token));
        assertThat(logout.statusCode()).isEqualTo(302);
        assertThat(logout.headers().firstValue("Location").orElse("")).endsWith("/login?logout");

        // The session is gone: the shop URL asks for a login again.
        assertThat(get("/cart").statusCode()).isEqualTo(302);
    }

    // ---------------------------------------------------------------- helpers

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    /** A GET like a browser would send it: it asks for HTML and never follows redirects. */
    private HttpResponse<String> get(String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url(path)))
                .header("Accept", "text/html,application/xhtml+xml")
                .GET()
                .build();
        return browser.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private HttpResponse<String> postForm(String path, String body) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url(path)))
                .header("Accept", "text/html,application/xhtml+xml")
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
        return browser.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    /**
     * The exact sequence a browser performs when somebody presses "Đăng nhập": load the form
     * (which also starts the session and hands out the CSRF token), then post the credentials
     * together with that token.
     */
    private HttpResponse<String> login(String username, String password) throws Exception {
        String token = csrfTokenFrom(get("/login").body());
        return postForm("/login",
                form("username", username, "password", password, "_csrf", token));
    }

    /** Reads the CSRF token out of the hidden field Thymeleaf rendered. */
    private String csrfTokenFrom(String html) {
        Matcher input = CSRF_INPUT.matcher(html);
        assertThat(input.find())
                .as("the page must contain the hidden _csrf field (Thymeleaf + Spring Security)")
                .isTrue();
        Matcher value = VALUE_ATTRIBUTE.matcher(input.group());
        assertThat(value.find()).as("the _csrf field must carry a value").isTrue();
        return value.group(1);
    }

    /** Builds an application/x-www-form-urlencoded body from alternating name/value pairs. */
    private static String form(String... namesAndValues) {
        StringBuilder body = new StringBuilder();
        for (int i = 0; i < namesAndValues.length; i += 2) {
            if (body.length() > 0) {
                body.append('&');
            }
            body.append(URLEncoder.encode(namesAndValues[i], StandardCharsets.UTF_8))
                    .append('=')
                    .append(URLEncoder.encode(namesAndValues[i + 1], StandardCharsets.UTF_8));
        }
        return body.toString();
    }
}
