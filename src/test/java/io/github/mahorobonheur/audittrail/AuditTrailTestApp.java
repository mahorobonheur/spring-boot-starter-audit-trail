package io.github.mahorobonheur.audittrail;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Minimal Spring Boot application used exclusively by integration tests.
 *
 * <p>Entity resolution mirrors a real consumer application: {@code TestUser} is
 * found through default scanning of this application's package, and the starter
 * contributes {@code model.AuditLog} additively via
 * {@code @AutoConfigurationPackage} in {@code AuditTrailAutoConfiguration} —
 * no {@code @EntityScan} is required anywhere.
 *
 * <p>{@code @EnableJpaRepositories} is scoped to the {@code integration} package
 * for {@code TestUserRepository}; the starter's {@code AuditLogRepository} is a
 * programmatically registered bean, independent of repository scanning.
 *
 * @author Bonheur Mahoro
 */
@SpringBootApplication
@EnableJpaRepositories(basePackages = "io.github.mahorobonheur.audittrail.integration")
public class AuditTrailTestApp {
}
