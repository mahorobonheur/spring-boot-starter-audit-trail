package io.github.mahorobonheur.audittrail.integration;

import io.github.mahorobonheur.audittrail.AuditTrailTestApp;
import io.github.mahorobonheur.audittrail.model.AuditAction;
import io.github.mahorobonheur.audittrail.model.AuditLog;
import io.github.mahorobonheur.audittrail.repository.AuditLogRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Integration test verifying the full audit trail flow:
 * entity save → JPA EntityListener → audit log written to H2 database.
 *
 * <p>Uses H2 in-memory database configured via {@code application-test.properties}.
 * The Spring context is provided by {@link AuditTrailTestApp}, whose root package
 * covers all entity, repository, and configuration sub-packages — no
 * {@code @EntityScan} or {@code @EnableJpaRepositories} required.
 *
 * @author Bonheur Mahoro
 */
@SpringBootTest(classes = AuditTrailTestApp.class)
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class AuditTrailIntegrationTest {

    // ── Injected beans ───────────────────────────────────────────────────────

    @Autowired TestUserRepository userRepo;
    @Autowired AuditLogRepository auditRepo;

    // ── Tests ────────────────────────────────────────────────────────────────

    @Test
    @WithMockUser(username = "bonheur")
    @DisplayName("CREATE event: audit log entry is written when entity is persisted")
    void createEntity_auditLogCreated() {
        userRepo.save(new TestUser("alice", "alice@x.com", "secret", "USER"));

        await().atMost(3, TimeUnit.SECONDS).untilAsserted(() -> {
            Page<AuditLog> logs = auditRepo.findByEntityName("TestUser", PageRequest.of(0, 20));
            assertThat(logs.getTotalElements()).isGreaterThanOrEqualTo(1);

            AuditLog entry = logs.getContent().stream()
                    .filter(l -> l.getAction() == AuditAction.CREATE)
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("No CREATE entry found"));

            assertThat(entry.getAction()).isEqualTo(AuditAction.CREATE);
            assertThat(entry.getChangedBy()).isEqualTo("bonheur");
            assertThat(entry.getFieldDiffs()).doesNotContain("password");
            assertThat(entry.getFieldDiffs()).contains("alice");
        });
    }

    @Test
    @WithMockUser(username = "bonheur")
    @DisplayName("UPDATE event: field-level diff is recorded when entity fields change")
    void updateEntity_fieldDiffRecorded() {
        TestUser user = userRepo.save(new TestUser("bob", "bob@x.com", "pass", "USER"));
        user.email = "newemail@x.com";
        user.role  = "ADMIN";
        userRepo.saveAndFlush(user);

        await().atMost(3, TimeUnit.SECONDS).untilAsserted(() -> {
            Page<AuditLog> logs = auditRepo.findByEntityName("TestUser", PageRequest.of(0, 20));
            boolean hasUpdate = logs.getContent().stream()
                    .anyMatch(l -> l.getAction() == AuditAction.UPDATE
                            && l.getFieldDiffs().contains("email")
                            && l.getFieldDiffs().contains("role"));
            assertThat(hasUpdate).isTrue();
        });
    }

    @Test
    @WithMockUser(username = "bonheur")
    @DisplayName("DELETE event: audit log entry is written when entity is removed")
    void deleteEntity_auditLogCreated() {
        TestUser user = userRepo.save(new TestUser("carol", "carol@x.com", "pass", "USER"));
        userRepo.delete(user);
        userRepo.flush();

        await().atMost(3, TimeUnit.SECONDS).untilAsserted(() -> {
            Page<AuditLog> logs = auditRepo.findByEntityName("TestUser", PageRequest.of(0, 20));
            boolean hasDelete = logs.getContent().stream()
                    .anyMatch(l -> l.getAction() == AuditAction.DELETE);
            assertThat(hasDelete).isTrue();
        });
    }
}
