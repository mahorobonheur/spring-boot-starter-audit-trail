package io.github.mahorobonheur.audittrail.writer;

import io.github.mahorobonheur.audittrail.model.AuditWriteRequest;
import org.springframework.scheduling.annotation.Async;

/**
 * Decorator that delegates to the configured {@link AuditLogWriter} backend on a
 * background thread when {@code audit-trail.async=true} (the default), so audit
 * writes never block the originating request.
 *
 * @author Bonheur Mahoro
 */
public class AsyncAuditLogWriter implements AuditLogWriter {

    private final AuditLogWriter delegate;

    public AsyncAuditLogWriter(AuditLogWriter delegate) {
        this.delegate = delegate;
    }

    @Async
    @Override
    public void write(AuditWriteRequest request) {
        delegate.write(request);
    }
}
