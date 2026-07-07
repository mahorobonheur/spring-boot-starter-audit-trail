package io.github.mahorobonheur.audittrail.writer;

import io.github.mahorobonheur.audittrail.model.AuditAction;
import io.github.mahorobonheur.audittrail.model.AuditWriteRequest;
import io.github.mahorobonheur.audittrail.model.FieldDiff;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AsyncAuditLogWriterTest {

    @Mock
    private AuditLogWriter delegate;

    @Test
    @DisplayName("write delegates to the wrapped writer")
    void write_delegates() {
        AsyncAuditLogWriter asyncWriter = new AsyncAuditLogWriter(delegate);
        AuditWriteRequest request = AuditWriteRequest.builder()
                .entityName("User")
                .entityId("42")
                .action(AuditAction.UPDATE)
                .changedBy("alice")
                .diffs(List.of(new FieldDiff("email", "a", "b")))
                .build();

        asyncWriter.write(request);

        ArgumentCaptor<AuditWriteRequest> captor = ArgumentCaptor.forClass(AuditWriteRequest.class);
        verify(delegate).write(captor.capture());
        assertThat(captor.getValue().getEntityName()).isEqualTo("User");
    }
}
