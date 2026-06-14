package io.github.mahorobonheur.audittrail.actuator;

import io.github.mahorobonheur.audittrail.model.AuditLog;
import io.github.mahorobonheur.audittrail.repository.AuditLogRepository;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.boot.actuate.endpoint.annotation.Selector;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Spring Boot Actuator endpoint exposing audit trail statistics.
 *
 * <p>Available at {@code /actuator/audit-trail} when
 * {@code management.endpoints.web.exposure.include=audit-trail} is configured.
 *
 * <p>Supports two read operations:
 * <ul>
 *   <li>{@code /actuator/audit-trail/summary} — counts of each action type in the last 24 hours plus all-time total</li>
 *   <li>{@code /actuator/audit-trail/hotspots} — top 10 most-audited entity names in the last 24 hours</li>
 * </ul>
 *
 * @author Bonheur Mahoro
 */
@Endpoint(id = "audit-trail")
public class AuditTrailActuatorEndpoint {

    private final AuditLogRepository repository;

    public AuditTrailActuatorEndpoint(AuditLogRepository repository) {
        this.repository = repository;
    }

    /**
     * Returns either {@code summary} or {@code hotspots} statistics depending on
     * the path segment provided after {@code /actuator/audit-trail/}.
     *
     * @param operation {@code "summary"} or {@code "hotspots"}
     * @return the requested statistics map
     */
    @ReadOperation
    public Map<String, Object> info(@Selector String operation) {
        return switch (operation) {
            case "summary"  -> summary();
            case "hotspots" -> hotspots();
            default         -> summary();
        };
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private Map<String, Object> summary() {
        Instant since  = Instant.now().minus(24, ChronoUnit.HOURS);
        List<AuditLog> recent = repository.findByChangedAtAfter(since);

        long creates = recent.stream().filter(e -> e.getAction() != null
                && e.getAction().name().equals("CREATE")).count();
        long updates = recent.stream().filter(e -> e.getAction() != null
                && e.getAction().name().equals("UPDATE")).count();
        long deletes = recent.stream().filter(e -> e.getAction() != null
                && e.getAction().name().equals("DELETE")).count();
        long total   = recent.size();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("last24h", Map.of(
                "CREATE", creates,
                "UPDATE", updates,
                "DELETE", deletes,
                "total",  total
        ));
        result.put("allTime", repository.count());
        return result;
    }

    private Map<String, Object> hotspots() {
        Instant since  = Instant.now().minus(24, ChronoUnit.HOURS);
        List<AuditLog> recent = repository.findByChangedAtAfter(since);

        Map<String, Long> counts = recent.stream()
                .collect(Collectors.groupingBy(AuditLog::getEntityName, Collectors.counting()));

        // Sort by count descending and return top 10
        List<Map<String, Object>> top10 = counts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(10)
                .map(e -> Map.<String, Object>of("entityName", e.getKey(), "count", e.getValue()))
                .toList();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("hotspots", top10);
        result.put("windowHours", 24);
        return result;
    }
}
