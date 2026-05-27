package io.github.mahorobonheur.audittrail.writer;

import io.github.mahorobonheur.audittrail.model.AuditAction;
import io.github.mahorobonheur.audittrail.model.FieldDiff;
import org.springframework.scheduling.annotation.Async;

import java.util.List;

/**
 * Decorator that delegates to {@link DatabaseAuditLogWriter} on a background thread
 * when {@code audit-trail.async=true}.
 */
public class AsyncAuditLogWriter implements AuditLogWriter {

    private final DatabaseAuditLogWriter delegate;

    public AsyncAuditLogWriter(DatabaseAuditLogWriter delegate) {
        this.delegate = delegate;
    }

    @Async
    @Override
    public void write(String entityName,
                      String entityId,
                      AuditAction action,
                      String changedBy,
                      List<FieldDiff> diffs) {
        delegate.write(entityName, entityId, action, changedBy, diffs);
    }
}
