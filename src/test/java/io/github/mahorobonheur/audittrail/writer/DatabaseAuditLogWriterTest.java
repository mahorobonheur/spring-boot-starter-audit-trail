package io.github.mahorobonheur.audittrail.writer;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.mahorobonheur.audittrail.anomaly.AuditAnomalyDetector;
import io.github.mahorobonheur.audittrail.model.AuditAction;
import io.github.mahorobonheur.audittrail.model.AuditLog;
import io.github.mahorobonheur.audittrail.model.AuditWriteRequest;
import io.github.mahorobonheur.audittrail.model.FieldDiff;
import io.github.mahorobonheur.audittrail.repository.AuditLogRepository;
import io.github.mahorobonheur.audittrail.service.AuditChainService;
import io.github.mahorobonheur.audittrail.support.TestAuditLogs;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DatabaseAuditLogWriterTest {

    @Mock
    private AuditLogRepository repository;

    @Mock
    private ObjectMapper objectMapper;

    private DatabaseAuditLogWriter writer;

    @BeforeEach
    void setUp() {
        writer = new DatabaseAuditLogWriter(repository, objectMapper);
    }

    @Test
    @DisplayName("Serialises diffs and persists an AuditLog entry")
    void write_persistsAuditLog() throws Exception {
        when(objectMapper.writeValueAsString(any())).thenReturn("[{\"field\":\"email\"}]");

        writer.write("User", "42", AuditAction.UPDATE, "alice",
                List.of(new FieldDiff("email", "a@x.com", "b@x.com")));

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(repository).save(captor.capture());

        AuditLog saved = captor.getValue();
        assertThat(saved.getEntityName()).isEqualTo("User");
        assertThat(saved.getEntityId()).isEqualTo("42");
        assertThat(saved.getAction()).isEqualTo(AuditAction.UPDATE);
        assertThat(saved.getChangedBy()).isEqualTo("alice");
        assertThat(saved.getFieldDiffs()).isEqualTo("[{\"field\":\"email\"}]");
        assertThat(saved.getChangedAt()).isNotNull();
    }

    @Test
    @DisplayName("write with chain service stores prevHash from the previous entry")
    void write_withChainService_setsPrevHash() throws Exception {
        AuditChainService chainService = new AuditChainService();
        AuditLog previous = TestAuditLogs.entry(
                "prev", "User", "42", AuditAction.CREATE,
                Instant.parse("2026-01-01T00:00:00Z"), "[]", null);
        String expectedPrevHash = chainService.computeChainHash(null, previous);

        when(repository.findTopByEntityNameAndEntityIdOrderByChangedAtDescIdDesc("User", "42"))
                .thenReturn(Optional.of(previous));
        when(objectMapper.writeValueAsString(any())).thenReturn("[]");
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        DatabaseAuditLogWriter chainedWriter =
                new DatabaseAuditLogWriter(repository, objectMapper, null, chainService, null);
        chainedWriter.write(AuditWriteRequest.from(
                "User", "42", AuditAction.UPDATE, "alice",
                List.of(new FieldDiff("email", "a", "b"))));

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getPrevHash()).isEqualTo(expectedPrevHash);
    }

    @Test
    @DisplayName("write invokes anomaly detector after save")
    void write_withAnomalyDetector_evaluatesEntry() throws Exception {
        AuditAnomalyDetector anomalyDetector = org.mockito.Mockito.mock(AuditAnomalyDetector.class);
        when(objectMapper.writeValueAsString(any())).thenReturn("[]");
        AuditLog saved = TestAuditLogs.entry(
                "e1", "User", "42", AuditAction.DELETE,
                Instant.now(), "[]", null);
        when(repository.save(any())).thenReturn(saved);

        DatabaseAuditLogWriter writerWithAnomaly =
                new DatabaseAuditLogWriter(repository, objectMapper, null, null, anomalyDetector);
        writerWithAnomaly.write(AuditWriteRequest.from(
                "User", "42", AuditAction.DELETE, "alice", List.of()));

        verify(anomalyDetector).evaluate(saved);
    }
}
