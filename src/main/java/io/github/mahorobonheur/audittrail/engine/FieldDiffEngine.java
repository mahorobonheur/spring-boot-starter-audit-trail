package io.github.mahorobonheur.audittrail.engine;

import io.github.mahorobonheur.audittrail.annotation.AuditExclude;
import io.github.mahorobonheur.audittrail.annotation.AuditTrail;
import io.github.mahorobonheur.audittrail.model.FieldDiff;
import org.hibernate.Hibernate;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Compares two snapshots of the same entity and returns a list of field-level diffs.
 *
 * <p>Fields are excluded from the diff when:
 * <ul>
 *   <li>They are listed in the {@link AuditTrail#exclude()} attribute, or</li>
 *   <li>They are annotated with {@link AuditExclude}.</li>
 * </ul>
 *
 * <p>The engine walks the full class hierarchy (including superclasses) so that
 * fields declared in {@code @MappedSuperclass} base classes are also captured.
 *
 * @author Bonheur Mahoro
 */
public class FieldDiffEngine {

    /**
     * Computes the set of fields that changed between {@code oldEntity} and {@code newEntity}.
     *
     * <p>For a {@code CREATE} event, pass {@code null} as {@code oldEntity}.
     * For a {@code DELETE} event, pass {@code null} as {@code newEntity}.
     *
     * @param oldEntity the entity state before the change (may be {@code null})
     * @param newEntity the entity state after the change (may be {@code null})
     * @return an unmodifiable list of {@link FieldDiff} objects, one per changed field
     * @throws IllegalArgumentException if both arguments are {@code null}
     */
    public List<FieldDiff> diff(Object oldEntity, Object newEntity) {
        if (oldEntity == null && newEntity == null) {
            throw new IllegalArgumentException("At least one of oldEntity or newEntity must be non-null");
        }

        Object reference = (newEntity != null) ? newEntity : oldEntity;
        Class<?> entityClass = Hibernate.getClass(reference);
        Set<String> excluded = excludedFieldNames(entityClass);

        List<Field> allFields = getAllFields(entityClass);
        List<FieldDiff> diffs = new ArrayList<>();

        for (Field field : allFields) {
            if (excluded.contains(field.getName())) {
                continue;
            }

            field.setAccessible(true);

            Object oldValue = safeGet(field, oldEntity);
            Object newValue = safeGet(field, newEntity);

            if (!Objects.equals(oldValue, newValue)) {
                diffs.add(new FieldDiff(
                        field.getName(),
                        stringify(oldValue),
                        stringify(newValue)
                ));
            }
        }

        return Collections.unmodifiableList(diffs);
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
     * @param entityType    the audited entity class (used to resolve exclusions)
     * @param propertyNames Hibernate persister property names, aligned with the state arrays
     * @param oldState      property values before the change ({@code null} on CREATE)
     * @param newState      property values after the change ({@code null} on DELETE)
     * @return an unmodifiable list of {@link FieldDiff} objects, one per changed property
     */
    public List<FieldDiff> diff(Class<?> entityType,
                                String[] propertyNames,
                                Object[] oldState,
                                Object[] newState) {
        if (propertyNames == null) {
            throw new IllegalArgumentException("propertyNames must be non-null");
        }
        Set<String> excluded = excludedFieldNames(entityType);
        List<FieldDiff> diffs = new ArrayList<>();

        for (int i = 0; i < propertyNames.length; i++) {
            String name = propertyNames[i];
            if (excluded.contains(name)) {
                continue;
            }
            Object oldValue = (oldState != null && i < oldState.length) ? oldState[i] : null;
            Object newValue = (newState != null && i < newState.length) ? newState[i] : null;

            if (!Objects.equals(oldValue, newValue)) {
                diffs.add(new FieldDiff(name, stringify(oldValue), stringify(newValue)));
            }
        }

        return Collections.unmodifiableList(diffs);
    }

    // Helpers

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
            // Should not happen after setAccessible(true); return null gracefully.
            return null;
        }
    }

    /** Converts a value to its string representation, returning {@code null} for null inputs. */
    private String stringify(Object value) {
        return (value == null) ? null : value.toString();
    }
}
