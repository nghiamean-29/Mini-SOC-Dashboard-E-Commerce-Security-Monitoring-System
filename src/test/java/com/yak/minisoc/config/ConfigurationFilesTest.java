package com.yak.minisoc.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.env.MockEnvironment;

/**
 * Verifies the application configuration files themselves.
 *
 * <p>A typo in {@code application.yml} - a missing colon, a wrong indentation, a
 * literal password - is normally discovered only when the application fails to
 * start, which is the worst possible moment to notice. These tests parse the same
 * files Spring Boot reads and fail immediately (and on every build) instead.</p>
 *
 * <p>No Spring context and no database are needed: {@link YamlPropertySourceLoader}
 * is the very class Spring Boot uses internally to turn YAML into properties.</p>
 */
@DisplayName("application*.yml - configuration files")
class ConfigurationFilesTest {

    private static final String BASE_CONFIG = "application.yml";
    private static final String DEV_CONFIG = "application-dev.yml";
    private static final String PROD_CONFIG = "application-prod.yml";

    /** All configuration files that must always stay valid YAML. */
    private static final List<String> ALL_CONFIG_FILES = List.of(BASE_CONFIG, DEV_CONFIG, PROD_CONFIG);

    /**
     * Property-name fragments that indicate a secret. If a key contains one of
     * these, its value must come from the environment (NFR-SEC-02).
     */
    private static final List<String> SECRET_NAME_FRAGMENTS =
            List.of("password", "secret", "token", "credential", "api-key", "apikey", "private-key");

    // ------------------------------------------------------------------ tests

    @Test
    @DisplayName("every configuration file is valid YAML and not empty")
    void everyConfigurationFileIsValidYaml() throws IOException {
        for (String fileName : ALL_CONFIG_FILES) {
            PropertySource<?> source = load(fileName);

            assertThat(source.getPropertyNames())
                    .as("%s must declare at least one property", fileName)
                    .isNotNull()
                    .isNotEmpty();
        }
    }

    @Test
    @DisplayName("declares the application name")
    void declaresApplicationName() throws IOException {
        assertThat(rawValue(load(BASE_CONFIG), "spring.application.name")).isEqualTo("mini-soc-dashboard");
    }

    @Test
    @DisplayName("reads database credentials from environment variables")
    void credentialsComeFromEnvironmentVariables() throws IOException {
        PropertySource<?> base = load(BASE_CONFIG);

        assertThat(rawValue(base, "spring.datasource.url")).contains("${DB_URL");
        assertThat(rawValue(base, "spring.datasource.username")).contains("${DB_USERNAME");
        assertThat(rawValue(base, "spring.datasource.password")).contains("${DB_PASSWORD");
    }

    @Test
    @DisplayName("placeholder syntax resolves from the environment, with a default when absent")
    void placeholderResolutionUsesEnvironmentValues() throws IOException {
        PropertySource<?> base = load(BASE_CONFIG);

        MockEnvironment environment = new MockEnvironment()
                .withProperty("DB_URL", "jdbc:postgresql://db.internal:5432/minisoc_db")
                .withProperty("DB_USERNAME", "soc_app")
                .withProperty("DB_PASSWORD", "s3cr3t-from-environment");

        assertThat(environment.resolvePlaceholders(rawValue(base, "spring.datasource.url")))
                .isEqualTo("jdbc:postgresql://db.internal:5432/minisoc_db");
        assertThat(environment.resolvePlaceholders(rawValue(base, "spring.datasource.username")))
                .isEqualTo("soc_app");
        assertThat(environment.resolvePlaceholders(rawValue(base, "spring.datasource.password")))
                .isEqualTo("s3cr3t-from-environment");

        // An empty default (the ":" after the variable name) means: no password
        // is assumed when the environment does not provide one.
        assertThat(new MockEnvironment().resolvePlaceholders("${DB_PASSWORD:}")).isEmpty();
    }

