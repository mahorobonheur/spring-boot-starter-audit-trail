package io.github.mahorobonheur.audittrail.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.mahorobonheur.audittrail.model.AuditAction;
import io.github.mahorobonheur.audittrail.model.AuditLog;
import io.github.mahorobonheur.audittrail.repository.AuditLogRepository;
import io.github.mahorobonheur.audittrail.support.TestAuditLogs;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuditReconstructionServiceTest {

    @Mock
    private AuditLogRepository repository;

    private AuditReconstructionService reconstructionService;

    @BeforeEach
    void setUp() {
        reconstructionService = new AuditReconstructionService(repository, new ObjectMapper());
    }

    @Test
    @DisplayName("reconstruct replays diffs up to the given instant")
    void reconstruct_replaysDiffsUpToInstant() {
        Instant t1 = Instant.parse("2026-01-01T10:00:00Z");
        Instant t2 = Instant.parse("2026-01-01T11:00:00Z");
        Instant t3 = Instant.parse("2026-01-01T12:00:00Z");

        AuditLog create = TestAuditLogs.entry(
                "e1", "User", "42", AuditAction.CREATE, t1,
                "[{\"field\":\"email\",\"oldValue\":null,\"newValue\":\"a@x.com\"}]", null);
        AuditLog update = TestAuditLogs.entry(
                "e2", "User", "42", AuditAction.UPDATE, t2,
                "[{\"field\":\"role\",\"oldValue\":\"USER\",\"newValue\":\"ADMIN\"}]", null);
        AuditLog future = TestAuditLogs.entry(
                "e3", "User", "42", AuditAction.UPDATE, t3,
                "[{\"field\":\"role\",\"oldValue\":\"ADMIN\",\"newValue\":\"SUPER\"}]", null);

        when(repository.findByEntityNameAndEntityIdOrderByChangedAtAscIdAsc("User", "42"))
                .thenReturn(List.of(create, update, future));

        Map<String, Object> state = reconstructionService.reconstruct("User", "42", t2);

        assertThat(state).containsEntry("email", "a@x.com");
        assertThat(state).containsEntry("role", "ADMIN");
    }

    @Test
    @DisplayName("reconstruct returns empty map when no entries exist")
    void reconstruct_noEntries_returnsEmpty() {
        when(repository.findByEntityNameAndEntityIdOrderByChangedAtAscIdAsc("User", "99"))
                .thenReturn(List.of());

        assertThat(reconstructionService.reconstruct(
                "User", "99", Instant.parse("2026-06-01T00:00:00Z"))).isEmpty();
    }

    @Test
    @DisplayName("reconstruct skips malformed fieldDiffs JSON")
    void reconstruct_malformedJson_skipsEntry() {
        Instant t1 = Instant.parse("2026-01-01T10:00:00Z");
        AuditLog bad = TestAuditLogs.entry("e1", "User", "42", AuditAction.UPDATE, t1, "not-json", null);

        when(repository.findByEntityNameAndEntityIdOrderByChangedAtAscIdAsc("User", "42"))
                .thenReturn(List.of(bad));

        assertThat(reconstructionService.reconstruct("User", "42", t1)).isEmpty();
    }
}
