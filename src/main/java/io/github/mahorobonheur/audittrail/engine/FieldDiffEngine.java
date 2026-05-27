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

        AuditTrail annotation = entityClass.getAnnotation(AuditTrail.class);
        Set<String> excludedByAnnotation = (annotation != null)
                ? new HashSet<>(Arrays.asList(annotation.exclude()))
                : Collections.emptySet();

        List<Field> allFields = getAllFields(entityClass);
        List<FieldDiff> diffs = new ArrayList<>();

        for (Field field : allFields) {
            // Skip fields excluded via @AuditTrail(exclude = {...})
            if (excludedByAnnotation.contains(field.getName())) {
                continue;
            }
            // Skip fields annotated with @AuditExclude
            if (field.isAnnotationPresent(AuditExclude.class)) {
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

    // Helpers

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
