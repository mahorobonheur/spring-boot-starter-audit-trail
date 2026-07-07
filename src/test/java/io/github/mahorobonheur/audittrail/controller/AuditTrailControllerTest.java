package io.github.mahorobonheur.audittrail.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.mahorobonheur.audittrail.config.AuditTrailProperties;
import io.github.mahorobonheur.audittrail.model.AuditAction;
import io.github.mahorobonheur.audittrail.model.AuditLog;
import io.github.mahorobonheur.audittrail.repository.AuditLogRepository;
import io.github.mahorobonheur.audittrail.service.AuditChainService;
import io.github.mahorobonheur.audittrail.service.AuditReconstructionService;
import io.github.mahorobonheur.audittrail.support.TestAuditLogs;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuditTrailControllerTest {

    @Mock private AuditLogRepository repository;
    @Mock private AuditReconstructionService reconstructionService;

    private AuditChainService chainService;
    private AuditTrailController controller;

    @BeforeEach
    void setUp() {
        chainService = new AuditChainService();
        AuditTrailProperties properties = new AuditTrailProperties();
        controller = new AuditTrailController(
                repository, properties, Optional.of(chainService),
                reconstructionService, new ObjectMapper().findAndRegisterModules());
    }

    @Test
    @DisplayName("GET /{entityName}/{entityId} returns paginated history")
    void getHistory_returnsPage() {
        AuditLog entry = TestAuditLogs.entry(
                "e1", "User", "42", AuditAction.CREATE,
                Instant.parse("2026-01-01T00:00:00Z"),
                "[{\"field\":\"email\",\"oldValue\":null,\"newValue\":\"a@x.com\"}]", null);
        when(repository.findByEntityNameAndEntityId(eq("User"), eq("42"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(entry)));

        ResponseEntity<Page<Map<String, Object>>> response =
                controller.getHistory("User", "42", 0, 20);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getContent().get(0).get("entityName")).isEqualTo("User");
        assertThat(response.getBody().getContent().get(0).get("fieldDiffs")).isInstanceOf(List.class);
    }

    @Test
    @DisplayName("GET /dashboard entity path returns 404 when dashboard disabled")
    void getHistoryByEntity_dashboardReserved_returns404() {
        ResponseEntity<Page<Map<String, Object>>> response =
                controller.getHistoryByEntity("dashboard", 0, 20);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("GET diff returns 404 when an entry id is missing")
    void getDiff_missingEntry_returns404() {
        when(repository.findById("from")).thenReturn(Optional.empty());

        ResponseEntity<Map<String, Object>> response =
                controller.getDiff("User", "42", "from", "to");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("GET diff returns both entries when found")
    void getDiff_found_returnsBoth() {
        AuditLog from = TestAuditLogs.entry(
                "e1", "User", "42", AuditAction.CREATE,
                Instant.parse("2026-01-01T00:00:00Z"), "[]", null);
        AuditLog to = TestAuditLogs.entry(
                "e2", "User", "42", AuditAction.UPDATE,
                Instant.parse("2026-01-01T01:00:00Z"), "[]", null);
        when(repository.findById("e1")).thenReturn(Optional.of(from));
        when(repository.findById("e2")).thenReturn(Optional.of(to));

        ResponseEntity<Map<String, Object>> response =
                controller.getDiff("User", "42", "e1", "e2");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsKeys("from", "to");
    }

    @Test
    @DisplayName("GET verify returns chain verification result")
    void verifyChain_enabled_returnsResult() {
        AuditLog entry = TestAuditLogs.entry(
                "e1", "User", "42", AuditAction.CREATE,
                Instant.parse("2026-01-01T00:00:00Z"), "[]", null);
        when(repository.findByEntityNameAndEntityIdOrderByChangedAtAscIdAsc("User", "42"))
                .thenReturn(List.of(entry));

        ResponseEntity<Map<String, Object>> response = controller.verifyChain("User", "42");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsEntry("valid", true);
        assertThat(response.getBody()).containsEntry("entryCount", 1);
    }

    @Test
    @DisplayName("GET verify returns 501 when chain service is absent")
    void verifyChain_disabled_returns501() {
        AuditTrailController disabled = new AuditTrailController(
                repository, new AuditTrailProperties(), Optional.empty(),
                reconstructionService, new ObjectMapper());

        ResponseEntity<Map<String, Object>> response = disabled.verifyChain("User", "42");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_IMPLEMENTED);
        assertThat(response.getBody()).containsKey("error");
    }

    @Test
    @DisplayName("GET reconstruct returns 400 for invalid instant")
    void reconstruct_invalidAt_returns400() {
        ResponseEntity<Map<String, Object>> response =
                controller.reconstruct("User", "42", "not-an-instant");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).containsKey("error");
    }

    @Test
    @DisplayName("GET reconstruct returns state map")
    void reconstruct_validAt_returnsState() {
        when(reconstructionService.reconstruct(eq("User"), eq("42"), any(Instant.class)))
                .thenReturn(Map.of("email", "a@x.com"));

        ResponseEntity<Map<String, Object>> response =
                controller.reconstruct("User", "42", "2026-06-01T10:00:00Z");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsKey("state");
        @SuppressWarnings("unchecked")
        Map<String, Object> state = (Map<String, Object>) response.getBody().get("state");
        assertThat(state).containsEntry("email", "a@x.com");
    }
}
