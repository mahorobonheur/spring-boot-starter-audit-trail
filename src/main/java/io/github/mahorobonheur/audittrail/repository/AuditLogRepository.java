package io.github.mahorobonheur.audittrail.repository;

import io.github.mahorobonheur.audittrail.model.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
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
public interface AuditLogRepository extends JpaRepository<AuditLog, String> {

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
     * {@code changedAt} ascending (oldest first). Used by the reconstruction service
     * and chain verification.
     */
    List<AuditLog> findByEntityNameAndEntityIdOrderByChangedAtAsc(String entityName, String entityId);

    /**
     * Returns the most recent audit log entry for a given entity+id.
     * Used by {@link io.github.mahorobonheur.audittrail.service.AuditChainService}
     * to determine the previous hash when building the chain.
     */
    Optional<AuditLog> findTopByEntityNameAndEntityIdOrderByChangedAtDesc(String entityName, String entityId);

    /**
     * Returns all audit log entries after {@code after} for a specific entity type.
     * Used by the actuator endpoint hotspot analysis when filtered by entity.
     */
    List<AuditLog> findByChangedAtAfterAndEntityName(Instant after, String entityName);
}
