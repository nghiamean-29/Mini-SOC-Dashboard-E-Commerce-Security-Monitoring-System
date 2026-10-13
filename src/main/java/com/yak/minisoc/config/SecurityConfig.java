package com.yak.minisoc.config;

import com.yak.minisoc.security.AppRoles;
import jakarta.servlet.DispatcherType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.session.SessionRegistryImpl;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.security.web.session.HttpSessionEventPublisher;

/**
 * The security configuration of the application: who may log in, and who may see what.
 *
 * <p>Everything security-related that is <em>infrastructure</em> is defined here, in Java,
 * so it is reviewable, testable and searchable (unlike XML or a pile of properties).</p>
 *
 * <h3>Three decisions worth reading about</h3>
 * <ol>
 *   <li><strong>The rules fail closed.</strong> {@code anyRequest().authenticated()} is the
 *       last line: a URL somebody adds later and forgets to classify is protected by
 *       default, not public by accident. The allowlist at the top is short and explicit.</li>
 *   <li><strong>Authorization on two levels.</strong> This class decides per URL pattern
 *       (the first line of defence, visible in one screen), and the controllers annotate
 *       sensitive actions with {@code @PreAuthorize} (defence in depth: a URL rule that
 *       somebody later relaxes does not silently open the operation).</li>
 *   <li><strong>Sessions are managed, not assumed.</strong> Session fixation protection is
 *       explicit, at most one active session per account is allowed, and the
 *       {@link HttpSessionEventPublisher} keeps the session registry accurate.</li>
 * </ol>
 *
 * <p><strong>Not decided here:</strong> what happens to a user who is already logged in but
 * lacks the role. Spring Security answers 403 (via the servlet error page
 * {@code templates/error/403.html}). Phase 5 attaches a listener to the
 * {@code AuthorizationDeniedEvent} that Spring Security publishes, records an
 * {@code ACCESS_DENIED} security event, and Phase 6 turns repeated attempts into an alert -
 * the two-layer answer (block <em>and</em> detect) described in the threat model.</p>
 */
@Configuration
@EnableWebSecurity
// Enables @PreAuthorize/@PostAuthorize on beans (defence in depth, see class javadoc).
@EnableMethodSecurity
public class SecurityConfig {

    /**
     * Content-Security-Policy sent with every HTML response.
     *
     * <p>{@code default-src 'self'} means: the browser may only load scripts, styles,
     * images and fonts from our own origin. That single header is the strongest available
     * second line of defence against XSS (T8 in the threat model): even if an attacker
     * manages to inject markup, the injected script has nothing it is allowed to load or
     * execute - which is why there is no {@code 'unsafe-inline'} for scripts here.</p>
     *
     * <p>{@code frame-ancestors 'none'} stops clickjacking (our pages cannot be embedded in
     * a foreign frame) and {@code form-action 'self'} stops a form on our page from posting
     * the credentials to somebody else's server.</p>
     *
     * <p>When Phase 10 adds the WebSocket dashboard, {@code connect-src} has to be added -
     * the one place where this policy will change, and it will change here, in one line.</p>
     */
    private static final String CONTENT_SECURITY_POLICY = String.join("; ",
            "default-src 'self'",
            "script-src 'self'",
            "style-src 'self'",
            "img-src 'self' data:",
            "font-src 'self'",
            "connect-src 'self'",
            "object-src 'none'",
            "base-uri 'self'",
            "form-action 'self'",
            "frame-ancestors 'none'");

