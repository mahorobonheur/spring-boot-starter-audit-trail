package io.github.mahorobonheur.audittrail;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

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
 * <p>Scans test entities and repositories; {@code AuditLog} is registered by the starter's
 * {@code @EntityScan} / {@code @EnableJpaRepositories} in {@code AuditTrailAutoConfiguration}.
 *
 * @author Bonheur Mahoro
 */
@SpringBootApplication
@EntityScan(basePackages = "io.github.mahorobonheur.audittrail.integration")
@EnableJpaRepositories(basePackages = "io.github.mahorobonheur.audittrail.integration")
public class AuditTrailTestApp {
}
