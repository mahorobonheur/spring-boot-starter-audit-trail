package io.github.mahorobonheur.audittrail.listener;

import io.github.mahorobonheur.audittrail.annotation.AuditTrail;
import io.github.mahorobonheur.audittrail.config.SpringContextHolder;
import io.github.mahorobonheur.audittrail.engine.FieldDiffEngine;
import io.github.mahorobonheur.audittrail.model.AuditAction;
import io.github.mahorobonheur.audittrail.model.FieldDiff;
import io.github.mahorobonheur.audittrail.security.AuditSecurityResolver;
import io.github.mahorobonheur.audittrail.writer.AuditLogWriter;
import org.hibernate.Hibernate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Intercepts entity lifecycle events for entities annotated with {@link AuditTrail}.
 *
 * <p>This listener is registered globally with Hibernate via
 * {@link io.github.mahorobonheur.audittrail.config.AuditTrailHibernateIntegrator},
 * which invokes it from the {@code POST_INSERT}, {@code PRE_UPDATE}, and
 * {@code PRE_DELETE} Hibernate events. For updates and deletes, the old/new
 * property state arrays come directly from Hibernate's dirty-checking, so the
 * recorded diff is exactly what is written to the database.
 *
 * <p>Delete audit events require a managed entity removal
 * ({@code EntityManager.remove} / {@code repository.delete}) — bulk operations
 * such as {@code deleteById} JPQL deletes do not trigger entity events.
 *
 * <p>Spring beans ({@link AuditLogWriter}, {@link AuditSecurityResolver},
 * {@link FieldDiffEngine}) are resolved lazily via {@link SpringContextHolder}
 * because Hibernate, not Spring, drives the event pipeline.
 *
 * @author Bonheur Mahoro
 */
public class AuditTrailEntityListener {

    private static final Logger log = LoggerFactory.getLogger(AuditTrailEntityListener.class);

    // Lazy bean accessors — resolved at call time via SpringContextHolder

    private AuditLogWriter writer() {
        return SpringContextHolder.getBean(AuditLogWriter.class);
    }

    private AuditSecurityResolver securityResolver() {
        return SpringContextHolder.getBean(AuditSecurityResolver.class);
    }

    private FieldDiffEngine diffEngine() {
        return SpringContextHolder.getBean(FieldDiffEngine.class);
    }

    // Lifecycle callbacks (invoked by AuditTrailHibernateIntegrator)

    /**
     * Fires after a new entity has been inserted (so database-generated IDs are
     * already assigned). Records a CREATE audit event listing all non-excluded
     * fields with {@code oldValue = null}.
     */
    public void onPrePersist(Object entity) {
        if (!isAudited(entity)) return;
        try {
            List<FieldDiff> diffs = diffEngine().diff(null, entity);
            write(entity, AuditAction.CREATE, diffs);
        } catch (Exception e) {
            log.warn("Audit trail failed for CREATE on {}: {}", entityName(entity), e.getMessage(), e);
        }
    }

    /**
     * Fires just before an existing entity is updated. Computes the field-level
     * diff from Hibernate's own old/new property state arrays.
     *
     * @param entity        the managed entity with its new state
     * @param propertyNames persister property names aligned with the state arrays
     * @param oldState      property values before the change (may be {@code null}
     *                      when Hibernate has no loaded state available)
     * @param newState      property values being written
     */
    public void onPreUpdate(Object entity, String[] propertyNames, Object[] oldState, Object[] newState) {
        if (!isAudited(entity)) return;
        try {
            List<FieldDiff> diffs = (propertyNames != null && oldState != null)
                    ? diffEngine().diff(Hibernate.getClass(entity), propertyNames, oldState, newState)
                    : diffEngine().diff(null, entity);
            if (!diffs.isEmpty()) {
                write(entity, AuditAction.UPDATE, diffs);
            }
        } catch (Exception e) {
            log.warn("Audit trail failed for UPDATE on {}: {}", entityName(entity), e.getMessage(), e);
        }
    }

    /**
     * Fires just before an entity is deleted. Records a DELETE audit event listing
     * all non-excluded fields with {@code newValue = null}, using Hibernate's
     * deleted-state array when available.
     *
     * @param entity        the entity being removed
     * @param propertyNames persister property names aligned with {@code deletedState}
     * @param deletedState  property values at deletion time (may be {@code null})
     */
    public void onPreRemove(Object entity, String[] propertyNames, Object[] deletedState) {
        if (!isAudited(entity)) return;
        try {
            List<FieldDiff> diffs = (propertyNames != null && deletedState != null)
                    ? diffEngine().diff(Hibernate.getClass(entity), propertyNames, deletedState, null)
                    : diffEngine().diff(entity, null);
            write(entity, AuditAction.DELETE, diffs);
        } catch (Exception e) {
            log.warn("Audit trail failed for DELETE on {}: {}", entityName(entity), e.getMessage(), e);
        }
    }

    // Helpers

    private boolean isAudited(Object entity) {
        return entity != null
                && Hibernate.getClass(entity).isAnnotationPresent(AuditTrail.class);
    }

    private String entityName(Object entity) {
        return Hibernate.getClass(entity).getSimpleName();
    }

    private String entityId(Object entity) {
        // Try common ID field names; return the identity hash code as fallback.
        Class<?> entityClass = Hibernate.getClass(entity);
        for (String idField : new String[]{"id", "ID", "Id"}) {
            try {
                var field = findField(entityClass, idField);
                if (field != null) {
                    field.setAccessible(true);
                    Object val = field.get(entity);
                    return val != null ? val.toString() : "null";
                }
            } catch (Exception ignored) { }
        }
        return String.valueOf(System.identityHashCode(entity));
    }

    private java.lang.reflect.Field findField(Class<?> clazz, String name) {
        Class<?> current = clazz;
        while (current != null && current != Object.class) {
            try { return current.getDeclaredField(name); } catch (NoSuchFieldException ignored) { }
            current = current.getSuperclass();
        }
        return null;
    }

    private void write(Object entity, AuditAction action, List<FieldDiff> diffs) {
        writer().write(
                entityName(entity),
                entityId(entity),
                action,
                securityResolver().getCurrentUser(),
                diffs
        );
    }
}
