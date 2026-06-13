package io.github.mahorobonheur.audittrail.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.mahorobonheur.audittrail.controller.AuditTrailController;
import io.github.mahorobonheur.audittrail.engine.FieldDiffEngine;
import io.github.mahorobonheur.audittrail.listener.AuditTrailEntityListener;
import io.github.mahorobonheur.audittrail.repository.AuditLogRepository;
import io.github.mahorobonheur.audittrail.security.AuditSecurityResolver;
import io.github.mahorobonheur.audittrail.writer.AsyncAuditLogWriter;
import io.github.mahorobonheur.audittrail.writer.AuditLogWriter;
import io.github.mahorobonheur.audittrail.writer.DatabaseAuditLogWriter;
import io.github.mahorobonheur.audittrail.writer.LogAuditLogWriter;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.boot.model.naming.PhysicalNamingStrategy;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory;
import org.springframework.orm.jpa.SharedEntityManagerCreator;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.web.bind.annotation.RestController;

/**
 * Spring Boot auto-configuration for the audit trail starter.
 *
 * <p>This configuration is activated automatically when the starter JAR is on
 * the classpath and {@code audit-trail.enabled} is {@code true} (the default).
 *
 * <p>All beans are conditional — if you declare your own bean of the same type,
 * the starter's default bean will not be created, giving you full control to
 * override any component.
 *
 * <h2>What gets registered</h2>
 * <ul>
 *   <li>{@link FieldDiffEngine} — compares entity snapshots</li>
 *   <li>{@link AuditSecurityResolver} — resolves the current user</li>
 *   <li>{@link AuditTrailEntityListener} — JPA lifecycle hook</li>
 *   <li>{@link DatabaseAuditLogWriter} — default write-to-DB strategy</li>
 *   <li>{@link AuditTrailController} — REST query endpoint (if enabled)</li>
 * </ul>
 *
 * @author Bonheur Mahoro
 */
// The AuditLog ENTITY is registered with Hibernate directly by AuditTrailMappingContributor
// (META-INF/services SPI) and the REPOSITORY is built programmatically below — deliberately
// NOT via @EntityScan / @EnableJpaRepositories, which would override the host application's
// own entity/repository scanning and break it.
@AutoConfiguration
@EnableAsync
@EnableConfigurationProperties(AuditTrailProperties.class)
@ConditionalOnProperty(prefix = "audit-trail", name = "enabled", havingValue = "true", matchIfMissing = true)
public class AuditTrailAutoConfiguration {

    /**
     * The audit log repository, built programmatically so the starter never has to
     * declare {@code @EnableJpaRepositories} (which would suppress Spring Boot's
     * repository auto-scanning in the host application).
     *
     * <p>The shared {@link EntityManager} proxy is transaction-aware, so the
     * repository participates in whatever transaction is active at call time.
     *
     * <p>The bean is deliberately named {@code auditTrailAuditLogRepository} rather
     * than the default {@code auditLogRepository}: Spring Data names scanned
     * repository beans by decapitalised simple interface name, so a host application
     * with its own {@code AuditLogRepository} interface would otherwise collide.
     */
    @Bean(name = "auditTrailAuditLogRepository")
    @ConditionalOnMissingBean
    public AuditLogRepository auditTrailAuditLogRepository(EntityManagerFactory entityManagerFactory) {
        EntityManager entityManager = SharedEntityManagerCreator.createSharedEntityManager(entityManagerFactory);
        return new JpaRepositoryFactory(entityManager).getRepository(AuditLogRepository.class);
    }

    /**
     * The core diff engine. Stateless and thread-safe — singleton scope is appropriate.
     */
    @Bean
    @ConditionalOnMissingBean
    public FieldDiffEngine fieldDiffEngine() {
        return new FieldDiffEngine();
    }

    /**
     * Resolves the authenticated username from Spring Security context.
     */
    @Bean
    @ConditionalOnMissingBean
    public AuditSecurityResolver auditSecurityResolver() {
        return new AuditSecurityResolver();
    }

    /**
     * Exposes the Spring {@link org.springframework.context.ApplicationContext} via a
     * static accessor so that non-Spring-managed objects (specifically the JPA
     * {@link io.github.mahorobonheur.audittrail.listener.AuditTrailEntityListener})
     * can look up beans at runtime.
     */
    @Bean
    @ConditionalOnMissingBean
    public SpringContextHolder springContextHolder() {
        return new SpringContextHolder();
    }

