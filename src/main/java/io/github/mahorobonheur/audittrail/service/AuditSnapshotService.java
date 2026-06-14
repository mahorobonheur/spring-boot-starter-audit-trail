package io.github.mahorobonheur.audittrail.service;

import io.github.mahorobonheur.audittrail.context.AuditWhyContext;
import io.github.mahorobonheur.audittrail.engine.FieldDiffEngine;
import io.github.mahorobonheur.audittrail.engine.FieldDiffEngine.DiffResult;
import io.github.mahorobonheur.audittrail.model.AuditAction;
import io.github.mahorobonheur.audittrail.model.AuditWriteRequest;
import io.github.mahorobonheur.audittrail.security.AuditSecurityResolver;
import io.github.mahorobonheur.audittrail.writer.AuditLogWriter;
import org.hibernate.Hibernate;
import org.springframework.stereotype.Service;

import java.lang.reflect.Field;

/**
 * Programmatic API for capturing a point-in-time snapshot of an entity's current state.
 *
 * <p>Unlike the automatic Hibernate listener, snapshots are triggered explicitly by
 * application code. They record the full current field state with
 * {@link AuditAction#CREATE} semantics (all fields, {@code oldValue = null}).
 * A {@code snapshotLabel} is attached so these entries can be distinguished from
 * regular persistence events when querying the audit log.
 *
 * <p>Typically used before complex operations:
 * <pre>{@code
 * snapshotService.captureWithReason(contract, "pre-approval", "Snapshot before approval workflow");
 * approvalService.submit(contract);
 * }</pre>
 *
 * @author Bonheur Mahoro
 */
@Service
public class AuditSnapshotService {

    private final AuditLogWriter        writer;
    private final FieldDiffEngine       diffEngine;
    private final AuditSecurityResolver securityResolver;

    public AuditSnapshotService(AuditLogWriter writer,
                                 FieldDiffEngine diffEngine,
                                 AuditSecurityResolver securityResolver) {
        this.writer          = writer;
        this.diffEngine      = diffEngine;
        this.securityResolver = securityResolver;
    }

    /**
     * Captures the current state of the given entity as a labelled snapshot.
     *
     * @param entity the entity to snapshot (must be a non-null, Hibernate-proxied or plain POJO)
     * @param label  a short, human-readable label identifying this snapshot point
     */
    public void capture(Object entity, String label) {
        captureInternal(entity, label, null);
    }

    /**
     * Captures the current state of the given entity as a labelled snapshot with a reason.
     *
     * @param entity the entity to snapshot
     * @param label  a short, human-readable label identifying this snapshot point
     * @param reason the business reason for taking this snapshot
     */
    public void captureWithReason(Object entity, String label, String reason) {
        captureInternal(entity, label, reason);
    }

    // ── Private ───────────────────────────────────────────────────────────────

    private void captureInternal(Object entity, String label, String reason) {
        if (entity == null) {
            throw new IllegalArgumentException("entity must not be null");
        }

        // diff(null, entity) gives us all current field values with oldValue=null
        DiffResult result = diffEngine.diff(null, entity);

        AuditWriteRequest request = AuditWriteRequest.builder()
                .entityName(entityName(entity))
                .entityId(entityId(entity))
                .action(AuditAction.CREATE)
                .changedBy(securityResolver.getCurrentUser())
                .diffs(result.getDiffs())
                .whyReason(reason)
                .snapshotLabel(label)
                .hasMaskedFields(result.isHasMaskedFields())
                .build();

        writer.write(request);
    }

    private String entityName(Object entity) {
        return Hibernate.getClass(entity).getSimpleName();
    }

    private String entityId(Object entity) {
        Class<?> entityClass = Hibernate.getClass(entity);
        for (String idField : new String[]{"id", "ID", "Id"}) {
            try {
                Field field = findField(entityClass, idField);
                if (field != null) {
                    field.setAccessible(true);
                    Object val = field.get(entity);
                    return val != null ? val.toString() : "null";
                }
            } catch (Exception ignored) { }
        }
        return String.valueOf(System.identityHashCode(entity));
    }

    private Field findField(Class<?> clazz, String name) {
        Class<?> current = clazz;
        while (current != null && current != Object.class) {
            try { return current.getDeclaredField(name); } catch (NoSuchFieldException ignored) { }
            current = current.getSuperclass();
        }
        return null;
    }
}
