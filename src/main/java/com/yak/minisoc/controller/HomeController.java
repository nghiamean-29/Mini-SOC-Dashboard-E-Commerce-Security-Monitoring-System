package com.yak.minisoc.controller;

import com.yak.minisoc.security.AppRoles;
import com.yak.minisoc.security.AuthenticatedUser;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * The public shop front (one page for now - the catalogue arrives in Phase 4).
 *
 * <p><strong>Why this controller is so small:</strong> it has no business logic to express
 * yet. What it does already show is the pattern used by every later controller: read what
 * the view needs, put it in the model, name a template. No repository, no entity, no rule
 * lives here - see the layer contract in the {@code controller} package documentation.</p>
 *
 * <h3>Why the navigation flags are computed here instead of using {@code sec:authorize}</h3>
 * <p>The usual way to hide links by role in Thymeleaf is the Spring Security dialect
 * ({@code <div sec:authorize="hasRole('ADMIN')">}), which requires an extra dependency
 * ({@code thymeleaf-extras-springsecurity*}). This project deliberately avoids it and passes
 * three booleans to the model instead:</p>
 * <ul>
 *   <li>the template stays plain Thymeleaf - one less version-sensitive dependency to keep
 *       in step with Spring Security 7;</li>
 *   <li>the rule "who sees which link" is written in Java, where it is unit-testable and
 *       greppable, next to the very same constants the filter chain uses
 *       ({@link AppRoles});</li>
 *   <li>it is honest about what it is: hiding a link is <em>cosmetic</em>. The real
 *       protection is the URL rule in {@code SecurityConfig} plus {@code @PreAuthorize} on
 *       the action - a hidden link that still works when typed by hand would be a fake
 *       defence.</li>
 * </ul>
 */
@Controller
public class HomeController {

    /** The landing page. Everyone (including guests) may see it. */
    @GetMapping("/")
    public String home(Authentication authentication, Model model) {
        boolean loggedIn = isLoggedIn(authentication);
        boolean admin = hasRole(authentication, AppRoles.ADMIN);
        boolean analyst = hasRole(authentication, AppRoles.SOC_ANALYST);
        boolean customer = hasRole(authentication, AppRoles.CUSTOMER);

        model.addAttribute("loggedIn", loggedIn);
        model.addAttribute("username", loggedIn ? authentication.getName() : null);

        // Which roles this account has.
        model.addAttribute("isAdmin", admin);
        model.addAttribute("isAnalyst", analyst);
        model.addAttribute("isCustomer", customer);

        // Which groups of links to show. These three are computed HERE, in Java, instead
        // of being written as a boolean expression inside the template (for example
        // "${isCustomer or isAdmin}"): a rule in Java is testable, and the template stays
        // free of the null-in-expression trap that Thymeleaf/SpEL boolean operators have.
        model.addAttribute("showShopLinks", customer || admin);
        model.addAttribute("showSocLinks", analyst || admin);
        model.addAttribute("showAdminLinks", admin);

        return "index";
    }

    /**
     * True when somebody is really logged in.
     *
     * <p>Spring Security represents "not logged in" as an {@link AnonymousAuthenticationToken}
     * which is technically authenticated and has the role {@code ROLE_ANONYMOUS}. Checking
     * only {@code isAuthenticated()} would therefore treat every visitor as a logged-in user
     * and show the personal menu to guests - a mistake that also shows up in navigation and
     * in the word "anonymous" appearing as the username.</p>
     */
    private static boolean isLoggedIn(Authentication authentication) {
        return authentication != null
                && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken);
    }

    /**
     * Role check that works for both principal types.
     *
     * <p>Authorities suffice for the template flags, but using the same
     * {@code ROLE_}-prefixed names from {@link AppRoles} keeps this code consistent with
     * what the authorization rules check.</p>
     */
    private static boolean hasRole(Authentication authentication, String roleCode) {
        if (!isLoggedIn(authentication)) {
            return false;
        }
        String authority = AppRoles.authority(roleCode);
        return authentication.getAuthorities().stream()
                .anyMatch(granted -> granted.getAuthority().equals(authority));
    }

    /**
     * Convenience for the (future) pages that need the database id of the current user.
     *
     * <p>Placeholder of the pattern used from Phase 4 on: inject {@code Authentication},
     * take the principal, and be explicit about what happens when the principal is not the
     * expected type (for example when a test uses {@code @WithMockUser}, which installs a
     * Spring Security {@code User} without an id).</p>
     */
    protected static Long currentUserId(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof AuthenticatedUser user) {
            return user.getUserId();
        }
        return null;
    }
}
