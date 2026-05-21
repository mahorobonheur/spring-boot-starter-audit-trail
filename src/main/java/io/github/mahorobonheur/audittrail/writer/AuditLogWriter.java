package io.github.mahorobonheur.audittrail.writer;

import io.github.mahorobonheur.audittrail.model.AuditAction;
import io.github.mahorobonheur.audittrail.model.FieldDiff;

import java.util.List;

/**
 * Strategy interface for persisting audit events.
 *
 * <p>The default implementation writes events to the database ({@link DatabaseAuditLogWriter}).
 * Future implementations may write to a log file, a message queue, or a webhook endpoint.
 * You can provide your own bean of this type to override the default behaviour.
 *
 * @author Bonheur Mahoro
 */
public interface AuditLogWriter {

    /**
     * Persists an audit event.
     *
     * @param entityName the simple class name of the audited entity (e.g. {@code "User"})
     * @param entityId   string representation of the entity's primary key
     * @param action     the type of change: {@code CREATE}, {@code UPDATE}, or {@code DELETE}
     * @param changedBy  the username of the user who made the change
     * @param diffs      the list of field-level changes detected for this event
     */
    void write(String entityName,
               String entityId,
               AuditAction action,
               String changedBy,
               List<FieldDiff> diffs);
}
