package io.github.mahorobonheur.audittrail.listener;

import io.github.mahorobonheur.audittrail.annotation.AuditTrail;
import io.github.mahorobonheur.audittrail.config.SpringContextHolder;
import io.github.mahorobonheur.audittrail.engine.FieldDiffEngine;
import io.github.mahorobonheur.audittrail.model.AuditAction;
import io.github.mahorobonheur.audittrail.model.FieldDiff;
import io.github.mahorobonheur.audittrail.security.AuditSecurityResolver;
import io.github.mahorobonheur.audittrail.writer.AuditLogWriter;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PreRemove;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.PrePersist;
import org.hibernate.Hibernate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Constructor;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * JPA {@code EntityListener} that intercepts entity lifecycle events for
 * entities annotated with {@link AuditTrail}.
 *
 * <p>This listener is registered globally via {@link io.github.mahorobonheur.audittrail.config.AuditTrailHibernateIntegrator}.
 * Delete audit events require a managed entity removal ({@code EntityManager.remove}) — bulk
 * {@code deleteById} calls do not trigger these callbacks.
 * It hooks into the standard JPA lifecycle callbacks:
 * <ul>
 *   <li>{@link PrePersist} — captures CREATE events</li>
 *   <li>{@link PostLoad} + {@link PreUpdate} — captures UPDATE events with field-level diffs</li>
 *   <li>{@link PreRemove} — captures DELETE events</li>
 * </ul>
 *
 * <p>Spring beans ({@link AuditLogWriter}, {@link AuditSecurityResolver},
 * {@link FieldDiffEngine}) are resolved lazily via {@link SpringContextHolder}
 * because JPA EntityListeners are instantiated by Hibernate, not by Spring,
 * and therefore cannot receive {@code @Autowired} injections directly.
 *
 * @author Bonheur Mahoro
 */
public class AuditTrailEntityListener {

    private static final Logger log = LoggerFactory.getLogger(AuditTrailEntityListener.class);

    /**
     * Stores a pre-update snapshot of each entity keyed by its identity hash.
     * This allows field-level diffs to be computed in {@link #onPreUpdate(Object)}.
     */
    private static final Map<Integer, Object> PRE_UPDATE_SNAPSHOTS = new HashMap<>();

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

    // Lifecycle callbacks

    /**
     * Fires just before a new entity is inserted into the database.
     * Records a CREATE audit event. All non-excluded fields are listed in the diff
     * with {@code oldValue = null}.
     */
    @PrePersist
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
     * Fires after an entity is loaded from the database.
     * Stores a snapshot of the current state for later comparison in {@link #onPreUpdate(Object)}.
     */
    @PostLoad
    public void onPostLoad(Object entity) {
        if (!isAudited(entity)) return;
        try {
            PRE_UPDATE_SNAPSHOTS.put(System.identityHashCode(entity), cloneShallow(entity));
        } catch (Exception e) {
            log.warn("Could not snapshot entity {} for audit: {}", entityName(entity), e.getMessage(), e);
        }
    }

    /**
     * Fires just before an existing entity is updated in the database.
     * Computes field-level diffs against the snapshot captured in {@link #onPostLoad(Object)}.
     */
    @PreUpdate
    public void onPreUpdate(Object entity) {
        if (!isAudited(entity)) return;
        try {
            Object snapshot = PRE_UPDATE_SNAPSHOTS.remove(System.identityHashCode(entity));
            List<FieldDiff> diffs = diffEngine().diff(snapshot, entity);
            if (!diffs.isEmpty()) {
                write(entity, AuditAction.UPDATE, diffs);
            }
        } catch (Exception e) {
            log.warn("Audit trail failed for UPDATE on {}: {}", entityName(entity), e.getMessage(), e);
        }
    }

    /**
     * Fires just before an entity is deleted from the database.
     * Records a DELETE audit event. All non-excluded fields are listed in the diff
     * with {@code newValue = null}.
     */
    @PreRemove
    public void onPreRemove(Object entity) {
        if (!isAudited(entity)) return;
        try {
            Object snapshot = PRE_UPDATE_SNAPSHOTS.remove(System.identityHashCode(entity));
            Object oldState = snapshot != null ? snapshot : entity;
            List<FieldDiff> diffs = diffEngine().diff(oldState, null);
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

    /**
     * Creates a shallow field-by-field copy of the entity for snapshotting purposes.
     * Only primitive, String, Number, and Enum types are copied to avoid cascading
     * relationship loads.
     */
    private Object cloneShallow(Object entity) throws Exception {
        Class<?> clazz = Hibernate.getClass(entity);
        Constructor<?> constructor = clazz.getDeclaredConstructor();
        constructor.setAccessible(true);                      // handles protected/package-private constructors
        Object clone = constructor.newInstance();
        Class<?> current = clazz;
        while (current != null && current != Object.class) {
            for (var field : current.getDeclaredFields()) {
                field.setAccessible(true);
                Object val = field.get(entity);
                if (isSimpleType(val)) {
                    field.set(clone, val);
                }
            }
            current = current.getSuperclass();
        }
        return clone;
    }

    private boolean isSimpleType(Object val) {
        if (val == null) return true;
        return val instanceof String
                || val instanceof Number
                || val instanceof Boolean
                || val instanceof Enum<?>
                || val instanceof java.time.temporal.Temporal
                || val instanceof java.util.Date;
    }
}
