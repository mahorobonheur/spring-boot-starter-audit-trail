package io.github.mahorobonheur.audittrail.integration;

import io.github.mahorobonheur.audittrail.AuditTrailTestApp;
import io.github.mahorobonheur.audittrail.model.AuditAction;
import io.github.mahorobonheur.audittrail.model.AuditLog;
import io.github.mahorobonheur.audittrail.repository.AuditLogRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Integration tests against a real PostgreSQL database via Testcontainers.
 */
@SpringBootTest(classes = AuditTrailTestApp.class)
@ActiveProfiles("testcontainers")
@Testcontainers(disabledWithoutDocker = true)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class AuditTrailPostgresIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    TestUserRepository userRepo;

    @Autowired
    AuditLogRepository auditRepo;

    @Test
    @WithMockUser(username = "bonheur")
    @DisplayName("PostgreSQL: CREATE event writes audit log")
    void createEntity_auditLogCreated() {
        userRepo.save(new TestUser("alice", "alice@x.com", "secret", "USER"));

        await().atMost(3, TimeUnit.SECONDS).untilAsserted(() -> {
            Page<AuditLog> logs = auditRepo.findByEntityName("TestUser", PageRequest.of(0, 20));
            assertThat(logs.getContent()).anyMatch(l -> l.getAction() == AuditAction.CREATE
                    && l.getChangedBy().equals("bonheur"));
        });
    }

    @Test
    @DisplayName("PostgreSQL: unauthenticated request records changed_by as anonymous")
    void createEntity_withoutAuth_recordsAnonymous() {
        userRepo.save(new TestUser("anon", "anon@x.com", "secret", "USER"));

        await().atMost(3, TimeUnit.SECONDS).untilAsserted(() -> {
            Page<AuditLog> logs = auditRepo.findByEntityName("TestUser", PageRequest.of(0, 20));
            assertThat(logs.getContent()).anyMatch(l -> l.getAction() == AuditAction.CREATE
                    && l.getChangedBy().equals("anonymous"));
        });
    }

    @Test
    @WithMockUser(username = "bonheur")
    @DisplayName("PostgreSQL: UPDATE event records field-level diff")
    void updateEntity_fieldDiffRecorded() {
        TestUser user = userRepo.save(new TestUser("bob", "bob@x.com", "pass", "USER"));
        user.email = "newemail@x.com";
        userRepo.saveAndFlush(user);

        await().atMost(3, TimeUnit.SECONDS).untilAsserted(() -> {
            Page<AuditLog> logs = auditRepo.findByEntityName("TestUser", PageRequest.of(0, 20));
            assertThat(logs.getContent()).anyMatch(l -> l.getAction() == AuditAction.UPDATE
                    && l.getFieldDiffs().contains("email"));
        });
    }
}
