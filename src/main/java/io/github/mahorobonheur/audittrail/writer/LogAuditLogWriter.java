package io.github.mahorobonheur.audittrail.writer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.mahorobonheur.audittrail.model.AuditWriteRequest;
import io.github.mahorobonheur.audittrail.model.FieldDiff;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;

/**
 * {@link AuditLogWriter} implementation that writes audit events as structured
 * lines to the {@code audit-trail} SLF4J log category instead of the database.
 *
 * <p>Activated with {@code audit-trail.storage=log}. Because entries are not
 * persisted to the audit log table, the REST endpoint and
 * {@link io.github.mahorobonheur.audittrail.repository.AuditLogRepository}
 * will not return them — route the {@code audit-trail} category to a dedicated
 * appender (file, syslog, log shipper) to collect the events.
 *
 * <h2>Example logback routing</h2>
 * <pre>{@code
 * <logger name="audit-trail" level="INFO" additivity="false">
 *     <appender-ref ref="AUDIT_FILE"/>
 * </logger>
 * }</pre>
 *
 * @author Bonheur Mahoro
 */
public class LogAuditLogWriter implements AuditLogWriter {

    /** Dedicated category so applications can route audit output to its own appender. */
    private static final Logger AUDIT_LOG = LoggerFactory.getLogger("audit-trail");

    private final ObjectMapper objectMapper;

    public LogAuditLogWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Emits a single structured log line containing the entity name, ID,
     * action, user, UTC timestamp, the JSON-serialised field diffs, and —
     * when present — the business reason.
     */
    @Override
    public void write(AuditWriteRequest request) {
        if (request.getWhyReason() != null) {
            AUDIT_LOG.info("entity={} id={} action={} by={} at={} why={} diffs={}",
                    request.getEntityName(),
                    request.getEntityId(),
                    request.getAction(),
                    request.getChangedBy(),
                    Instant.now(),
                    request.getWhyReason(),
                    toJson(request.getDiffs()));
        } else {
            AUDIT_LOG.info("entity={} id={} action={} by={} at={} diffs={}",
                    request.getEntityName(),
                    request.getEntityId(),
                    request.getAction(),
                    request.getChangedBy(),
                    Instant.now(),
                    toJson(request.getDiffs()));
        }
    }

    private String toJson(List<FieldDiff> diffs) {
        try {
            return objectMapper.writeValueAsString(diffs);
        } catch (JsonProcessingException e) {
            return "[]";
        }
    }
}
