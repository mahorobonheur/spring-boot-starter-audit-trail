package io.github.mahorobonheur.audittrail.model;

/**
 * Represents a single field-level change captured during an audit event.
 *
 * <p>Each instance records the field name, the value before the change,
 * and the value after the change. Both {@code oldValue} and {@code newValue}
 * are stored as {@code String} representations to remain database-agnostic.
 * A {@code null} value means the field had no value (e.g., on CREATE the
 * {@code oldValue} will be {@code null}).
 *
 * @param field    the name of the changed field (as declared in the Java class)
 * @param oldValue string representation of the value before the change, or {@code null}
 * @param newValue string representation of the value after the change, or {@code null}
 *
 * @author Bonheur Mahoro
 */
public record FieldDiff(String field, String oldValue, String newValue) {

    /**
     * Returns a human-readable description of the change.
     *
     * @return e.g. {@code "email: 'old@x.com' → 'new@x.com'"}
     */
    @Override
    public String toString() {
        return String.format("%s: '%s' → '%s'", field, oldValue, newValue);
    }
}
