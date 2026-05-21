package io.github.mahorobonheur.audittrail;

import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Minimal Spring Boot application used exclusively by integration tests.
 *
 * <p>Placing this class in the root {@code io.github.mahorobonheur.audittrail}
 * package means Spring Boot's auto-detection covers every sub-package, so:
 * <ul>
 *   <li>{@code model.AuditLog} and {@code integration.TestUser} are found as JPA entities</li>
 *   <li>{@code repository.AuditLogRepository} and {@code integration.TestUserRepository}
 *       are found as Spring Data repositories</li>
 *   <li>{@code config.AuditTrailAutoConfiguration} is loaded via the
 *       {@code AutoConfiguration.imports} file</li>
 * </ul>
 *
 * <p>This avoids {@code @EntityScan} and {@code @EnableJpaRepositories} and their
 * direct dependency on {@code spring-boot-autoconfigure} internals, making the
 * test setup stable across Spring Boot minor versions.
 *
 * @author Bonheur Mahoro
 */
@SpringBootApplication
public class AuditTrailTestApp {
}
