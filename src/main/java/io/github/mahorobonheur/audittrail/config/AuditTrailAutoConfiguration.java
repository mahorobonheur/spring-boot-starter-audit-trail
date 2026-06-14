package io.github.mahorobonheur.audittrail.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.mahorobonheur.audittrail.actuator.AuditTrailActuatorEndpoint;
import io.github.mahorobonheur.audittrail.anomaly.AuditAnomalyDetector;
import io.github.mahorobonheur.audittrail.aspect.AuditWhyAspect;
import io.github.mahorobonheur.audittrail.controller.AuditTrailController;
import io.github.mahorobonheur.audittrail.engine.FieldDiffEngine;
import io.github.mahorobonheur.audittrail.listener.AuditTrailEntityListener;
import io.github.mahorobonheur.audittrail.repository.AuditLogRepository;
import io.github.mahorobonheur.audittrail.security.AuditSecurityResolver;
import io.github.mahorobonheur.audittrail.service.AuditChainService;
import io.github.mahorobonheur.audittrail.service.AuditReconstructionService;
import io.github.mahorobonheur.audittrail.service.AuditSnapshotService;
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
import org.springframework.context.ApplicationEventPublisher;
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
 * @author Bonheur Mahoro
 */
@AutoConfiguration
@EnableAsync
@EnableConfigurationProperties(AuditTrailProperties.class)
@ConditionalOnProperty(prefix = "audit-trail", name = "enabled", havingValue = "true", matchIfMissing = true)
public class AuditTrailAutoConfiguration {

    /**
     * The audit log repository, built programmatically so the starter never has to
     * declare {@code @EnableJpaRepositories}.
     */
    @Bean(name = "auditTrailAuditLogRepository")
    @ConditionalOnMissingBean
    public AuditLogRepository auditTrailAuditLogRepository(EntityManagerFactory entityManagerFactory) {
        EntityManager entityManager = SharedEntityManagerCreator.createSharedEntityManager(entityManagerFactory);
        return new JpaRepositoryFactory(entityManager).getRepository(AuditLogRepository.class);
    }

    /**
     * The core diff engine. Stateless and thread-safe.
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
     * Exposes the Spring {@code ApplicationContext} via a static accessor.
     */
    @Bean
    @ConditionalOnMissingBean
    public SpringContextHolder springContextHolder() {
        return new SpringContextHolder();
    }

    /**
     * The JPA entity listener that intercepts lifecycle events.
     */
    @Bean
    @ConditionalOnMissingBean
    public AuditTrailEntityListener auditTrailEntityListener() {
        return new AuditTrailEntityListener();
    }

    /**
     * Jackson 2 {@link ObjectMapper} for serialising field diffs.
     */
    @Bean
    @ConditionalOnMissingBean
    public ObjectMapper auditTrailObjectMapper() {
        return new ObjectMapper();
    }

    /**
     * Applies {@code audit-trail.table-name} to the AuditLog entity table mapping.
     */
    @Bean
    @ConditionalOnMissingBean(PhysicalNamingStrategy.class)
    public PhysicalNamingStrategy auditTrailPhysicalNamingStrategy(AuditTrailProperties properties) {
        return new AuditTrailTableNamingStrategy(properties.getTableName());
    }

    // ── AOP ───────────────────────────────────────────────────────────────────

    /**
     * AOP aspect that captures {@code @AuditWhy} parameters from Spring bean methods.
     * Only registered when AspectJ weaver is on the classpath.
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnClass(name = "org.aspectj.lang.ProceedingJoinPoint")
    public AuditWhyAspect auditWhyAspect() {
        return new AuditWhyAspect();
    }

    // ── Services ──────────────────────────────────────────────────────────────

    /**
     * Service for capturing explicit point-in-time entity snapshots.
     */
    @Bean
    @ConditionalOnMissingBean
    public AuditSnapshotService auditSnapshotService(AuditLogWriter writer,
                                                      FieldDiffEngine engine,
                                                      AuditSecurityResolver resolver) {
        return new AuditSnapshotService(writer, engine, resolver);
    }

    /**
     * Service for reconstructing entity state at a past point in time.
     */
    @Bean
    @ConditionalOnMissingBean
    public AuditReconstructionService auditReconstructionService(AuditLogRepository repo,
                                                                   ObjectMapper mapper) {
        return new AuditReconstructionService(repo, mapper);
    }

    // ── Chain service (conditional) ───────────────────────────────────────────

