package com.yak.minisoc;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Bootstrap smoke test: verifies the <em>contract</em> of the main class without
 * starting a Spring context (so it also runs on a machine where PostgreSQL is not
 * up yet).
 *
 * <p>These assertions look trivial, but they protect real behaviour:</p>
 * <ul>
 *   <li>without {@code @SpringBootApplication} the application would not start;</li>
 *   <li>if somebody added {@code scanBasePackages = "com.yak.minisoc.something"},
 *       the hidden packages {@code service}, {@code detection}, ... would silently
 *       produce "no bean found" errors at runtime;</li>
 *   <li>if {@code main} were not {@code public static void main(String[])},
 *       {@code java -jar} would fail with "no main manifest attribute".</li>
 * </ul>
 */
@DisplayName("MiniSocApplication - bootstrap class")
class MiniSocApplicationTest {

    @Test
    @DisplayName("is annotated with @SpringBootApplication")
    void isAnnotatedWithSpringBootApplication() {
        assertThat(MiniSocApplication.class.isAnnotationPresent(SpringBootApplication.class))
                .as("@SpringBootApplication is required to enable auto-configuration and component scanning")
                .isTrue();
    }

    @Test
    @DisplayName("scans components from the root package, without overriding the base package")
    void scansFromRootPackage() {
        SpringBootApplication annotation = MiniSocApplication.class.getAnnotation(SpringBootApplication.class);

        assertThat(MiniSocApplication.class.getPackageName()).isEqualTo("com.yak.minisoc");
        assertThat(annotation.scanBasePackages())
                .as("no explicit base package: every com.yak.minisoc.* package is scanned")
                .isEmpty();
        assertThat(annotation.scanBasePackageClasses())
                .as("no explicit base package class: scanning starts at com.yak.minisoc")
                .isEmpty();
    }

    @Test
    @DisplayName("exposes a public static void main(String[]) method")
    void hasMainMethod() throws NoSuchMethodException {
        Method main = MiniSocApplication.class.getMethod("main", String[].class);

        assertThat(Modifier.isPublic(main.getModifiers())).isTrue();
        assertThat(Modifier.isStatic(main.getModifiers())).isTrue();
        assertThat(main.getReturnType()).isEqualTo(void.class);
    }
}
