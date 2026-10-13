package com.yak.minisoc.config;

import com.yak.minisoc.security.AppRoles;
import com.yak.minisoc.service.AuthService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Creates the first administrator, once, from environment variables.
 *
 * <h3>The problem this solves</h3>
 * <p>Phase 2 deliberately seeded <strong>no user</strong>: a seeded account means a
 * documented default credential somewhere in the repository, which is how "admin/admin"
 * survives into production in real projects. But then who creates the first administrator?
 * Chicken and egg: creating an account is an administration action, and there is no
 * administrator yet.</p>
 *
 * <p>The answer is explicit configuration at start-up: if the environment provides
 * {@code APP_BOOTSTRAP_ADMIN_USERNAME} and {@code APP_BOOTSTRAP_ADMIN_PASSWORD}, and no
 * account with that username exists, the account is created. No secret in the repository,
 * no default password, and the whole thing can be switched off by simply not setting the
 * variables.</p>
 *
 * <h3>Why an {@link ApplicationRunner} and not a {@code CommandLineRunner} / a
 * {@code @PostConstruct}</h3>
 * <ul>
 *   <li>It runs <strong>after</strong> the container is fully started - so Flyway has
 *       already applied the migrations, which is what makes the {@code roles} rows exist
 *       and {@code AuthService} usable at all.</li>
 *   <li>{@code @PostConstruct} would run in the middle of bean creation, where a
 *       {@code @Transactional} service call does not behave as it does everywhere else
 *       (no proxy yet) - a classic and confusing bug.</li>
 *   <li>Both runner interfaces are equivalent; {@code ApplicationRunner} passes the
 *       arguments as {@code ApplicationArguments} instead of a raw array.</li>
 * </ul>
 *
 * <h3>Why {@code @Profile("!test")}</h3>
 * <p>The test profile must never create accounts implicitly: a test that expects an empty
 * {@code users} table would fail depending on the variables of whoever runs the build.
 * Tests create exactly the accounts they need (see {@code AuthServiceTest}). The bootstrap
 * has its own test that calls the method directly.</p>
 *
 * <h3>What is logged, and what is not</h3>
 * <p>Logging that an account was created is useful. Logging the password would defeat the
 * entire purpose of this phase, so it is never touched - the string goes straight from the
 * configuration property into the password encoder.</p>
 */
@Configuration
@Profile("!test")
@EnableConfigurationProperties(BootstrapAdminProperties.class)
public class AdminBootstrapConfig {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrapConfig.class);

    /**
     * Creates the administrator described by {@link BootstrapAdminProperties}, if configured
     * and not already present. Idempotent: restarting the application changes nothing.
     */
    @Bean
    public ApplicationRunner bootstrapAdministrator(BootstrapAdminProperties properties,
                                                    AuthService authService) {
        return args -> {
            if (!properties.isConfigured()) {
                log.info("No bootstrap administrator configured (set APP_BOOTSTRAP_ADMIN_USERNAME "
                        + "and APP_BOOTSTRAP_ADMIN_PASSWORD to create one). Registration is "
                        + "available at /register.");
                return;
            }

            String username = properties.getUsername();
            if (authService.accountExists(username)) {
                // The normal case on every start after the first one: nothing to do.
                log.info("Bootstrap administrator '{}' already exists - nothing to create.", username);
                return;
            }

            authService.createAccount(username, properties.getEmail(), properties.getPassword(),
                    properties.getFullName(), java.util.List.of(AppRoles.ADMIN));

            // Expected, copy-pasteable, and safe: it says which account exists, never the
            // password. The operator already knows the password - they set the variable.
            log.info("================================================================");
            log.info(" Bootstrap administrator created: username = {}", username);
            log.info(" Password: the value of APP_BOOTSTRAP_ADMIN_PASSWORD (not logged).");
            log.info(" => Log in at http://localhost:8080/login and enable the admin area.");
            log.info("================================================================");
        };
    }
}
