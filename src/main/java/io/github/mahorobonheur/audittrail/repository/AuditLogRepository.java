package io.github.mahorobonheur.audittrail.repository;

import io.github.mahorobonheur.audittrail.model.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for {@link AuditLog} entries.
 *
 * <p>Provides out-of-the-box CRUD operations plus targeted query methods
 * used by the REST controller to return paginated audit history for a
 * specific entity record.
 *
 * @author Bonheur Mahoro
 */
@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, String> {

    /**
     * Returns all audit log entries for a given entity type and record ID,
     * ordered by the caller-supplied {@link Pageable} (typically by {@code changedAt} descending).
     *
     * @param entityName the simple class name of the entity (e.g. {@code "User"})
     * @param entityId   the string representation of the entity's primary key
     * @param pageable   pagination and sorting specification
     * @return a page of matching audit log entries
     */
    Page<AuditLog> findByEntityNameAndEntityId(String entityName, String entityId, Pageable pageable);

    /**
     * Returns all audit log entries for a given entity type across all record IDs.
     *
     * @param entityName the simple class name of the entity
     * @param pageable   pagination and sorting specification
     * @return a page of matching audit log entries
     */
    Page<AuditLog> findByEntityName(String entityName, Pageable pageable);
}
