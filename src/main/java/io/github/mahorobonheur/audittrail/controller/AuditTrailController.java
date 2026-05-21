package io.github.mahorobonheur.audittrail.controller;

import io.github.mahorobonheur.audittrail.config.AuditTrailProperties;
import io.github.mahorobonheur.audittrail.model.AuditLog;
import io.github.mahorobonheur.audittrail.repository.AuditLogRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller exposing read-only access to the audit trail.
 *
 * <p>The base path defaults to {@code /audit-trail} and can be customised
 * via {@code audit-trail.rest.base-path} in your application configuration.
 *
 * <h2>Endpoints</h2>
 * <pre>
 * GET /audit-trail/{entityName}/{entityId}
 *     Returns paginated audit history for a specific record.
 *     Query params: page (default 0), size (default 20)
 *
 * GET /audit-trail/{entityName}
 *     Returns paginated audit history for all records of a given entity type.
 *     Query params: page (default 0), size (default 20)
 * </pre>
 *
 * <p>Results are always sorted by {@code changedAt} descending (most recent first).
 *
 * @author Bonheur Mahoro
 */
@RestController
@RequestMapping("${audit-trail.rest.base-path:/audit-trail}")
public class AuditTrailController {

    private final AuditLogRepository repository;
    private final AuditTrailProperties properties;

    public AuditTrailController(AuditLogRepository repository, AuditTrailProperties properties) {
        this.repository = repository;
        this.properties = properties;
    }

    /**
     * Returns the paginated audit history for a specific entity record.
     *
     * @param entityName the simple class name of the entity (case-sensitive, e.g. {@code User})
     * @param entityId   the string representation of the record's primary key
     * @param page       zero-based page index (default {@code 0})
     * @param size       maximum number of entries per page (default {@code 20})
     * @return a {@code 200 OK} response containing a {@link Page} of {@link AuditLog} entries
     */
    @GetMapping("/{entityName}/{entityId}")
    public ResponseEntity<Page<AuditLog>> getHistory(
            @PathVariable String entityName,
            @PathVariable String entityId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Pageable pageable = PageRequest.of(page, size, Sort.by("changedAt").descending());
        Page<AuditLog> result = repository.findByEntityNameAndEntityId(entityName, entityId, pageable);
        return ResponseEntity.ok(result);
    }

    /**
     * Returns the paginated audit history for all records of a given entity type.
     *
     * @param entityName the simple class name of the entity (case-sensitive, e.g. {@code User})
     * @param page       zero-based page index (default {@code 0})
     * @param size       maximum number of entries per page (default {@code 20})
     * @return a {@code 200 OK} response containing a {@link Page} of {@link AuditLog} entries
     */
    @GetMapping("/{entityName}")
    public ResponseEntity<Page<AuditLog>> getHistoryByEntity(
            @PathVariable String entityName,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Pageable pageable = PageRequest.of(page, size, Sort.by("changedAt").descending());
        Page<AuditLog> result = repository.findByEntityName(entityName, pageable);
        return ResponseEntity.ok(result);
    }
}
