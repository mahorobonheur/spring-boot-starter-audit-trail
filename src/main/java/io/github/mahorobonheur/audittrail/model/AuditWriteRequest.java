package io.github.mahorobonheur.audittrail.model;

import java.util.Collections;
import java.util.List;

/**
 * Immutable value object carrying all data needed to write a single audit entry.
 *
 * <p>Use the static {@link #builder()} factory method for the fluent-builder API,
 * or {@link #from(String, String, AuditAction, String, List)} for quick construction
 * from the legacy parameter set (backwards-compatible with v1 callers).
 *
 * @author Bonheur Mahoro
 */
public final class AuditWriteRequest {

    private final String      entityName;
    private final String      entityId;
    private final AuditAction action;
    private final String      changedBy;
    private final List<FieldDiff> diffs;
    private final String      whyReason;
    private final String      snapshotLabel;
    private final boolean     hasMaskedFields;

    private AuditWriteRequest(Builder b) {
        this.entityName      = b.entityName;
        this.entityId        = b.entityId;
        this.action          = b.action;
        this.changedBy       = b.changedBy;
        this.diffs           = b.diffs == null
                ? Collections.emptyList()
                : Collections.unmodifiableList(b.diffs);
        this.whyReason       = b.whyReason;
        this.snapshotLabel   = b.snapshotLabel;
        this.hasMaskedFields = b.hasMaskedFields;
    }

    // ── Factory methods ──────────────────────────────────────────────────────

    /**
     * Returns a new {@link Builder}.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Convenience factory that maps the legacy 5-parameter signature to a request
     * with no {@code whyReason}, no {@code snapshotLabel}, and {@code hasMaskedFields=false}.
     *
     * @param entityName audited entity class name
     * @param entityId   string representation of the entity's primary key
     * @param action     the type of change
     * @param changedBy  authenticated username
     * @param diffs      field-level diffs
     * @return a fully populated {@link AuditWriteRequest}
     */
    public static AuditWriteRequest from(String entityName,
                                         String entityId,
                                         AuditAction action,
                                         String changedBy,
                                         List<FieldDiff> diffs) {
        return builder()
                .entityName(entityName)
                .entityId(entityId)
                .action(action)
                .changedBy(changedBy)
                .diffs(diffs)
                .build();
    }

    // ── Getters ──────────────────────────────────────────────────────────────

    public String          getEntityName()      { return entityName; }
    public String          getEntityId()        { return entityId; }
    public AuditAction     getAction()          { return action; }
    public String          getChangedBy()       { return changedBy; }
    public List<FieldDiff> getDiffs()           { return diffs; }
    public String          getWhyReason()       { return whyReason; }
    public String          getSnapshotLabel()   { return snapshotLabel; }
    public boolean         isHasMaskedFields()  { return hasMaskedFields; }

    // ── Builder ──────────────────────────────────────────────────────────────

    public static final class Builder {

        private String          entityName;
        private String          entityId;
        private AuditAction     action;
        private String          changedBy;
        private List<FieldDiff> diffs;
        private String          whyReason;
        private String          snapshotLabel;
        private boolean         hasMaskedFields;

        private Builder() { }

        public Builder entityName(String entityName)          { this.entityName = entityName; return this; }
        public Builder entityId(String entityId)              { this.entityId = entityId; return this; }
        public Builder action(AuditAction action)             { this.action = action; return this; }
        public Builder changedBy(String changedBy)            { this.changedBy = changedBy; return this; }
        public Builder diffs(List<FieldDiff> diffs)           { this.diffs = diffs; return this; }
        public Builder whyReason(String whyReason)            { this.whyReason = whyReason; return this; }
        public Builder snapshotLabel(String snapshotLabel)    { this.snapshotLabel = snapshotLabel; return this; }
        public Builder hasMaskedFields(boolean hasMasked)     { this.hasMaskedFields = hasMasked; return this; }

        /**
         * Builds and returns the immutable {@link AuditWriteRequest}.
         *
         * @return a new {@link AuditWriteRequest}
         * @throws IllegalStateException if {@code entityName}, {@code entityId},
         *                               {@code action}, or {@code changedBy} is null
         */
        public AuditWriteRequest build() {
            if (entityName == null) throw new IllegalStateException("entityName must not be null");
            if (entityId   == null) throw new IllegalStateException("entityId must not be null");
            if (action     == null) throw new IllegalStateException("action must not be null");
            if (changedBy  == null) throw new IllegalStateException("changedBy must not be null");
            return new AuditWriteRequest(this);
        }
    }
}
