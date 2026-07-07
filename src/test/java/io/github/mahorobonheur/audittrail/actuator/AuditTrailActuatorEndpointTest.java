package io.github.mahorobonheur.audittrail.actuator;

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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuditTrailActuatorEndpointTest {

    @Mock
    private AuditLogRepository repository;

    private AuditTrailActuatorEndpoint endpoint;

    @BeforeEach
    void setUp() {
        endpoint = new AuditTrailActuatorEndpoint(repository);
    }

    @Test
    @DisplayName("summary returns action counts for the last 24 hours")
    void summary_returnsCounts() {
        Instant now = Instant.now();
        List<AuditLog> recent = List.of(
                TestAuditLogs.entry("c1", "User", "1", AuditAction.CREATE, now, "[]", null),
                TestAuditLogs.entry("u1", "User", "1", AuditAction.UPDATE, now, "[]", null),
                TestAuditLogs.entry("d1", "User", "1", AuditAction.DELETE, now, "[]", null));
        when(repository.findByChangedAtAfter(any(Instant.class))).thenReturn(recent);
        when(repository.count()).thenReturn(100L);

        @SuppressWarnings("unchecked")
        Map<String, Object> last24h = (Map<String, Object>) endpoint.info("summary").get("last24h");

        assertThat(last24h.get("CREATE")).isEqualTo(1L);
        assertThat(last24h.get("UPDATE")).isEqualTo(1L);
        assertThat(last24h.get("DELETE")).isEqualTo(1L);
        assertThat(endpoint.info("summary").get("allTime")).isEqualTo(100L);
    }

    @Test
    @DisplayName("hotspots returns top entity names by volume")
    void hotspots_returnsTopEntities() {
        Instant now = Instant.now();
        List<AuditLog> recent = List.of(
                TestAuditLogs.entry("u1", "User", "1", AuditAction.UPDATE, now, "[]", null),
                TestAuditLogs.entry("u2", "User", "2", AuditAction.UPDATE, now, "[]", null),
                TestAuditLogs.entry("o1", "Order", "1", AuditAction.CREATE, now, "[]", null));
        when(repository.findByChangedAtAfter(any(Instant.class))).thenReturn(recent);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> hotspots =
                (List<Map<String, Object>>) endpoint.info("hotspots").get("hotspots");

        assertThat(hotspots).hasSize(2);
        assertThat(hotspots.get(0).get("entityName")).isEqualTo("User");
        assertThat(hotspots.get(0).get("count")).isEqualTo(2L);
    }

    @Test
    @DisplayName("unknown operation falls back to summary")
    void unknownOperation_defaultsToSummary() {
        when(repository.findByChangedAtAfter(any(Instant.class))).thenReturn(List.of());
        when(repository.count()).thenReturn(0L);

        assertThat(endpoint.info("unknown")).containsKey("last24h");
    }
}