    /**
     * Chain hashing service — only created when {@code audit-trail.chain.enabled=true}.
     * The {@code @ConditionalOnProperty} on the bean itself handles this; the bean
     * declaration here ensures it participates in auto-configuration ordering.
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "audit-trail.chain", name = "enabled", havingValue = "true")
    public AuditChainService auditChainService() {
        return new AuditChainService();
    }

    // ── Anomaly detector (conditional) ───────────────────────────────────────

    /**
     * Anomaly detector — only created when {@code audit-trail.anomaly.enabled=true}.
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "audit-trail.anomaly", name = "enabled", havingValue = "true")
    public AuditAnomalyDetector auditAnomalyDetector(ApplicationEventPublisher publisher,
                                                      AuditTrailProperties props) {
        return new AuditAnomalyDetector(publisher, props);
    }

    // ── Actuator endpoint (conditional) ──────────────────────────────────────

    /**
     * Actuator endpoint — only created when Spring Boot Actuator is on the classpath.
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnClass(name = "org.springframework.boot.actuate.endpoint.annotation.Endpoint")
    public AuditTrailActuatorEndpoint auditTrailActuatorEndpoint(AuditLogRepository repo) {
        return new AuditTrailActuatorEndpoint(repo);
    }

    // ── Writer beans ──────────────────────────────────────────────────────────

    /**
     * Synchronous write strategy ({@code audit-trail.async=false}).
     */
    @Bean
    @ConditionalOnMissingBean(AuditLogWriter.class)
    @ConditionalOnProperty(prefix = "audit-trail", name = "async", havingValue = "false")
    public AuditLogWriter syncAuditLogWriter(AuditTrailProperties properties,
                                             AuditLogRepository repository,
                                             ObjectMapper objectMapper,
                                             ObjectProvider<PlatformTransactionManager> transactionManager,
                                             ObjectProvider<AuditChainService> chain,
                                             ObjectProvider<AuditAnomalyDetector> anomaly) {
        return selectBackend(properties, repository, objectMapper, transactionManager, chain, anomaly);
    }

    /**
     * Asynchronous write strategy ({@code audit-trail.async=true}, the default).
     */
    @Bean
    @ConditionalOnMissingBean(AuditLogWriter.class)
    @ConditionalOnProperty(prefix = "audit-trail", name = "async", havingValue = "true", matchIfMissing = true)
    public AuditLogWriter asyncAuditLogWriter(AuditTrailProperties properties,
                                              AuditLogRepository repository,
                                              ObjectMapper objectMapper,
                                              ObjectProvider<PlatformTransactionManager> transactionManager,
                                              ObjectProvider<AuditChainService> chain,
                                              ObjectProvider<AuditAnomalyDetector> anomaly) {
        return new AsyncAuditLogWriter(
                selectBackend(properties, repository, objectMapper, transactionManager, chain, anomaly));
    }

    private static AuditLogWriter selectBackend(AuditTrailProperties properties,
                                                 AuditLogRepository repository,
                                                 ObjectMapper objectMapper,
                                                 ObjectProvider<PlatformTransactionManager> txm,
                                                 ObjectProvider<AuditChainService> chain,
                                                 ObjectProvider<AuditAnomalyDetector> anomaly) {
        return switch (properties.getStorage()) {
            case LOG -> new LogAuditLogWriter(objectMapper);
            case DATABASE -> new DatabaseAuditLogWriter(
                    repository,
                    objectMapper,
                    txm.getIfAvailable(),
                    chain.getIfAvailable(),
                    anomaly.getIfAvailable());
        };
    }

    // ── REST endpoint configuration ───────────────────────────────────────────

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(RestController.class)
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    @ConditionalOnProperty(prefix = "audit-trail.rest", name = "enabled", havingValue = "true", matchIfMissing = true)
    static class AuditTrailRestConfiguration {

        @Bean
        @ConditionalOnMissingBean
        public AuditTrailController auditTrailController(AuditLogRepository repository,
                                                          AuditTrailProperties properties,
                                                          ObjectProvider<AuditChainService> chainService,
                                                          AuditReconstructionService reconstructionService) {
            return new AuditTrailController(
                    repository,
                    properties,
                    chainService.getIfAvailable() != null
                            ? java.util.Optional.of(chainService.getIfAvailable())
                            : java.util.Optional.empty(),
                    reconstructionService);
        }
    }
}
