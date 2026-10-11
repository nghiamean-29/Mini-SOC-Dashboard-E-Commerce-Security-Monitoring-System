package com.yak.minisoc;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point of the Mini SOC Dashboard application.
 *
 * <p><strong>What this class is:</strong> the {@code main} class that boots the
 * Spring container. It contains no business logic on purpose - its only job is
 * to tell Spring where to start.</p>
 *
 * <p><strong>Why it lives in the root package</strong> {@code com.yak.minisoc}:
 * {@code @SpringBootApplication} performs component scanning starting from its
 * own package. Placing it at the root means every package below
 * ({@code controller}, {@code service}, {@code detection}, ...) is scanned
 * automatically, and no package can be forgotten by accident.</p>
 *
 * <p><strong>What the single annotation does:</strong> {@code @SpringBootApplication}
 * is a shortcut for three annotations:</p>
 * <ul>
 *   <li>{@code @SpringBootConfiguration} - marks this as a configuration class,
 *       so the container can read {@code @Bean} definitions from it.</li>
 *   <li>{@code @EnableAutoConfiguration} - "intelligent defaults": Spring Boot
 *       inspects the classpath and configures what it finds (Tomcat because we
 *       have spring-boot-starter-webmvc, Hibernate because we have
 *       spring-boot-starter-data-jpa, Flyway because we have
 *       spring-boot-starter-flyway, ...). Everything it decides is driven by
 *       {@code src/main/resources/application.yml}.</li>
 *   <li>{@code @ComponentScan} - discovers classes annotated with
 *       {@code @Component}/{@code @Service}/{@code @Repository}/{@code @Controller}
 *       and registers them as beans.</li>
 * </ul>
 *
 * <p><strong>Why no other annotations yet:</strong> things such as
 * {@code @EnableScheduling} (background jobs), {@code @EnableAsync} (event
 * pipeline) and {@code @EnableTransactionManagement} are added in the phase that
 * actually needs them, together with the code that justifies them. An annotation
 * without a consumer only hides complexity instead of explaining it.</p>
 */
@SpringBootApplication
public class MiniSocApplication {

    /**
     * Starts the whole application: creates the Spring container (ApplicationContext),
     * runs Flyway migrations against PostgreSQL, starts the embedded Tomcat on
     * {@code server.port} (8080 by default) and then keeps running until the JVM
     * is stopped.
     *
     * @param args command line arguments, for example
     *             {@code --spring.profiles.active=dev}
     */
    public static void main(String[] args) {
        SpringApplication.run(MiniSocApplication.class, args);
    }
}
