package io.github.mahorobonheur.audittrail.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.mahorobonheur.audittrail.model.AuditLog;
import io.github.mahorobonheur.audittrail.repository.AuditLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reconstructs the state of an entity at a given point in time by replaying
 * all audit log entries up to (and including) that instant.
 *
 * <p>The reconstruction algorithm walks the entries in chronological order
 * ({@code changedAt} ascending), applying each diff to a running state map.
 * For CREATE and UPDATE entries, {@code newValue} is applied; for DELETE entries,
 * fields are set to {@code null}.
 *
 * <p>The returned map uses field names as keys and the last recorded string
 * representations as values. This is necessarily a best-effort reconstruction
 * because:
 * <ul>
 *   <li>Masked fields show the placeholder string, not the real value.</li>
 *   <li>Only fields that appeared in at least one diff are included.</li>
 *   <li>The map reflects the <em>recorded</em> string values, not typed Java objects.</li>
 * </ul>
 *
 * @author Bonheur Mahoro
 */
@Service
public class AuditReconstructionService {

    private static final Logger log = LoggerFactory.getLogger(AuditReconstructionService.class);

    private final AuditLogRepository repository;
    private final ObjectMapper        objectMapper;

    public AuditReconstructionService(AuditLogRepository repository, ObjectMapper objectMapper) {
        this.repository   = repository;
        this.objectMapper = objectMapper;
    }

    /**
     * Reconstructs the entity state as it existed at {@code at}.
     *
     * @param entityName simple class name of the entity (e.g. {@code "User"})
     * @param entityId   string primary key of the entity record
     * @param at         the point in time to reconstruct state at (inclusive)
     * @return a mutable map of field name → last recorded value up to {@code at};
     *         empty map if no audit entries exist before {@code at}
     */
    public Map<String, Object> reconstruct(String entityName, String entityId, Instant at) {
        List<AuditLog> entries = repository
                .findByEntityNameAndEntityIdOrderByChangedAtAsc(entityName, entityId)
                .stream()
                .filter(e -> !e.getChangedAt().isAfter(at))
                .toList();

        Map<String, Object> state = new LinkedHashMap<>();

        for (AuditLog entry : entries) {
            try {
                List<Map<String, String>> diffs = objectMapper.readValue(
                        entry.getFieldDiffs(),
                        new TypeReference<List<Map<String, String>>>() { });

                for (Map<String, String> diff : diffs) {
                    String field    = diff.get("field");
                    String newValue = diff.get("newValue");
                    if (field != null) {
                        state.put(field, newValue);
                    }
                }
            } catch (Exception e) {
                log.warn("Failed to parse fieldDiffs for audit entry {}: {}", entry.getId(), e.getMessage());
            }
        }

        return state;
    }
}
