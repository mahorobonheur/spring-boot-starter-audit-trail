package io.github.mahorobonheur.audittrail.service;

import io.github.mahorobonheur.audittrail.annotation.AuditTrail;
import io.github.mahorobonheur.audittrail.engine.FieldDiffEngine;
import io.github.mahorobonheur.audittrail.model.AuditAction;
import io.github.mahorobonheur.audittrail.model.AuditWriteRequest;
import io.github.mahorobonheur.audittrail.security.AuditSecurityResolver;
import io.github.mahorobonheur.audittrail.writer.AuditLogWriter;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuditSnapshotServiceTest {

    @Mock private AuditLogWriter writer;
    @Mock private AuditSecurityResolver securityResolver;

    private AuditSnapshotService snapshotService;

    @BeforeEach
    void setUp() {
        snapshotService = new AuditSnapshotService(writer, new FieldDiffEngine(), securityResolver);
    }

    @Test
    @DisplayName("capture writes a labelled CREATE snapshot")
    void capture_writesSnapshot() {
        when(securityResolver.getCurrentUser()).thenReturn("alice");
        SnapshotEntity entity = new SnapshotEntity();
        entity.id = 7L;
        entity.name = "bob";

        snapshotService.capture(entity, "pre-approval");

        ArgumentCaptor<AuditWriteRequest> captor = ArgumentCaptor.forClass(AuditWriteRequest.class);
        verify(writer).write(captor.capture());
        AuditWriteRequest request = captor.getValue();
        assertThat(request.getEntityName()).isEqualTo("SnapshotEntity");
        assertThat(request.getEntityId()).isEqualTo("7");
        assertThat(request.getAction()).isEqualTo(AuditAction.CREATE);
        assertThat(request.getSnapshotLabel()).isEqualTo("pre-approval");
        assertThat(request.getDiffs()).isNotEmpty();
    }

    @Test
    @DisplayName("captureWithReason includes the business reason")
    void captureWithReason_includesWhy() {
        when(securityResolver.getCurrentUser()).thenReturn("alice");
        SnapshotEntity entity = new SnapshotEntity();
        entity.id = 7L;
        entity.name = "bob";

        snapshotService.captureWithReason(entity, "checkpoint", "manual review");

        ArgumentCaptor<AuditWriteRequest> captor = ArgumentCaptor.forClass(AuditWriteRequest.class);
        verify(writer).write(captor.capture());
        assertThat(captor.getValue().getWhyReason()).isEqualTo("manual review");
    }

    @Test
    @DisplayName("capture rejects null entity")
    void capture_nullEntity_throws() {
        assertThatThrownBy(() -> snapshotService.capture(null, "label"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Entity
    @AuditTrail
    static class SnapshotEntity {
        @Id Long id;
        String name;
    }
}