    /**
     * The JPA entity listener that intercepts lifecycle events.
     * Its Spring dependencies are resolved lazily via {@link SpringContextHolder}
     * because JPA instantiates EntityListeners outside of Spring's container.
     */
    @Bean
    @ConditionalOnMissingBean
    public AuditTrailEntityListener auditTrailEntityListener() {
        return new AuditTrailEntityListener();
    }

    /**
     * Jackson 2 {@link ObjectMapper} for serialising field diffs. Spring Boot 4 defaults
     * to Jackson 3 ({@code JsonMapper}) and does not register this bean automatically.
     */
    @Bean
    @ConditionalOnMissingBean
    public ObjectMapper auditTrailObjectMapper() {
        return new ObjectMapper();
    }

    /**
     * Applies {@code audit-trail.table-name} to the {@link AuditLog} entity table mapping.
     */
    @Bean
    @ConditionalOnMissingBean(PhysicalNamingStrategy.class)
    public PhysicalNamingStrategy auditTrailPhysicalNamingStrategy(AuditTrailProperties properties) {
        return new AuditTrailTableNamingStrategy(properties.getTableName());
    }

    /**
     * Synchronous write strategy ({@code audit-trail.async=false}).
     *
     * <p>Note: the backend writer is deliberately <em>not</em> exposed as its own
     * bean — being an {@link AuditLogWriter}, it would itself satisfy
     * {@code @ConditionalOnMissingBean(AuditLogWriter.class)} and prevent these
     * strategy beans from ever being created. Override the write strategy by
     * declaring your own {@link AuditLogWriter} bean.
     */
    @Bean
    @ConditionalOnMissingBean(AuditLogWriter.class)
    @ConditionalOnProperty(prefix = "audit-trail", name = "async", havingValue = "false")
    public AuditLogWriter syncAuditLogWriter(AuditTrailProperties properties,
                                             AuditLogRepository repository,
                                             ObjectMapper objectMapper,
                                             ObjectProvider<PlatformTransactionManager> transactionManager) {
        return selectBackend(properties, repository, objectMapper, transactionManager);
    }

    /** Asynchronous write strategy ({@code audit-trail.async=true}, the default). */
    @Bean
    @ConditionalOnMissingBean(AuditLogWriter.class)
    @ConditionalOnProperty(prefix = "audit-trail", name = "async", havingValue = "true", matchIfMissing = true)
    public AuditLogWriter asyncAuditLogWriter(AuditTrailProperties properties,
                                              AuditLogRepository repository,
                                              ObjectMapper objectMapper,
                                              ObjectProvider<PlatformTransactionManager> transactionManager) {
        return new AsyncAuditLogWriter(
                selectBackend(properties, repository, objectMapper, transactionManager));
    }

    /**
     * Builds the storage backend configured via {@code audit-trail.storage}:
     * {@code database} (default — persisted in its own {@code REQUIRES_NEW}
     * transaction so mid-flush writes are safe) or {@code log}.
     */
    private static AuditLogWriter selectBackend(AuditTrailProperties properties,
                                                AuditLogRepository repository,
                                                ObjectMapper objectMapper,
                                                ObjectProvider<PlatformTransactionManager> transactionManager) {
        return switch (properties.getStorage()) {
            case LOG -> new LogAuditLogWriter(objectMapper);
            case DATABASE -> new DatabaseAuditLogWriter(repository, objectMapper,
                    transactionManager.getIfAvailable());
        };
    }

    /**
     * REST endpoint configuration. Kept in a nested class so the conditions are
     * evaluated before any Spring MVC class is loaded — Spring Web is an optional
     * dependency, and this configuration backs off entirely when it is absent or
     * when the host application is not a servlet web application.
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(RestController.class)
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    @ConditionalOnProperty(prefix = "audit-trail.rest", name = "enabled", havingValue = "true", matchIfMissing = true)
    static class AuditTrailRestConfiguration {

        /**
         * REST controller exposing {@code GET /audit-trail/{entity}/{id}}.
         * Only registered when {@code audit-trail.rest.enabled=true} (default).
         */
        @Bean
        @ConditionalOnMissingBean
        public AuditTrailController auditTrailController(AuditLogRepository repository,
                                                         AuditTrailProperties properties) {
            return new AuditTrailController(repository, properties);
        }
    }
}
