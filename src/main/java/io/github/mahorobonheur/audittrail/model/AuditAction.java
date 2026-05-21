package io.github.mahorobonheur.audittrail.model;

/**
 * Represents the type of change recorded in an audit log entry.
 *
 * @author Bonheur Mahoro
 */
public enum AuditAction {

    /** A new entity record was persisted. */
    CREATE,

    /** An existing entity record was modified. */
    UPDATE,

    /** An entity record was removed. */
    DELETE
}