    /**
     * How passwords are hashed.
     *
     * <p><strong>BCrypt, strength 12.</strong> BCrypt generates a random salt per password
     * (two users with the same password get different hashes) and it is deliberately slow,
     * so guessing is expensive even after a database leak. Strength 12 is about 0.25-0.5 s
     * per hash on a laptop - imperceptible at login, brutal for an attacker who wants to
     * try a million candidates.</p>
     *
     * <p>Why not SHA-256/MD5 (with or without salt): they are designed to be <em>fast</em>,
     * which is the opposite of what a password hash needs. A GPU tries billions of
     * SHA-256 candidates per second. This is the difference between OWASP-compliant and
     * "we hashed it, so it is safe" (A02 in the OWASP Top 10 list, see SDD section 11.5).</p>
     *
     * <p>Because the encoder is a bean, {@code DaoAuthenticationProvider} and the
     * registration service share the same instance - there is no way to register with one
     * algorithm and log in with another.</p>
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    /**
     * The whole HTTP security policy: which URLs are public, how login/logout work, how
     * sessions are protected, which headers every response carries.
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // ---------------------------------------------------- 1. authorization
                .authorizeHttpRequests(requests -> requests
                        // The ERROR dispatch is what renders templates/error/403.html after
                        // a denial. Without this line the 403 page itself would be
                        // rejected, and the user would see a blank response instead.
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()

                        // Public pages: the shop front, the two authentication screens and
                        // the error page. Everything else needs a session.
                        .requestMatchers("/", "/login", "/register", "/error").permitAll()

                        // Static assets. They contain nothing private (CSS, a favicon,
                        // later the vendored STOMP client), and requiring a login for a
                        // stylesheet would make the login page itself unstyled.
                        .requestMatchers("/css/**", "/js/**", "/images/**", "/vendor/**",
                                "/favicon.svg", "/favicon.ico").permitAll()

                        // Actuator: operational data (health, build info) is for operators.
                        // SECURITY NOTE: in Phase 1 this endpoint was reachable while the
                        // application had no authentication at all; from now on it answers
                        // 302 (to /login) for anonymous callers. That is intentional and
                        // more correct - health of the database is not public knowledge.
                        .requestMatchers("/actuator/health", "/actuator/info").hasRole(AppRoles.ADMIN)

                        // The two privileged areas (see the matrix in SDD section 11.2).
                        .requestMatchers("/admin/**").hasRole(AppRoles.ADMIN)
                        .requestMatchers("/soc/**").hasAnyRole(AppRoles.ADMIN, AppRoles.SOC_ANALYST)

                        // The shop side. An administrator may also shop (a customer with an
                        // admin account); a SOC analyst may not - buying has nothing to do
                        // with monitoring, and keeping the roles separate is the point of
                        // having three of them.
                        .requestMatchers("/cart/**", "/orders/**")
                        .hasAnyRole(AppRoles.CUSTOMER, AppRoles.ADMIN)

                        // Fail closed: anything not classified above requires a login.
                        .anyRequest().authenticated())

                // ------------------------------------------------------------ 2. login
                .formLogin(form -> form
                        // Our own page instead of the generated one: it carries the
                        // application's layout and the neutral error message.
                        .loginPage("/login")
                        // The URL the form posts to (Spring Security's AuthenticationFilter).
                        .loginProcessingUrl("/login")
                        // false = after login, return to the page the user originally asked
                        // for (the saved request) instead of always going to '/'. Note the
                        // parameter name: defaultSuccessUrl("/", true) would force '/'.
                        .defaultSuccessUrl("/", false)
                        // One single message for "wrong password", "unknown user" and
                        // "disabled account": the login form must not reveal which usernames
                        // exist, and a locked account must not confirm it either.
                        .failureUrl("/login?error")
                        .permitAll())

                // ----------------------------------------------------------- 3. logout
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        // POST-only (the CSRF token is required): a GET logout could be
                        // triggered by an <img src="/logout"> on any website - a real, if
                        // petty, denial of service. Spring Security 7 enforces this default.
                        .logoutSuccessUrl("/login?logout")
                        .invalidateHttpSession(true)
                        .clearAuthentication(true)
                        .deleteCookies("JSESSIONID"))

                // ---------------------------------------------------------- 4. sessions
                .sessionManagement(session -> session
                        // Session fixation: after a successful login the session id is
                        // changed, so an id planted by an attacker before login becomes
                        // useless. This is Spring Security's default; stating it explicitly
                        // documents the protection instead of relying on a default that a
                        // future refactor could switch off.
                        .sessionFixation(fixation -> fixation.changeSessionId())
                        // One active session per account. The second login wins and the old
                        // session is expired (maxSessionsPreventsLogin(false)) - realistic
                        // for a demo recorded on two browsers, and it means a stolen session
                        // cannot be used silently next to the real user's session.
                        .maximumSessions(1)
                        .maxSessionsPreventsLogin(false)
                        // Where to send the browser whose session was pushed out.
                        .expiredUrl("/login?expired"))

                // ----------------------------------------------------------- 5. headers
                .headers(headers -> headers
                        // No embedding at all (clickjacking). frame-ancestors in the CSP
                        // covers modern browsers, this header covers the rest.
                        .frameOptions(frame -> frame.deny())
                        // Send the origin (not the full URL) when navigating away: URLs of a
                        // SOC contain ids and search terms that should not leak to third
                        // party sites through the Referer header.
                        .referrerPolicy(referrer -> referrer
                                .policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.SAME_ORIGIN))
                        .contentSecurityPolicy(csp -> csp
                                .policyDirectives(CONTENT_SECURITY_POLICY)));

        // CSRF stays ENABLED (the Spring Security default). Every state-changing request
        // must carry the token, and because Thymeleaf forms use th:action="@{...}", the
        // hidden csrf field is inserted automatically - there is no "remember to add the
        // token" step for the developer to forget.
        return http.build();
    }

    /**
     * Keeps {@link SessionRegistry} informed when an HTTP session is destroyed.
     *
     * <p>The session registry is what makes "maximum 1 session" work: it holds one
     * {@code SessionInformation} per logged-in session. It learns about a session ending
     * from a {@code SessionDestroyedEvent} - and that event only exists because this
     * listener translates the servlet container's {@code HttpSessionListener} callbacks
     * into Spring application events. Without this bean, logging out (or a session timeout)
     * would leave a stale entry behind, the account would look permanently "already in use",
     * and - worse - the registry would grow for the lifetime of the JVM.</p>
     */
    @Bean
    public HttpSessionEventPublisher httpSessionEventPublisher() {
        return new HttpSessionEventPublisher();
    }

    /**
     * The registry itself, exposed as a bean so it receives the session events above and so
     * the analyser screens of Phase 9 can ask "who is logged in right now?".
     *
     * <p>Declaring it explicitly (instead of letting the session-management DSL create a
     * private instance) is what connects it to the application context - a bean that
     * implements {@code ApplicationListener} is automatically subscribed to its events.</p>
     */
    @Bean
    public SessionRegistry sessionRegistry() {
        return new SessionRegistryImpl();
    }
}
