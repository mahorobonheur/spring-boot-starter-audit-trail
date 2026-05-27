package io.github.mahorobonheur.audittrail.writer;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.mahorobonheur.audittrail.model.AuditAction;
import io.github.mahorobonheur.audittrail.model.AuditLog;
import io.github.mahorobonheur.audittrail.model.FieldDiff;
import io.github.mahorobonheur.audittrail.repository.AuditLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

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
}
