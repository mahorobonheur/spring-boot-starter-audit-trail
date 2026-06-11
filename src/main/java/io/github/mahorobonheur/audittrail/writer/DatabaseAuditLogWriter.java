package io.github.mahorobonheur.audittrail.writer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.mahorobonheur.audittrail.model.AuditAction;
import io.github.mahorobonheur.audittrail.model.AuditLog;
import io.github.mahorobonheur.audittrail.model.FieldDiff;
import io.github.mahorobonheur.audittrail.repository.AuditLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;

/**
 * Default {@link AuditLogWriter} implementation that persists audit events
 * to the relational database via {@link AuditLogRepository}.
 *
 * <p>Writes are synchronous. When {@code audit-trail.async=true} (the default),
 * {@link AsyncAuditLogWriter} delegates to this class on a background thread.
 *
 * <p>When a {@link PlatformTransactionManager} is supplied, each audit entry is
 * persisted in its own {@code REQUIRES_NEW} transaction. This is essential for
 * synchronous writes: the writer is invoked from Hibernate event listeners that
 * fire <em>during</em> a flush, and saving through the same session mid-flush
 * can corrupt Hibernate's action queue.
 *
 * <p>Field diffs are serialised to JSON using Jackson before storage.
 *
 * @author Bonheur Mahoro
 */
public class DatabaseAuditLogWriter implements AuditLogWriter {

    private static final Logger log = LoggerFactory.getLogger(DatabaseAuditLogWriter.class);

    private final AuditLogRepository repository;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;

    /**
     * Creates a writer that saves directly through the repository without
     * transaction isolation. Intended for unit tests and custom setups.
     */
    public DatabaseAuditLogWriter(AuditLogRepository repository, ObjectMapper objectMapper) {
        this(repository, objectMapper, null);
    }

    /**
     * Creates a writer that persists each audit entry in a new, independent
     * transaction ({@code REQUIRES_NEW}).
     */
    public DatabaseAuditLogWriter(AuditLogRepository repository,
                                  ObjectMapper objectMapper,
                                  PlatformTransactionManager transactionManager) {
        this.repository   = repository;
        this.objectMapper = objectMapper;
        if (transactionManager != null) {
            TransactionTemplate template = new TransactionTemplate(transactionManager);
            template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            this.transactionTemplate = template;
        } else {
            this.transactionTemplate = null;
        }
    }

    /**
     * {@inheritDoc}
     *
     * <p>Serialises {@code diffs} to a JSON string and persists the resulting
     * {@link AuditLog} entity.
     */
    @Override
    public void write(String entityName,
                      String entityId,
                      AuditAction action,
                      String changedBy,
                      List<FieldDiff> diffs) {
        try {
            String diffsJson = objectMapper.writeValueAsString(diffs);
            AuditLog entry = new AuditLog(
                    entityName,
                    entityId,
                    action,
                    changedBy,
                    Instant.now(),
                    diffsJson
            );
            if (transactionTemplate != null) {
                transactionTemplate.executeWithoutResult(status -> repository.save(entry));
            } else {
                repository.save(entry);
            }
            log.debug("Audit log saved: entity={}, id={}, action={}, by={}",
                    entityName, entityId, action, changedBy);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialise field diffs for entity={}, id={}: {}",
                    entityName, entityId, e.getMessage(), e);
        }
    }
}
