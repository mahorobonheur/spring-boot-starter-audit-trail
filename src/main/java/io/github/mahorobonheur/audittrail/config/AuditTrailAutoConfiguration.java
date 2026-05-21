package io.github.mahorobonheur.audittrail.config;

import io.github.mahorobonheur.audittrail.controller.AuditTrailController;
import io.github.mahorobonheur.audittrail.engine.FieldDiffEngine;
import io.github.mahorobonheur.audittrail.listener.AuditTrailEntityListener;
import io.github.mahorobonheur.audittrail.repository.AuditLogRepository;
import io.github.mahorobonheur.audittrail.security.AuditSecurityResolver;
import io.github.mahorobonheur.audittrail.writer.AuditLogWriter;
import io.github.mahorobonheur.audittrail.writer.DatabaseAuditLogWriter;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableAsync;

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
@AutoConfiguration
@EnableAsync
@EnableConfigurationProperties(AuditTrailProperties.class)
@ConditionalOnProperty(prefix = "audit-trail", name = "enabled", havingValue = "true", matchIfMissing = true)
public class AuditTrailAutoConfiguration {

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
     * Default write strategy: persists audit events to the relational database.
     * Override by providing your own {@link AuditLogWriter} bean.
     */
    @Bean
    @ConditionalOnMissingBean(AuditLogWriter.class)
    public AuditLogWriter auditLogWriter(AuditLogRepository repository,
                                         com.fasterxml.jackson.databind.ObjectMapper objectMapper) {
        return new DatabaseAuditLogWriter(repository, objectMapper);
    }

    /**
     * REST controller exposing {@code GET /audit-trail/{entity}/{id}}.
     * Only registered when {@code audit-trail.rest.enabled=true} (default).
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "audit-trail.rest", name = "enabled", havingValue = "true", matchIfMissing = true)
    public AuditTrailController auditTrailController(AuditLogRepository repository,
                                                      AuditTrailProperties properties) {
        return new AuditTrailController(repository, properties);
    }
}
