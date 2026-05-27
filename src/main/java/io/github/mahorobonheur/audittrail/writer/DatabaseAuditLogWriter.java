package io.github.mahorobonheur.audittrail.writer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.mahorobonheur.audittrail.model.AuditAction;
import io.github.mahorobonheur.audittrail.model.AuditLog;
import io.github.mahorobonheur.audittrail.model.FieldDiff;
import io.github.mahorobonheur.audittrail.repository.AuditLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.time.Instant;
import java.util.List;

/**
 * Default {@link AuditLogWriter} implementation that persists audit events
 * to the relational database via {@link AuditLogRepository}.
 *
 * <p>Writes are synchronous. When {@code audit-trail.async=true} (the default),
 * {@link AsyncAuditLogWriter} delegates to this class on a background thread.
 *
 * <p>Field diffs are serialised to JSON using Jackson before storage.
 *
 * @author Bonheur Mahoro
 */
public class DatabaseAuditLogWriter implements AuditLogWriter {

    private static final Logger log = LoggerFactory.getLogger(DatabaseAuditLogWriter.class);

    private final AuditLogRepository repository;
    private final ObjectMapper objectMapper;

    public DatabaseAuditLogWriter(AuditLogRepository repository, ObjectMapper objectMapper) {
        this.repository   = repository;
        this.objectMapper = objectMapper;
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
            repository.save(entry);
            log.debug("Audit log saved: entity={}, id={}, action={}, by={}",
                    entityName, entityId, action, changedBy);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialise field diffs for entity={}, id={}: {}",
                    entityName, entityId, e.getMessage(), e);
        }
    }
}
