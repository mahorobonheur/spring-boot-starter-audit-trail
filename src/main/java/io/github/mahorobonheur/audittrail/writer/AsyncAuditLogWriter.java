package io.github.mahorobonheur.audittrail.writer;

import io.github.mahorobonheur.audittrail.model.AuditAction;
import io.github.mahorobonheur.audittrail.model.FieldDiff;
import org.springframework.scheduling.annotation.Async;

import java.util.List;

/**
 * Decorator that delegates to the configured {@link AuditLogWriter} backend on a
 * background thread when {@code audit-trail.async=true} (the default), so audit
 * writes never block the originating request.
 */
public class AsyncAuditLogWriter implements AuditLogWriter {

    private final AuditLogWriter delegate;

    public AsyncAuditLogWriter(AuditLogWriter delegate) {
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