    @Test
    @DisplayName("contains no literal secret: every secret-like key uses a placeholder")
    void noPlaintextSecretIsCommitted() throws IOException {
        for (String fileName : ALL_CONFIG_FILES) {
            PropertySource<?> source = load(fileName);

            for (String name : source.getPropertyNames()) {
                String lowerCaseName = name.toLowerCase(Locale.ROOT);
                boolean looksLikeSecret =
                        SECRET_NAME_FRAGMENTS.stream().anyMatch(lowerCaseName::contains);

                if (!looksLikeSecret) {
                    continue;
                }

                String value = rawValue(source, name);
                boolean comesFromEnvironment = value.contains("${") || value.isEmpty();

                assertThat(comesFromEnvironment)
                        .as("%s -> '%s' must reference an environment variable, never a literal value",
                                fileName, name)
                        .isTrue();
            }
        }
    }

    @Test
    @DisplayName("the production profile refuses to start without environment variables")
    void productionProfileHasNoCredentialDefaults() throws IOException {
        PropertySource<?> prod = load(PROD_CONFIG);

        assertThat(rawValue(prod, "spring.datasource.url"))
                .as("no default value: a missing DB_URL must abort startup")
                .isEqualTo("${DB_URL}");
        assertThat(rawValue(prod, "spring.datasource.username")).isEqualTo("${DB_USERNAME}");
        assertThat(rawValue(prod, "spring.datasource.password")).isEqualTo("${DB_PASSWORD}");
    }

    @Test
    @DisplayName("the Attack Simulator is off by default, on in dev, off in prod")
    void simulatorSwitchIsProfileControlled() throws IOException {
        assertThat(rawValue(load(BASE_CONFIG), "app.simulator.enabled"))
                .as("secure default: the simulator is opt-in")
                .isEqualTo("${APP_SIMULATOR_ENABLED:false}");

        assertThat(load(DEV_CONFIG).getProperty("app.simulator.enabled"))
                .as("available for demos while developing")
                .isEqualTo(Boolean.TRUE);

        assertThat(load(PROD_CONFIG).getProperty("app.simulator.enabled"))
                .as("never exposed in production")
                .isEqualTo(Boolean.FALSE);
    }

    @Test
    @DisplayName("Flyway owns the schema and timestamps are stored in UTC")
    void schemaAndTimeZoneSettingsAreExplicit() throws IOException {
        PropertySource<?> base = load(BASE_CONFIG);

        assertThat(rawValue(base, "spring.jpa.hibernate.ddl-auto"))
                .as("Hibernate must only validate the schema that Flyway created")
                .isEqualTo("validate");
        assertThat(rawValue(base, "spring.flyway.locations")).isEqualTo("classpath:db/migration");
        assertThat(rawValue(base, "spring.jpa.properties.hibernate.jdbc.time_zone"))
                .as("UTC everywhere in storage (NFR-DATA-01)")
                .isEqualTo("UTC");
        assertThat(rawValue(base, "spring.jpa.open-in-view"))
                .as("lazy loading outside a transaction must fail loudly")
                .isEqualTo("false");
    }

    // ---------------------------------------------------------------- helpers

    /**
     * Loads a YAML file from the classpath the same way Spring Boot does and
     * returns its first property source.
     */
    private PropertySource<?> load(String fileName) throws IOException {
        List<PropertySource<?>> sources = new YamlPropertySourceLoader()
                .load(fileName, new ClassPathResource(fileName));

        assertThat(sources)
                .as("%s must be present in src/main/resources and be valid YAML", fileName)
                .isNotEmpty();

        // Java 21 SequencedCollection: List#getFirst() replaces get(0).
        return sources.getFirst();
    }

    /** Reads a property as its raw, unresolved text (placeholders are NOT expanded). */
    private String rawValue(PropertySource<?> source, String propertyName) {
        Object value = source.getProperty(propertyName);

        assertThat(value)
                .as("property '%s' should be declared", propertyName)
                .isNotNull();

        return String.valueOf(value);
    }
}
