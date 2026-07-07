package io.github.mahorobonheur.audittrail.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.mahorobonheur.audittrail.config.AuditTrailProperties;
import io.github.mahorobonheur.audittrail.model.AuditLog;
import io.github.mahorobonheur.audittrail.repository.AuditLogRepository;
import io.github.mahorobonheur.audittrail.service.AuditChainService;
import io.github.mahorobonheur.audittrail.service.AuditReconstructionService;
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

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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
 *
 * GET /audit-trail/{entityName}
 *     Returns paginated audit history for all records of a given entity type.
 *
 * GET /audit-trail/{entityName}/{entityId}/diff?from={id1}&amp;to={id2}
 *     Returns a comparison of two specific audit log entries.
 *
 * GET /audit-trail/{entityName}/{entityId}/verify
 *     Verifies the chain integrity for a record (requires chain hashing enabled).
 *
 * GET /audit-trail/{entityName}/{entityId}/reconstruct?at={isoInstant}
 *     Reconstructs the entity state at the given point in time.
 * </pre>
 *
 * @author Bonheur Mahoro
 */
@RestController
@RequestMapping("${audit-trail.rest.base-path:/audit-trail}")
public class AuditTrailController {

    private final AuditLogRepository          repository;
    private final AuditTrailProperties        properties;
    private final Optional<AuditChainService> chainService;
    private final AuditReconstructionService  reconstructionService;
    private final ObjectMapper                objectMapper;

    public AuditTrailController(AuditLogRepository repository,
                                 AuditTrailProperties properties,
                                 Optional<AuditChainService> chainService,
                                 AuditReconstructionService reconstructionService,
                                 ObjectMapper objectMapper) {
        this.repository            = repository;
        this.properties            = properties;
        this.chainService          = chainService;
        this.reconstructionService = reconstructionService;
        this.objectMapper          = objectMapper;
    }

    /**
     * Converts an {@link AuditLog} entity to a plain {@code Map} suitable for
     * JSON serialization by any Jackson version.  The {@code fieldDiffs} string
     * (which is a stored JSON array) is parsed back to a {@code List} so it
     * serializes as a proper JSON array rather than an escaped string.
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> toResponse(AuditLog entry) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id",            entry.getId());
        m.put("entityName",    entry.getEntityName());
        m.put("entityId",      entry.getEntityId());
        m.put("action",        entry.getAction());
        m.put("changedBy",     entry.getChangedBy());
        m.put("changedAt",     entry.getChangedAt());
        m.put("masked",        entry.isMasked());
        m.put("whyReason",     entry.getWhyReason());
        m.put("snapshotLabel", entry.getSnapshotLabel());
        m.put("prevHash",      entry.getPrevHash());
        try {
            m.put("fieldDiffs", objectMapper.readValue(entry.getFieldDiffs(), List.class));
        } catch (Exception ignored) {
            m.put("fieldDiffs", entry.getFieldDiffs());
        }
        return m;
    }

    /**
     * Returns the paginated audit history for a specific entity record.
     * Both trailing-slash and non-trailing-slash forms are accepted because
     * Spring 6+ no longer matches them interchangeably by default.
     */
    @GetMapping({"/{entityName}/{entityId}", "/{entityName}/{entityId}/"})
    public ResponseEntity<Page<Map<String, Object>>> getHistory(
            @PathVariable String entityName,
            @PathVariable String entityId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Pageable pageable = PageRequest.of(page, size, Sort.by("changedAt").descending());
        return ResponseEntity.ok(
                repository.findByEntityNameAndEntityId(entityName, entityId, pageable)
                          .map(this::toResponse));
    }

    /**
     * Returns the paginated audit history for all records of a given entity type.
     */
    @GetMapping({"/{entityName}", "/{entityName}/"})
    public ResponseEntity<Page<Map<String, Object>>> getHistoryByEntity(
            @PathVariable String entityName,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        // "dashboard" is a reserved path segment — return 404 when the dashboard is disabled
        if ("dashboard".equalsIgnoreCase(entityName)) {
            return ResponseEntity.notFound().build();
        }
        Pageable pageable = PageRequest.of(page, size, Sort.by("changedAt").descending());
        return ResponseEntity.ok(
                repository.findByEntityName(entityName, pageable)
                          .map(this::toResponse));
    }

    /**
     * Compares two specific audit log entries, returning both entries side by side.
     *
     * @param entityName the entity class name
     * @param entityId   the entity's primary key
     * @param from       the id of the "before" audit log entry
     * @param to         the id of the "after" audit log entry
     * @return a map with keys {@code "from"} and {@code "to"} containing the respective entries,
     *         or {@code 404} if either entry is not found
     */
    @GetMapping("/{entityName}/{entityId}/diff")
    public ResponseEntity<Map<String, Object>> getDiff(
            @PathVariable String entityName,
            @PathVariable String entityId,
            @RequestParam String from,
            @RequestParam String to) {

        Optional<AuditLog> fromEntry = repository.findById(from);
        Optional<AuditLog> toEntry   = repository.findById(to);

        if (fromEntry.isEmpty() || toEntry.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("entityName", entityName);
        result.put("entityId",   entityId);
        result.put("from",       toResponse(fromEntry.get()));
        result.put("to",         toResponse(toEntry.get()));
        return ResponseEntity.ok(result);
    }

    /**
     * Verifies the tamper-evident chain for all audit entries of the given entity record.
     *
     * <p>Returns {@code 501 Not Implemented} when chain hashing is disabled.
     *
     * @param entityName the entity class name
     * @param entityId   the entity's primary key
     * @return chain verification result with {@code valid} flag and optional {@code brokenAtId}
     */
    @GetMapping("/{entityName}/{entityId}/verify")
    public ResponseEntity<Map<String, Object>> verifyChain(
            @PathVariable String entityName,
            @PathVariable String entityId) {

        if (chainService.isEmpty()) {
            return ResponseEntity.status(501)
                    .body(Map.of("error", "Chain hashing is not enabled. Set audit-trail.chain.enabled=true."));
        }

        List<AuditLog> entries = repository
                .findByEntityNameAndEntityIdOrderByChangedAtAscIdAsc(entityName, entityId);

        AuditChainService.ChainVerificationResult result = chainService.get().verifyChain(entries);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("entityName",  entityName);
        body.put("entityId",    entityId);
        body.put("entryCount",  entries.size());
        body.put("valid",       result.valid());
        if (!result.valid()) {
            body.put("brokenAtId", result.brokenAtId());
        }
        return ResponseEntity.ok(body);
    }

    /**
     * Reconstructs the entity state at the given point in time by replaying all
     * audit log entries up to and including {@code at}.
     *
     * @param entityName the entity class name
     * @param entityId   the entity's primary key
     * @param at         ISO-8601 instant string (e.g. {@code 2026-06-14T10:00:00Z})
     * @return a map of field name → value representing the reconstructed state
     */
    @GetMapping("/{entityName}/{entityId}/reconstruct")
    public ResponseEntity<Map<String, Object>> reconstruct(
            @PathVariable String entityName,
            @PathVariable String entityId,
            @RequestParam String at) {

        Instant instant;
        try {
            instant = Instant.parse(at);
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Invalid 'at' parameter. Expected ISO-8601 instant, e.g. 2026-06-14T10:00:00Z"));
        }

        Map<String, Object> state = reconstructionService.reconstruct(entityName, entityId, instant);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("entityName",    entityName);
        body.put("entityId",      entityId);
        body.put("reconstructAt", instant.toString());
        body.put("state",         state);
        return ResponseEntity.ok(body);
    }
}
