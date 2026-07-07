package io.github.mahorobonheur.audittrail.service;

import io.github.mahorobonheur.audittrail.model.AuditAction;
import io.github.mahorobonheur.audittrail.model.AuditLog;
import io.github.mahorobonheur.audittrail.support.TestAuditLogs;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AuditChainServiceTest {

    private AuditChainService chainService;
    private static final Instant T0 = Instant.parse("2026-01-01T00:00:00Z");
    private static final String DIFFS = "[{\"field\":\"email\",\"oldValue\":\"a\",\"newValue\":\"b\"}]";

    @BeforeEach
    void setUp() {
        chainService = new AuditChainService();
    }

    @Test
    @DisplayName("computeEntryHash is deterministic for the same entry")
    void computeEntryHash_isDeterministic() {
        AuditLog entry = TestAuditLogs.entry("e1", "User", "42", AuditAction.UPDATE, T0, DIFFS, null);
        assertThat(chainService.computeEntryHash(entry)).isEqualTo(chainService.computeEntryHash(entry));
        assertThat(chainService.computeEntryHash(entry)).hasSize(64);
    }

    @Test
    @DisplayName("verifyChain accepts empty or null lists")
    void verifyChain_emptyOrNull_isValid() {
        assertThat(chainService.verifyChain(List.of()).valid()).isTrue();
        assertThat(chainService.verifyChain(null).valid()).isTrue();
    }

    @Test
    @DisplayName("verifyChain accepts a single anchor entry")
    void verifyChain_singleAnchor_isValid() {
        AuditLog anchor = TestAuditLogs.entry("e1", "User", "42", AuditAction.CREATE, T0, DIFFS, null);
        assertThat(chainService.verifyChain(List.of(anchor)).valid()).isTrue();
    }

    @Test
    @DisplayName("verifyChain accepts a correctly linked two-entry chain")
    void verifyChain_linkedPair_isValid() {
        AuditLog first = TestAuditLogs.entry("e1", "User", "42", AuditAction.CREATE, T0, DIFFS, null);
        String linkHash = chainService.computeChainHash(null, first);
        AuditLog second = TestAuditLogs.entry(
                "e2", "User", "42", AuditAction.UPDATE, T0.plusSeconds(1), DIFFS, linkHash);

        assertThat(chainService.verifyChain(List.of(first, second)).valid()).isTrue();
    }

    @Test
    @DisplayName("verifyChain detects a tampered prevHash")
    void verifyChain_tamperedPrevHash_isInvalid() {
        AuditLog first = TestAuditLogs.entry("e1", "User", "42", AuditAction.CREATE, T0, DIFFS, null);
        AuditLog second = TestAuditLogs.entry(
                "e2", "User", "42", AuditAction.UPDATE, T0.plusSeconds(1), DIFFS, "deadbeef");

        AuditChainService.ChainVerificationResult result =
                chainService.verifyChain(List.of(first, second));

        assertThat(result.valid()).isFalse();
        assertThat(result.brokenAtId()).isEqualTo("e2");
    }

    @Test
    @DisplayName("computeChainHash handles null prevHash as empty string")
    void computeChainHash_nullPrevHash_matchesEmptyString() {
        AuditLog entry = TestAuditLogs.entry("e1", "User", "42", AuditAction.CREATE, T0, DIFFS, null);
        assertThat(chainService.computeChainHash(null, entry))
                .isEqualTo(chainService.computeChainHash("", entry));
    }
}
