package io.github.mahorobonheur.audittrail.controller;

import io.github.mahorobonheur.audittrail.model.AuditAction;
import io.github.mahorobonheur.audittrail.model.AuditLog;
import io.github.mahorobonheur.audittrail.repository.AuditLogRepository;
import jakarta.persistence.criteria.Predicate;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.MediaType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Serves the audit trail interactive dashboard UI and its backing JSON data endpoints.
 *
 * <p>Enable with {@code audit-trail.dashboard.enabled=true}. The UI is then available
 * at {@code {rest.basePath}/dashboard} (default: {@code /audit-trail/dashboard}).
 *
 * <p>The class-level {@code @RequestMapping} uses the {@code ${audit-trail.rest.base-path}}
 * property placeholder, which Spring MVC resolves at startup from the application context's
 * {@code Environment}. This avoids any dependency on runtime SpEL evaluation inside
 * annotation attributes.
 *
 * @author Bonheur Mahoro
 */
@Controller
@RequestMapping("${audit-trail.rest.base-path:/audit-trail}")
public class AuditTrailDashboardController {

    private static final DateTimeFormatter DAY_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneOffset.UTC);

    private final AuditLogRepository repository;
    private final String basePath;

    public AuditTrailDashboardController(
            AuditLogRepository repository,
            @Value("${audit-trail.rest.base-path:/audit-trail}") String basePath) {
        this.repository = repository;
        this.basePath   = basePath.endsWith("/") ? basePath.substring(0, basePath.length() - 1) : basePath;
    }

    // ── HTML shell ────────────────────────────────────────────────────────────

    /**
     * Serves the single-page dashboard HTML from the classpath resource
     * {@code audit-trail/dashboard.html}, substituting {@code {{BASE_PATH}}} with
     * the configured REST base path so the frontend knows where to send API calls.
     */
    @GetMapping("/dashboard")
    public void dashboardHtml(HttpServletRequest req, HttpServletResponse res) throws IOException {
        ClassPathResource resource = new ClassPathResource("audit-trail/dashboard.html");
        String html = resource.getContentAsString(StandardCharsets.UTF_8);
        html = html.replace("{{BASE_PATH}}", basePath);
        res.setContentType(MediaType.TEXT_HTML_VALUE + ";charset=UTF-8");
        res.setCharacterEncoding("UTF-8");
        res.setHeader("Cache-Control", "no-store");
        res.getWriter().write(html);
    }

    // ── Stats overview ────────────────────────────────────────────────────────

    /**
     * Returns aggregate statistics for the dashboard Overview tab.
     *
     * <p>Response fields:
     * <ul>
     *   <li>{@code totalLogs} — total audit log count</li>
     *   <li>{@code uniqueEntities} — distinct entity type count</li>
     *   <li>{@code changesToday} — entries created in the last 24 h</li>
     *   <li>{@code uniqueActors} — distinct actor count</li>
     *   <li>{@code activityByDay} — ordered map of date → count for the last 14 days</li>
     * </ul>
     */
    @GetMapping("/dashboard/api/stats")
    @ResponseBody
    public Map<String, Object> stats() {
        long total          = repository.count();
        long uniqueActors   = repository.findDistinctActors().size();
        long uniqueEntities = repository.findDistinctEntityNames().size();

        Instant oneDayAgo = Instant.now().minus(1, ChronoUnit.DAYS);
        long today = repository.findByChangedAtAfter(oneDayAgo).size();

        Instant fourteenDaysAgo = Instant.now().minus(14, ChronoUnit.DAYS);
        List<AuditLog> recent14 = repository.findByChangedAtAfter(fourteenDaysAgo);

        Map<String, Long> histogram = new TreeMap<>();
        for (int i = 13; i >= 0; i--) {
            histogram.put(DAY_FMT.format(Instant.now().minus(i, ChronoUnit.DAYS)), 0L);
        }
        for (AuditLog log : recent14) {
            String day = DAY_FMT.format(log.getChangedAt());
            histogram.merge(day, 1L, Long::sum);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalLogs",      total);
        result.put("uniqueEntities", uniqueEntities);
        result.put("changesToday",   today);
        result.put("uniqueActors",   uniqueActors);
        result.put("activityByDay",  histogram);
        return result;
    }

    // ── Entity summary ────────────────────────────────────────────────────────

    /**
     * Returns a list of entity types with their audit log counts and last-changed timestamps.
     * Used to populate the entity distribution chart and entity table on the Overview tab.
     */
    @GetMapping("/dashboard/api/entities")
    @ResponseBody
    public List<Map<String, Object>> entities() {
        List<Object[]> counts = repository.countGroupedByEntityName();
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object[] row : counts) {
            String entityName  = (String) row[0];
            long   count       = ((Number) row[1]).longValue();
            String lastChanged = repository
                    .findTopByEntityNameOrderByChangedAtDesc(entityName)
                    .map(log -> log.getChangedAt().toString())
                    .orElse(null);
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("name",        entityName);
            entry.put("count",       count);
            entry.put("lastChanged", lastChanged);
            result.add(entry);
        }
        return result;
    }

    // ── Recent activity ───────────────────────────────────────────────────────

    /**
     * Returns the 50 most recent audit log entries as plain maps.
     * Used to populate the Recent Activity table on the Overview tab.
     */
    @GetMapping("/dashboard/api/recent")
    @ResponseBody
    public List<Map<String, Object>> recent() {
        return repository.findTop50ByOrderByChangedAtDesc()
                .stream()
                .map(AuditTrailDashboardController::toMap)
                .collect(Collectors.toList());
    }

    // ── Filtered + paginated logs ─────────────────────────────────────────────

    /**
     * Returns a filtered, paginated page of audit log entries for the dashboard.
     *
     * <p>All parameters are optional — omitting them returns all entries ordered by
     * {@code changedAt} descending.
     *
     * @param page     zero-based page number (default 0)
     * @param size     page size, capped at 200 (default 20)
     * @param entity   exact entity class name filter (e.g. {@code "User"})
     * @param action   action filter: {@code CREATE}, {@code UPDATE}, or {@code DELETE}
     * @param actor    partial case-insensitive match on {@code changedBy}
     * @param from     ISO-8601 instant — include entries at or after this time
     * @param to       ISO-8601 instant — include entries at or before this time
     * @param entityId exact entity primary key filter
     */
    @GetMapping("/dashboard/api/logs")
    @ResponseBody
    public Map<String, Object> logs(
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String entity,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String actor,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) String entityId) {

        Specification<AuditLog> spec = buildSpec(entity, action, actor, from, to, entityId);
        org.springframework.data.domain.Pageable pageable = PageRequest.of(
                Math.max(0, page),
                Math.min(Math.max(1, size), 200),
                Sort.by("changedAt").descending());
        Page<AuditLog> result = repository.findAll(spec, pageable);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("content",       result.getContent().stream()
                .map(AuditTrailDashboardController::toMap).collect(Collectors.toList()));
        response.put("totalElements", result.getTotalElements());
        response.put("totalPages",    result.getTotalPages());
        response.put("page",          result.getNumber());
        response.put("size",          result.getSize());
        return response;
    }

    private Specification<AuditLog> buildSpec(
            String entity, String action, String actor,
            String from, String to, String entityId) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (entity   != null && !entity.isBlank())
                predicates.add(cb.equal(root.get("entityName"), entity));
            if (entityId != null && !entityId.isBlank())
                predicates.add(cb.equal(root.get("entityId"), entityId));
            if (actor    != null && !actor.isBlank())
                predicates.add(cb.like(cb.lower(root.get("changedBy")), "%" + actor.toLowerCase() + "%"));
            if (action   != null && !action.isBlank()) {
                try {
                    predicates.add(cb.equal(root.get("action"), AuditAction.valueOf(action.toUpperCase())));
                } catch (IllegalArgumentException ignored) { }
            }
            if (from != null && !from.isBlank()) {
                try { predicates.add(cb.greaterThanOrEqualTo(root.get("changedAt"), Instant.parse(from))); }
                catch (Exception ignored) { }
            }
            if (to != null && !to.isBlank()) {
                try { predicates.add(cb.lessThanOrEqualTo(root.get("changedAt"), Instant.parse(to))); }
                catch (Exception ignored) { }
            }
            return predicates.isEmpty()
                    ? cb.conjunction()
                    : cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static Map<String, Object> toMap(AuditLog log) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id",            log.getId());
        m.put("entityName",    log.getEntityName());
        m.put("entityId",      log.getEntityId());
        m.put("action",        log.getAction() != null ? log.getAction().name() : null);
        m.put("changedBy",     log.getChangedBy());
        m.put("changedAt",     log.getChangedAt() != null ? log.getChangedAt().toString() : null);
        m.put("fieldDiffs",    log.getFieldDiffs());
        m.put("whyReason",     log.getWhyReason());
        m.put("masked",        log.isMasked());
        m.put("snapshotLabel", log.getSnapshotLabel());
        m.put("prevHash",      log.getPrevHash());
        return m;
    }
}
