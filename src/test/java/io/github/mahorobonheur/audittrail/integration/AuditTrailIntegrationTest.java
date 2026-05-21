package io.github.mahorobonheur.audittrail.integration;

import io.github.mahorobonheur.audittrail.model.AuditAction;
import io.github.mahorobonheur.audittrail.model.AuditLog;
import io.github.mahorobonheur.audittrail.repository.AuditLogRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
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
 *
 * @author Bonheur Mahoro
 */
@SpringBootTest(classes = AuditTrailIntegrationTest.TestApp.class)
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class AuditTrailIntegrationTest {

    /**
     * Minimal Spring Boot application context for the integration tests.
     * Scans the {@code integration} package for both entities and repositories
     * so Spring Data can proxy {@link TestUserRepository} correctly.
     */
    @SpringBootApplication
    @EnableJpaRepositories(basePackages = {
            "io.github.mahorobonheur.audittrail.repository",
            "io.github.mahorobonheur.audittrail.integration"
    })
    @EntityScan(basePackages = {
            "io.github.mahorobonheur.audittrail.model",
            "io.github.mahorobonheur.audittrail.integration"
    })
    static class TestApp { }

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
