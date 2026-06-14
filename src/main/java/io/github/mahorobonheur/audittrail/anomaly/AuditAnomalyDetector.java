package io.github.mahorobonheur.audittrail.anomaly;

import io.github.mahorobonheur.audittrail.config.AuditTrailProperties;
import io.github.mahorobonheur.audittrail.model.AuditAction;
import io.github.mahorobonheur.audittrail.model.AuditLog;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory sliding-window anomaly detector for the audit trail.
 *
 * <p>Enabled only when {@code audit-trail.anomaly.enabled=true}. After each
 * audit entry is written to the database, {@link #evaluate(AuditLog)} is called
 * by {@link io.github.mahorobonheur.audittrail.writer.DatabaseAuditLogWriter}.
 * Two rules are checked:
 *
 * <ol>
 *   <li><strong>BULK_DELETE</strong> — more than
 *       {@code audit-trail.anomaly.bulk-delete-threshold} (default 10) DELETE events
 *       for the same entity type within
 *       {@code audit-trail.anomaly.window-seconds} (default 60) seconds.</li>
 *   <li><strong>RAPID_CHANGES</strong> — more than
 *       {@code audit-trail.anomaly.rapid-change-threshold} (default 50) changes by
 *       the same actor within the window.</li>
 * </ol>
 *
 * <p>When a rule fires, an {@link AuditAnomalyEvent} is published through Spring's
 * {@link ApplicationEventPublisher}, allowing application code to react via
 * {@link org.springframework.context.event.EventListener @EventListener}.
 *
 * <p><strong>Note:</strong> counters are held in heap memory and are lost on restart.
 * This is intentional — false positives from stale counters across restarts are worse
 * than missing a burst that spans a restart boundary.
 *
 * @author Bonheur Mahoro
 */
@Component
@ConditionalOnProperty(prefix = "audit-trail.anomaly", name = "enabled", havingValue = "true")
public class AuditAnomalyDetector {

    private final ApplicationEventPublisher publisher;
    private final AuditTrailProperties      properties;

    /** Sliding window event times keyed by rule-specific string keys. */
    private final ConcurrentHashMap<String, Deque<Instant>> windows = new ConcurrentHashMap<>();

    public AuditAnomalyDetector(ApplicationEventPublisher publisher,
                                 AuditTrailProperties properties) {
        this.publisher  = publisher;
        this.properties = properties;
    }

    /**
     * Evaluates the given audit entry against all configured anomaly rules.
     * Fires an {@link AuditAnomalyEvent} for each rule that is breached.
     *
     * @param entry the freshly persisted audit log entry
     */
    public void evaluate(AuditLog entry) {
        checkBulkDelete(entry);
        checkRapidChanges(entry);
    }

    // ── Rules ─────────────────────────────────────────────────────────────────

    private void checkBulkDelete(AuditLog entry) {
        if (entry.getAction() != AuditAction.DELETE) return;

        AuditTrailProperties.Anomaly cfg = properties.getAnomaly();
        String key   = "DELETE:" + entry.getEntityName();
        int    count = record(key, cfg.getWindowSeconds());

        if (count > cfg.getBulkDeleteThreshold()) {
            publisher.publishEvent(new AuditAnomalyEvent(
                    this,
                    entry.getEntityName(),
                    null,
                    entry.getChangedBy(),
                    "BULK_DELETE",
                    String.format("%d DELETE events for entity '%s' in the last %d seconds (threshold: %d)",
                            count, entry.getEntityName(), cfg.getWindowSeconds(), cfg.getBulkDeleteThreshold())
            ));
        }
    }

    private void checkRapidChanges(AuditLog entry) {
        AuditTrailProperties.Anomaly cfg = properties.getAnomaly();
        String key   = "actor:" + entry.getChangedBy();
        int    count = record(key, cfg.getWindowSeconds());

        if (count > cfg.getRapidChangeThreshold()) {
            publisher.publishEvent(new AuditAnomalyEvent(
                    this,
                    entry.getEntityName(),
                    entry.getEntityId(),
                    entry.getChangedBy(),
                    "RAPID_CHANGES",
                    String.format("Actor '%s' made %d changes in the last %d seconds (threshold: %d)",
                            entry.getChangedBy(), count, cfg.getWindowSeconds(), cfg.getRapidChangeThreshold())
            ));
        }
    }

    // ── Sliding window helper ─────────────────────────────────────────────────

    /**
     * Records the current timestamp in the sliding window for the given key, evicts
     * entries older than {@code windowSeconds}, and returns the current window count.
     */
    private int record(String key, int windowSeconds) {
        Instant now    = Instant.now();
        Instant cutoff = now.minusSeconds(windowSeconds);

        Deque<Instant> deque = windows.computeIfAbsent(key, k -> new ArrayDeque<>());

        synchronized (deque) {
            deque.addLast(now);
            // Evict expired entries from the front
            while (!deque.isEmpty() && deque.peekFirst().isBefore(cutoff)) {
                deque.pollFirst();
            }
            return deque.size();
        }
    }
}
