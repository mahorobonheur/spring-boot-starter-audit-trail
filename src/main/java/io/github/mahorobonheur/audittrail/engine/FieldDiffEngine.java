package io.github.mahorobonheur.audittrail.engine;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.mahorobonheur.audittrail.annotation.AuditExclude;
import io.github.mahorobonheur.audittrail.annotation.AuditMask;
import io.github.mahorobonheur.audittrail.annotation.AuditTrail;
import io.github.mahorobonheur.audittrail.model.FieldDiff;
import org.hibernate.Hibernate;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Compares two snapshots of the same entity and returns a {@link DiffResult}
 * containing the field-level diffs and a flag indicating whether any masked fields
 * were encountered.
 *
 * <p>Fields are excluded from the diff when:
 * <ul>
 *   <li>They are listed in the {@link AuditTrail#exclude()} attribute, or</li>
 *   <li>They are annotated with {@link AuditExclude}.</li>
 * </ul>
 *
 * <p>Fields annotated with {@link AuditMask} are included in the diff but their
 * actual values are replaced by the annotation's {@link AuditMask#placeholder()}.
 *
 * <p>The engine walks the full class hierarchy (including superclasses) so that
 * fields declared in {@code @MappedSuperclass} base classes are also captured.
 *
 * @author Bonheur Mahoro
 */
public class FieldDiffEngine {

    private final ObjectMapper objectMapper;

    /** No-arg constructor kept for backward compatibility; Jackson serialization disabled. */
    public FieldDiffEngine() {
        this.objectMapper = null;
    }

    /**
     * Preferred constructor — pass the application's {@link ObjectMapper} so that
     * complex nested objects ({@code @Embeddable}, plain POJOs) are serialized to
     * JSON rather than falling back to {@link Object#toString()}.
     */
    public FieldDiffEngine(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    // ── DiffResult ────────────────────────────────────────────────────────────

    /**
     * Holds the result of a diff computation: the list of changed fields and a
     * flag indicating whether any of those fields were masked.
     */
    public static final class DiffResult {

        private final List<FieldDiff> diffs;
        private final boolean         hasMaskedFields;

        public DiffResult(List<FieldDiff> diffs, boolean hasMaskedFields) {
            this.diffs           = Collections.unmodifiableList(diffs);
            this.hasMaskedFields = hasMaskedFields;
        }

        /** The field-level changes detected for this event. */
        public List<FieldDiff> getDiffs()         { return diffs; }

        /** {@code true} if at least one field was replaced with a mask placeholder. */
        public boolean isHasMaskedFields()        { return hasMaskedFields; }
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Computes the set of fields that changed between {@code oldEntity} and {@code newEntity}.
     *
     * <p>For a {@code CREATE} event, pass {@code null} as {@code oldEntity}.
     * For a {@code DELETE} event, pass {@code null} as {@code newEntity}.
     *
     * @param oldEntity the entity state before the change (may be {@code null})
     * @param newEntity the entity state after the change (may be {@code null})
     * @return a {@link DiffResult} containing changed-field descriptors and the masked flag
     * @throws IllegalArgumentException if both arguments are {@code null}
     */
    public DiffResult diff(Object oldEntity, Object newEntity) {
        if (oldEntity == null && newEntity == null) {
            throw new IllegalArgumentException("At least one of oldEntity or newEntity must be non-null");
        }

        Object reference = (newEntity != null) ? newEntity : oldEntity;
        Class<?> entityClass = Hibernate.getClass(reference);
        Set<String> excluded = excludedFieldNames(entityClass);
        Map<String, AuditMask> maskedFields = maskedFieldsOf(entityClass);

        List<Field> allFields = getAllFields(entityClass);
        List<FieldDiff> diffs = new ArrayList<>();
        boolean hasMasked = false;

        for (Field field : allFields) {
            if (excluded.contains(field.getName())) {
                continue;
            }

            field.setAccessible(true);

            Object oldValue = safeGet(field, oldEntity);
            Object newValue = safeGet(field, newEntity);

            AuditMask mask = maskedFields.get(field.getName());
            if (mask != null) {
                // Always record the field as changed when masked, showing placeholder on both sides
                String placeholder = mask.placeholder();
                diffs.add(new FieldDiff(field.getName(), placeholder, placeholder));
                hasMasked = true;
            } else if (!Objects.equals(oldValue, newValue)) {
                diffs.add(new FieldDiff(
                        field.getName(),
                        stringify(oldValue),
                        stringify(newValue)
                ));
            }
        }

        return new DiffResult(diffs, hasMasked);
    }

    /**
     * Computes field-level diffs from Hibernate event state arrays, as provided by
     * {@code PreUpdateEvent.getOldState()/getState()} and {@code PreDeleteEvent.getDeletedState()}.
     *
     * <p>This is the preferred path for UPDATE and DELETE events: the arrays come
     * directly from Hibernate's own dirty-checking, so the resulting diff is
     * guaranteed to reflect exactly what is written to the database. The identifier
     * property is not part of these arrays.
     *
     * @param entityType    the audited entity class (used to resolve exclusions and masks)
     * @param propertyNames Hibernate persister property names, aligned with the state arrays
     * @param oldState      property values before the change ({@code null} on CREATE)
     * @param newState      property values after the change ({@code null} on DELETE)
     * @return a {@link DiffResult} containing changed-field descriptors and the masked flag
     */
    public DiffResult diff(Class<?> entityType,
                           String[] propertyNames,
                           Object[] oldState,
                           Object[] newState) {
        if (propertyNames == null) {
            throw new IllegalArgumentException("propertyNames must be non-null");
        }
        Set<String> excluded = excludedFieldNames(entityType);
        Map<String, AuditMask> maskedFields = maskedFieldsOf(entityType);
        List<FieldDiff> diffs = new ArrayList<>();
        boolean hasMasked = false;

        for (int i = 0; i < propertyNames.length; i++) {
            String name = propertyNames[i];
            if (excluded.contains(name)) {
                continue;
            }
            Object oldValue = (oldState != null && i < oldState.length) ? oldState[i] : null;
            Object newValue = (newState != null && i < newState.length) ? newState[i] : null;

            AuditMask mask = maskedFields.get(name);
            if (mask != null) {
                String placeholder = mask.placeholder();
                diffs.add(new FieldDiff(name, placeholder, placeholder));
                hasMasked = true;
            } else if (!Objects.equals(oldValue, newValue)) {
                diffs.add(new FieldDiff(name, stringify(oldValue), stringify(newValue)));
            }
        }

        return new DiffResult(diffs, hasMasked);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Returns the set of field names excluded from auditing for the given class:
     * names listed in {@link AuditTrail#exclude()} plus fields annotated with
     * {@link AuditExclude}.
     */
    private Set<String> excludedFieldNames(Class<?> entityClass) {
        Set<String> excluded = new HashSet<>();
        AuditTrail annotation = entityClass.getAnnotation(AuditTrail.class);
        if (annotation != null) {
            excluded.addAll(Arrays.asList(annotation.exclude()));
        }
        for (Field field : getAllFields(entityClass)) {
            if (field.isAnnotationPresent(AuditExclude.class)) {
                excluded.add(field.getName());
            }
        }
        return excluded;
    }

    /**
     * Returns a map of field name → {@link AuditMask} annotation for all fields
     * in the class hierarchy that carry {@link AuditMask}.
     *
     * @param entityClass the entity class to inspect
     * @return an unmodifiable map; empty when no masked fields are present
     */
    private Map<String, AuditMask> maskedFieldsOf(Class<?> entityClass) {
        Map<String, AuditMask> masked = new HashMap<>();
        for (Field field : getAllFields(entityClass)) {
            AuditMask mask = field.getAnnotation(AuditMask.class);
            if (mask != null) {
                masked.put(field.getName(), mask);
            }
        }
        return Collections.unmodifiableMap(masked);
    }

    /**
     * Collects all declared fields from the class and every superclass,
     * stopping at {@link Object}.
     */
    private List<Field> getAllFields(Class<?> clazz) {
        List<Field> fields = new ArrayList<>();
        Class<?> current = clazz;
        while (current != null && current != Object.class) {
            fields.addAll(Arrays.asList(current.getDeclaredFields()));
            current = current.getSuperclass();
        }
        return fields;
    }

    /** Returns the field value from {@code entity}, or {@code null} if entity is {@code null}. */
    private Object safeGet(Field field, Object entity) {
        if (entity == null) return null;
        try {
            return field.get(entity);
        } catch (IllegalAccessException e) {
            return null;
        }
    }

    /**
     * Converts a value to a human-readable string for storage in field diffs.
     *
     * <p>Special handling (in order):
     * <ol>
     *   <li>Uninitialized Hibernate proxies/collections → {@code null} (avoids lazy-load outside session)</li>
     *   <li>JPA {@code @Entity} objects → the entity's {@code @Id} value (e.g. {@code 3fa1b2c3-…})</li>
     *   <li>Iterable/collection → {@code [id1, id2, …]} (IDs for entities, toString for primitives)</li>
     *   <li>Simple types (String, Number, Boolean, Enum, Date, UUID, temporal) → {@code toString()}</li>
     *   <li>Complex objects ({@code @Embeddable}, nested POJOs) → JSON via Jackson when available</li>
     *   <li>Fallback → {@code toString()}</li>
     * </ol>
     */
    private String stringify(Object value) {
        if (value == null) return null;
        try {
            // Don't trigger lazy loading for uninitialized proxies or collections
            if (!Hibernate.isInitialized(value)) return null;

            Class<?> realClass = Hibernate.getClass(value);

            // JPA entity → extract the @Id value
            if (realClass.isAnnotationPresent(jakarta.persistence.Entity.class)) {
                String id = extractEntityId(value, realClass);
                return id != null ? id : realClass.getSimpleName();
            }

            // Iterable (collections of entities or primitives)
            if (value instanceof Iterable) {
                List<String> items = new ArrayList<>();
                for (Object item : (Iterable<?>) value) {
                    if (item == null) { items.add("null"); continue; }
                    try {
                        Class<?> itemClass = Hibernate.getClass(item);
                        if (itemClass.isAnnotationPresent(jakarta.persistence.Entity.class)) {
                            String id = extractEntityId(item, itemClass);
                            items.add(id != null ? id : itemClass.getSimpleName());
                        } else {
                            items.add(item.toString());
                        }
                    } catch (Exception ignored) {
                        items.add(item.toString());
                    }
                }
                return "[" + String.join(", ", items) + "]";
            }

            // Simple scalar types — toString() is always correct
            if (isSimpleType(realClass)) {
                return value.toString();
            }

            // Complex object (@Embeddable, nested POJO) — serialize to JSON
            if (objectMapper != null) {
                try {
                    return objectMapper.writeValueAsString(value);
                } catch (Exception ignored) {
                    // Jackson can't serialize it (e.g. circular refs) — fall through
                }
            }
        } catch (Exception ignored) {
            // Safety net — fall through to plain toString()
        }
        return value.toString();
    }

    /**
     * Returns {@code true} for types whose {@link Object#toString()} produces a
     * meaningful, human-readable value: primitives, String, Number, Boolean,
     * Enum, UUID, {@link Date}, and any {@link java.time.temporal.Temporal}.
     */
    private boolean isSimpleType(Class<?> clazz) {
        return clazz.isPrimitive()
                || clazz == String.class
                || clazz == UUID.class
                || clazz == Boolean.class
                || clazz.isEnum()
                || Number.class.isAssignableFrom(clazz)
                || Date.class.isAssignableFrom(clazz)
                || java.time.temporal.Temporal.class.isAssignableFrom(clazz);
    }

    /**
     * Walks the class hierarchy to find a field annotated with
     * {@link jakarta.persistence.Id} and returns its value as a String.
     * Returns {@code null} if no {@code @Id} field is found or is inaccessible.
     */
    private String extractEntityId(Object entity, Class<?> entityClass) {
        Class<?> cursor = entityClass;
        while (cursor != null && cursor != Object.class) {
            for (Field field : cursor.getDeclaredFields()) {
                if (field.isAnnotationPresent(jakarta.persistence.Id.class)) {
                    try {
                        field.setAccessible(true);
                        Object val = field.get(entity);
                        return val != null ? val.toString() : null;
                    } catch (Exception ignored) { }
                }
            }
            cursor = cursor.getSuperclass();
        }
        return null;
    }
}
