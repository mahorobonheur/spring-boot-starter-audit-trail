package io.github.mahorobonheur.audittrail.writer;

import io.github.mahorobonheur.audittrail.model.AuditAction;
import io.github.mahorobonheur.audittrail.model.AuditWriteRequest;
import io.github.mahorobonheur.audittrail.model.FieldDiff;

import java.util.List;

/**
 * Strategy interface for persisting audit events.
 *
 * <p>The primary write method accepts an {@link AuditWriteRequest}, which carries
 * all audit metadata including optional {@code whyReason}, {@code snapshotLabel},
 * and masked-fields flag introduced in v2.
 *
 * <p>The default implementation writes events to the database ({@link DatabaseAuditLogWriter}).
 * Provide your own bean of this type to override the default behaviour.
 *
 * @author Bonheur Mahoro
 */
public interface AuditLogWriter {

    /**
     * Persists an audit event described by the given request.
     *
     * @param request all fields needed to create the audit entry
     */
    void write(AuditWriteRequest request);

    /**
     * Backwards-compatible convenience overload that delegates to {@link #write(AuditWriteRequest)}.
     *
     * @param entityName the simple class name of the audited entity (e.g. {@code "User"})
     * @param entityId   string representation of the entity's primary key
     * @param action     the type of change: {@code CREATE}, {@code UPDATE}, or {@code DELETE}
     * @param changedBy  the username of the user who made the change
     * @param diffs      the list of field-level changes detected for this event
     */
    default void write(String entityName,
                       String entityId,
                       AuditAction action,
                       String changedBy,
                       List<FieldDiff> diffs) {
        write(AuditWriteRequest.from(entityName, entityId, action, changedBy, diffs));
    }
}
