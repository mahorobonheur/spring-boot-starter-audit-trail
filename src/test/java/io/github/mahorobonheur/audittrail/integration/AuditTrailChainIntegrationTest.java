package io.github.mahorobonheur.audittrail.integration;

import io.github.mahorobonheur.audittrail.AuditTrailTestApp;
import io.github.mahorobonheur.audittrail.model.AuditAction;
import io.github.mahorobonheur.audittrail.model.AuditLog;
import io.github.mahorobonheur.audittrail.repository.AuditLogRepository;
import io.github.mahorobonheur.audittrail.service.AuditChainService;
import io.github.mahorobonheur.audittrail.service.AuditReconstructionService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies chain verification and reconstruction against persisted audit rows,
 * without exercising the Hibernate listener flush path (which conflicts with
 * synchronous chain lookups during {@code saveAndFlush}).
 */
@SpringBootTest(classes = AuditTrailTestApp.class)
@ActiveProfiles({"test", "test-chain"})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class AuditTrailChainIntegrationTest {

    @Autowired AuditLogRepository auditRepo;
    @Autowired AuditChainService chainService;
    @Autowired AuditReconstructionService reconstructionService;

    @Test
    @Transactional
    @DisplayName("persisted chain hashes verify as intact")
    void verifyChain_persistedEntries_isValid() {
        Instant t0 = Instant.parse("2026-01-01T10:00:00Z");
        Instant t1 = Instant.parse("2026-01-01T11:00:00Z");

        AuditLog first = auditRepo.save(new AuditLog(
                "User", "42", AuditAction.CREATE, "alice", t0,
                "[{\"field\":\"email\",\"oldValue\":null,\"newValue\":\"a@x.com\"}]",
                null, false, null, null));
        String link = chainService.computeChainHash(null, first);
        auditRepo.save(new AuditLog(
                "User", "42", AuditAction.UPDATE, "alice", t1,
                "[{\"field\":\"email\",\"oldValue\":\"a@x.com\",\"newValue\":\"b@x.com\"}]",
                null, false, null, link));

        List<AuditLog> ordered = auditRepo
                .findByEntityNameAndEntityIdOrderByChangedAtAscIdAsc("User", "42");

        assertThat(chainService.verifyChain(ordered).valid()).isTrue();
    }

    @Test
    @Transactional
    @DisplayName("reconstruction replays persisted field diffs")
    void reconstruct_persistedEntries_returnsState() {
        Instant t0 = Instant.parse("2026-01-01T10:00:00Z");
        Instant t1 = Instant.parse("2026-01-01T11:00:00Z");

        auditRepo.save(new AuditLog(
                "User", "7", AuditAction.CREATE, "alice", t0,
                "[{\"field\":\"email\",\"oldValue\":null,\"newValue\":\"old@x.com\"}]",
                null, false, null, null));
        auditRepo.save(new AuditLog(
                "User", "7", AuditAction.UPDATE, "alice", t1,
                "[{\"field\":\"email\",\"oldValue\":\"old@x.com\",\"newValue\":\"new@x.com\"}]",
                null, false, null, null));

        Map<String, Object> state = reconstructionService.reconstruct(
                "User", "7", t1);

        assertThat(state).containsEntry("email", "new@x.com");
    }
}
