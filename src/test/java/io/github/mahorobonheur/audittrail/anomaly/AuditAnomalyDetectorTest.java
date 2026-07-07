package io.github.mahorobonheur.audittrail.anomaly;

import io.github.mahorobonheur.audittrail.config.AuditTrailProperties;
import io.github.mahorobonheur.audittrail.model.AuditAction;
import io.github.mahorobonheur.audittrail.model.AuditLog;
import io.github.mahorobonheur.audittrail.support.TestAuditLogs;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class AuditAnomalyDetectorTest {

    @Mock
    private ApplicationEventPublisher publisher;

    private AuditTrailProperties properties;
    private AuditAnomalyDetector detector;

    @BeforeEach
    void setUp() {
        properties = new AuditTrailProperties();
        properties.getAnomaly().setEnabled(true);
        properties.getAnomaly().setBulkDeleteThreshold(2);
        properties.getAnomaly().setRapidChangeThreshold(2);
        properties.getAnomaly().setWindowSeconds(60);
        detector = new AuditAnomalyDetector(publisher, properties);
    }

    @Test
    @DisplayName("BULK_DELETE fires when delete threshold is exceeded")
    void evaluate_bulkDelete_firesEvent() {
        properties.getAnomaly().setRapidChangeThreshold(100);
        detector = new AuditAnomalyDetector(publisher, properties);

        Instant now = Instant.now();
        List<AuditLog> deletes = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            deletes.add(TestAuditLogs.entry(
                    "d" + i, "User", String.valueOf(i), AuditAction.DELETE, now,
                    "[]", null));
        }

        deletes.forEach(detector::evaluate);

        ArgumentCaptor<AuditAnomalyEvent> captor = ArgumentCaptor.forClass(AuditAnomalyEvent.class);
        verify(publisher).publishEvent(captor.capture());
        assertThat(captor.getValue().getRule()).isEqualTo("BULK_DELETE");
    }

    @Test
    @DisplayName("RAPID_CHANGES fires when actor threshold is exceeded")
    void evaluate_rapidChanges_firesEvent() {
        properties.getAnomaly().setBulkDeleteThreshold(100);
        detector = new AuditAnomalyDetector(publisher, properties);

        Instant now = Instant.now();
        for (int i = 0; i < 3; i++) {
            detector.evaluate(TestAuditLogs.entry(
                    "u" + i, "User", String.valueOf(i), AuditAction.UPDATE, now,
                    "[]", null));
        }

        ArgumentCaptor<AuditAnomalyEvent> captor = ArgumentCaptor.forClass(AuditAnomalyEvent.class);
        verify(publisher).publishEvent(captor.capture());
        assertThat(captor.getValue().getRule()).isEqualTo("RAPID_CHANGES");
    }

    @Test
    @DisplayName("CREATE events do not trigger BULK_DELETE")
    void evaluate_create_doesNotFireBulkDelete() {
        detector.evaluate(TestAuditLogs.entry(
                "c1", "User", "1", AuditAction.CREATE, Instant.now(), "[]", null));

        verifyNoInteractions(publisher);
    }
}
