package io.github.mahorobonheur.audittrail.repository;

import io.github.mahorobonheur.audittrail.model.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA repository for {@link AuditLog} entries.
 *
 * <p>Provides out-of-the-box CRUD operations plus targeted query methods
 * used by the REST controller, reconstruction service, chain service,
 * and actuator endpoint.
 *
 * @author Bonheur Mahoro
 */
@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, String>,
        org.springframework.data.jpa.repository.JpaSpecificationExecutor<AuditLog> {

    /**
     * Returns all audit log entries for a given entity type and record ID,
     * ordered by the caller-supplied {@link Pageable}.
     */
    Page<AuditLog> findByEntityNameAndEntityId(String entityName, String entityId, Pageable pageable);

    /**
     * Returns all audit log entries for a given entity type across all record IDs.
     */
    Page<AuditLog> findByEntityName(String entityName, Pageable pageable);

    /**
     * Returns all audit log entries whose {@code changedAt} is after the given instant.
     * Used by the actuator endpoint for 24-hour window statistics.
     */
    List<AuditLog> findByChangedAtAfter(Instant after);

    /**
     * Returns all audit log entries for a given entity+id, ordered by
     * {@code changedAt} ascending then {@code id} ascending (oldest first, stable).
     * The secondary sort on {@code id} prevents non-deterministic ordering when multiple
     * entries share the exact same timestamp — which would otherwise cause false
     * "chain broken" results during chain verification.
     * Used by the reconstruction service and chain verification.
     */
    List<AuditLog> findByEntityNameAndEntityIdOrderByChangedAtAscIdAsc(String entityName, String entityId);

    /**
     * Returns the most recent audit log entry for a given entity+id.
     * The secondary sort on {@code id} descending ensures a stable "last entry" when
     * multiple entries share the exact same timestamp — critical for computing a
     * consistent {@code prevHash} during chain write.
     * Used by {@link io.github.mahorobonheur.audittrail.service.AuditChainService}
     * to determine the previous hash when building the chain.
     */
    Optional<AuditLog> findTopByEntityNameAndEntityIdOrderByChangedAtDescIdDesc(String entityName, String entityId);

    /**
     * Returns all audit log entries after {@code after} for a specific entity type.
     * Used by the actuator endpoint hotspot analysis when filtered by entity.
     */
    List<AuditLog> findByChangedAtAfterAndEntityName(Instant after, String entityName);

    // ── Dashboard queries ─────────────────────────────────────────────────────

    /** Returns the 50 most recently changed audit log entries. Used by the dashboard. */
    List<AuditLog> findTop50ByOrderByChangedAtDesc();

    /** Returns the most recent audit log entry for a given entity type. */
    Optional<AuditLog> findTopByEntityNameOrderByChangedAtDesc(String entityName);

    /**
     * Returns all distinct entity class names present in the audit log.
     *
     * <p>Uses native SQL to avoid JPQL entity-name resolution, which would require
     * the library's {@code AuditLog} entity to be registered in the consuming app's
     * JPA persistence unit. Assumes the default table name {@code audit_log}; if you
     * have set {@code audit-trail.table-name} to a custom value, provide a local
     * override of this method with the matching table name.
     */
    @Query(value = "SELECT DISTINCT entity_name FROM audit_log ORDER BY entity_name",
           nativeQuery = true)
    List<String> findDistinctEntityNames();

    /**
     * Returns all distinct actor names ({@code changed_by}) present in the audit log.
     *
     * <p>Uses native SQL — see {@link #findDistinctEntityNames()} for the rationale
     * and the table-name caveat.
     */
    @Query(value = "SELECT DISTINCT changed_by FROM audit_log ORDER BY changed_by",
           nativeQuery = true)
    List<String> findDistinctActors();

    /**
     * Returns a count per entity name as {@code Object[]{entityName, count}},
     * ordered by count descending. Used by the dashboard entity summary panel.
     *
     * <p>Uses native SQL — see {@link #findDistinctEntityNames()} for the rationale
     * and the table-name caveat. The count is returned as a {@link Number}; callers
     * should use {@code ((Number) row[1]).longValue()} for portability across databases.
     */
    @Query(value = "SELECT entity_name, COUNT(*) AS cnt FROM audit_log GROUP BY entity_name ORDER BY cnt DESC",
           nativeQuery = true)
    List<Object[]> countGroupedByEntityName();
}
