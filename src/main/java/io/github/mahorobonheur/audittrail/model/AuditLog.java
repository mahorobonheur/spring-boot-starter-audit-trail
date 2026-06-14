package io.github.mahorobonheur.audittrail.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * JPA entity that represents a single entry in the audit log table.
 *
 * <p>The table name defaults to {@code audit_log} and can be customised via
 * the {@code audit-trail.table-name} configuration property.
 *
 * <p>The {@code fieldDiffs} column stores a JSON array of
 * {@code {"field":"...","oldValue":"...","newValue":"..."}} objects,
 * serialised by {@link io.github.mahorobonheur.audittrail.writer.DatabaseAuditLogWriter}.
 *
 * @author Bonheur Mahoro
 */
@Entity(name = "AuditTrailLog")
@Table(name = "audit_log")
public class AuditLog {

    /** Surrogate primary key — UUID stored as a VARCHAR for broad DB compatibility. */
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private String id;

    /** Simple class name of the audited entity (e.g., {@code "User"}). */
    @Column(name = "entity_name", nullable = false, length = 255)
    private String entityName;

    /**
     * String representation of the audited entity's primary key.
     * Coerced to {@code String} to remain generic across all ID types.
     */
    @Column(name = "entity_id", nullable = false, length = 255)
    private String entityId;

    /** The type of change: CREATE, UPDATE, or DELETE. */
    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false, length = 10)
    private AuditAction action;

    /** Username resolved from Spring Security at the time of the change. */
    @Column(name = "changed_by", nullable = false, length = 255)
    private String changedBy;

    /** UTC instant at which the change was recorded. */
    @Column(name = "changed_at", nullable = false, updatable = false)
    private Instant changedAt;

    /**
     * JSON array of field-level diffs, e.g.:
     * {@code [{"field":"email","oldValue":"a@x.com","newValue":"b@x.com"}]}
     *
     * <p>Stored as {@code TEXT} / {@code CLOB} to accommodate arbitrarily large diffs.
     */
    @Column(name = "field_diffs", columnDefinition = "TEXT")
    private String fieldDiffs;

    /**
     * Business reason / justification for the change, set via
     * {@link io.github.mahorobonheur.audittrail.annotation.AuditWhy} or
     * {@link io.github.mahorobonheur.audittrail.context.AuditWhyContext}.
     * {@code null} when no reason was provided.
     */
    @Column(name = "why_reason", columnDefinition = "TEXT")
    private String whyReason;

    /**
     * {@code true} when at least one field in {@code fieldDiffs} was masked
     * via {@link io.github.mahorobonheur.audittrail.annotation.AuditMask}.
     */
    @Column(name = "is_masked", nullable = false)
    private boolean masked;

    /**
     * Optional label identifying a manual snapshot point, populated when the
     * entry originates from
     * {@link io.github.mahorobonheur.audittrail.service.AuditSnapshotService}.
     */
    @Column(name = "snapshot_label", length = 255)
    private String snapshotLabel;

    /**
     * SHA-256 hash linking this entry to the previous entry for this entity+id,
     * forming a tamper-evident chain. {@code null} when chain hashing is disabled
     * ({@code audit-trail.chain.enabled=false}, the default) or for the first entry.
     */
    @Column(name = "prev_hash", length = 64)
    private String prevHash;

    // ── Constructors ─────────────────────────────────────────────────────────

    protected AuditLog() {
        // JPA requires a no-arg constructor
    }

    /**
     * Original 6-parameter constructor — preserved for backwards compatibility.
     */
    public AuditLog(String entityName,
                    String entityId,
                    AuditAction action,
                    String changedBy,
                    Instant changedAt,
                    String fieldDiffs) {
        this.entityName = entityName;
        this.entityId   = entityId;
        this.action     = action;
        this.changedBy  = changedBy;
        this.changedAt  = changedAt;
        this.fieldDiffs = fieldDiffs;
    }

    /**
     * Full 10-parameter constructor including all v2 fields.
     */
    public AuditLog(String entityName,
                    String entityId,
                    AuditAction action,
                    String changedBy,
                    Instant changedAt,
                    String fieldDiffs,
                    String whyReason,
                    boolean masked,
                    String snapshotLabel,
                    String prevHash) {
        this.entityName    = entityName;
        this.entityId      = entityId;
        this.action        = action;
        this.changedBy     = changedBy;
        this.changedAt     = changedAt;
        this.fieldDiffs    = fieldDiffs;
        this.whyReason     = whyReason;
        this.masked        = masked;
        this.snapshotLabel = snapshotLabel;
        this.prevHash      = prevHash;
    }

    // ── Getters ──────────────────────────────────────────────────────────────

    public String getId()              { return id; }
    public String getEntityName()      { return entityName; }
    public String getEntityId()        { return entityId; }
    public AuditAction getAction()     { return action; }
    public String getChangedBy()       { return changedBy; }
    public Instant getChangedAt()      { return changedAt; }
    public String getFieldDiffs()      { return fieldDiffs; }
    public String getWhyReason()       { return whyReason; }
    public boolean isMasked()          { return masked; }
    public String getSnapshotLabel()   { return snapshotLabel; }
    public String getPrevHash()        { return prevHash; }

    // ── Setters (new fields only; existing fields are immutable after construction) ──

    public void setWhyReason(String whyReason)       { this.whyReason = whyReason; }
    public void setMasked(boolean masked)             { this.masked = masked; }
    public void setSnapshotLabel(String snapshotLabel){ this.snapshotLabel = snapshotLabel; }
    public void setPrevHash(String prevHash)          { this.prevHash = prevHash; }
}
