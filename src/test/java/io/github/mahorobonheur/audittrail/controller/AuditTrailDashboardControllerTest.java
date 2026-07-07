package io.github.mahorobonheur.audittrail.controller;

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
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AuditTrailDashboardControllerTest {

    @Mock
    private AuditLogRepository repository;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        AuditTrailDashboardController controller =
                new AuditTrailDashboardController(repository, "/audit-trail");
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    @DisplayName("GET /dashboard serves HTML with base path substituted")
    void dashboardHtml_servesHtml() throws Exception {
        mockMvc.perform(get("/audit-trail/dashboard"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/html"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("/audit-trail")));
    }

    @Test
    @DisplayName("GET /dashboard/api/stats returns aggregate counters")
    void stats_returnsOverview() throws Exception {
        when(repository.count()).thenReturn(5L);
        when(repository.findDistinctActors()).thenReturn(List.of("alice", "bob"));
        when(repository.findDistinctEntityNames()).thenReturn(List.of("User"));
        when(repository.findByChangedAtAfter(any(Instant.class))).thenReturn(List.of());

        mockMvc.perform(get("/audit-trail/dashboard/api/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalLogs").value(5))
                .andExpect(jsonPath("$.uniqueActors").value(2))
                .andExpect(jsonPath("$.uniqueEntities").value(1))
                .andExpect(jsonPath("$.activityByDay").isMap());
    }

    @Test
    @DisplayName("GET /dashboard/api/entities returns entity summaries")
    void entities_returnsSummaries() throws Exception {
        AuditLog latest = TestAuditLogs.entry(
                "e1", "User", "1", AuditAction.UPDATE,
                Instant.parse("2026-06-01T10:00:00Z"), "[]", null);
        when(repository.countGroupedByEntityName()).thenReturn(List.<Object[]>of(new Object[]{"User", 3L}));
        when(repository.findTopByEntityNameOrderByChangedAtDesc("User")).thenReturn(Optional.of(latest));

        mockMvc.perform(get("/audit-trail/dashboard/api/entities"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("User"))
                .andExpect(jsonPath("$[0].count").value(3));
    }

    @Test
    @DisplayName("GET /dashboard/api/recent returns latest entries")
    void recent_returnsEntries() throws Exception {
        AuditLog entry = TestAuditLogs.entry(
                "e1", "User", "42", AuditAction.CREATE,
                Instant.parse("2026-06-01T10:00:00Z"), "[]", null);
        when(repository.findTop50ByOrderByChangedAtDesc()).thenReturn(List.of(entry));

        mockMvc.perform(get("/audit-trail/dashboard/api/recent"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("e1"));
    }

    @Test
    @DisplayName("GET /dashboard/api/logs supports pagination metadata")
    void logs_returnsPage() throws Exception {
        AuditLog entry = TestAuditLogs.entry(
                "e1", "User", "42", AuditAction.UPDATE,
                Instant.parse("2026-06-01T10:00:00Z"), "[]", null);
        when(repository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(entry)));

        mockMvc.perform(get("/audit-trail/dashboard/api/logs")
                        .param("entity", "User")
                        .param("action", "UPDATE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].entityName").value("User"));
    }
}
