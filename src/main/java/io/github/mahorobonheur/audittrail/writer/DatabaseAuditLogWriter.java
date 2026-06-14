package io.github.mahorobonheur.audittrail.writer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.mahorobonheur.audittrail.anomaly.AuditAnomalyDetector;
import io.github.mahorobonheur.audittrail.model.AuditLog;
import io.github.mahorobonheur.audittrail.model.AuditWriteRequest;
import io.github.mahorobonheur.audittrail.repository.AuditLogRepository;
import io.github.mahorobonheur.audittrail.service.AuditChainService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.Optional;

/**
 * Default {@link AuditLogWriter} implementation that persists audit events
 * to the relational database via {@link AuditLogRepository}.
 *
 * <p>Writes are synchronous. When {@code audit-trail.async=true} (the default),
 * {@link AsyncAuditLogWriter} delegates to this class on a background thread.
 *
 * <p>When a {@link PlatformTransactionManager} is supplied, each audit entry is
 * persisted in its own {@code REQUIRES_NEW} transaction. This is essential for
 * synchronous writes: the writer is invoked from Hibernate event listeners that
 * fire <em>during</em> a flush, and saving through the same session mid-flush
 * can corrupt Hibernate's action queue.
 *
 * <p>When {@link AuditChainService} is present (i.e., {@code audit-trail.chain.enabled=true}),
 * the last entry for the same entity+id is looked up and a chain hash is computed and
 * stored in the new entry's {@code prevHash} column.
 *
 * <p>When {@link AuditAnomalyDetector} is present, it is called after each successful
 * save to evaluate anomaly rules.
 *
 * <p>Field diffs are serialised to JSON using Jackson before storage.
 *
 * @author Bonheur Mahoro
 */
public class DatabaseAuditLogWriter implements AuditLogWriter {

    private static final Logger log = LoggerFactory.getLogger(DatabaseAuditLogWriter.class);

    private final AuditLogRepository     repository;
    private final ObjectMapper           objectMapper;
    private final TransactionTemplate    transactionTemplate;
    private final AuditChainService      chainService;      // nullable
    private final AuditAnomalyDetector   anomalyDetector;   // nullable

    // ── Constructors ──────────────────────────────────────────────────────────

    /**
     * Creates a writer that saves directly through the repository without
     * transaction isolation. Intended for unit tests and custom setups.
     */
    public DatabaseAuditLogWriter(AuditLogRepository repository, ObjectMapper objectMapper) {
        this(repository, objectMapper, null, null, null);
    }

    /**
     * Creates a writer that persists each audit entry in a new, independent
     * transaction ({@code REQUIRES_NEW}).
     */
    public DatabaseAuditLogWriter(AuditLogRepository repository,
                                  ObjectMapper objectMapper,
                                  PlatformTransactionManager transactionManager) {
        this(repository, objectMapper, transactionManager, null, null);
    }

    /**
     * Full constructor used by the auto-configuration when optional services are available.
     *
     * @param repository         the audit log repository
     * @param objectMapper       Jackson mapper for diff serialisation
     * @param transactionManager transaction manager for REQUIRES_NEW isolation; may be {@code null}
     * @param chainService       optional chain-hash service; {@code null} when chain is disabled
     * @param anomalyDetector    optional anomaly detector; {@code null} when anomaly detection is disabled
     */
    public DatabaseAuditLogWriter(AuditLogRepository repository,
                                  ObjectMapper objectMapper,
                                  PlatformTransactionManager transactionManager,
                                  AuditChainService chainService,
                                  AuditAnomalyDetector anomalyDetector) {
        this.repository      = repository;
        this.objectMapper    = objectMapper;
        this.chainService    = chainService;
        this.anomalyDetector = anomalyDetector;
        if (transactionManager != null) {
            TransactionTemplate template = new TransactionTemplate(transactionManager);
            template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            this.transactionTemplate = template;
        } else {
            this.transactionTemplate = null;
        }
    }

    // ── AuditLogWriter ────────────────────────────────────────────────────────

    /**
     * {@inheritDoc}
     *
     * <p>Serialises {@code request.getDiffs()} to a JSON string, optionally computes
     * a chain hash, then persists the resulting {@link AuditLog} entity and optionally
     * runs anomaly detection.
     */
    @Override
    public void write(AuditWriteRequest request) {
        try {
            String diffsJson = objectMapper.writeValueAsString(request.getDiffs());

            // Resolve prevHash from chain service when enabled
            String prevHash = null;
            if (chainService != null) {
                Optional<AuditLog> last = repository
                        .findTopByEntityNameAndEntityIdOrderByChangedAtDesc(
                                request.getEntityName(), request.getEntityId());
                if (last.isPresent()) {
                    prevHash = chainService.computeChainHash(last.get().getPrevHash(), last.get());
                }
            }

            final String resolvedPrevHash = prevHash;

            AuditLog entry = new AuditLog(
                    request.getEntityName(),
                    request.getEntityId(),
                    request.getAction(),
                    request.getChangedBy(),
                    Instant.now(),
                    diffsJson,
                    request.getWhyReason(),
                    request.isHasMaskedFields(),
                    request.getSnapshotLabel(),
                    resolvedPrevHash
            );

            AuditLog saved;
            if (transactionTemplate != null) {
                saved = transactionTemplate.execute(status -> repository.save(entry));
            } else {
                saved = repository.save(entry);
            }

            log.debug("Audit log saved: entity={}, id={}, action={}, by={}",
                    request.getEntityName(), request.getEntityId(),
                    request.getAction(), request.getChangedBy());

            // Post-save anomaly detection
            if (anomalyDetector != null && saved != null) {
                try {
                    anomalyDetector.evaluate(saved);
                } catch (Exception e) {
                    log.warn("Anomaly detection failed for entry {}: {}", saved.getId(), e.getMessage(), e);
                }
            }

        } catch (JsonProcessingException e) {
            log.error("Failed to serialise field diffs for entity={} id={}: {}",
                    request.getEntityName(), request.getEntityId(), e.getMessage(), e);
        }
    }
}
