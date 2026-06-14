package io.github.mahorobonheur.audittrail.service;

import io.github.mahorobonheur.audittrail.model.AuditLog;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

/**
 * Provides SHA-256 chain-hashing for audit log entries, forming a tamper-evident
 * linked list analogous to a blockchain.
 *
 * <p>Enabled only when {@code audit-trail.chain.enabled=true}. When active,
 * each new {@link AuditLog} entry for an entity+id receives a {@code prevHash}
 * value computed from the previous entry's hash, creating a verifiable chain.
 *
 * <p>Verification can detect whether any entry in a chain has been silently
 * modified after the fact via {@link #verifyChain(List)}.
 *
 * @author Bonheur Mahoro
 */
@Service
@ConditionalOnProperty(prefix = "audit-trail.chain", name = "enabled", havingValue = "true")
public class AuditChainService {

    private static final String SHA_256 = "SHA-256";

    /**
     * Computes a SHA-256 hash for the given audit log entry, covering its most
     * critical immutable fields.
     *
     * @param entry the audit log entry to hash
     * @return lowercase hex-encoded 64-character SHA-256 digest
     */
    public String computeEntryHash(AuditLog entry) {
        String input = String.valueOf(entry.getId())
                + entry.getEntityName()
                + entry.getEntityId()
                + entry.getAction()
                + entry.getChangedAt()
                + entry.getFieldDiffs();
        return sha256(input);
    }

    /**
     * Computes a chain hash by combining the previous entry's hash with the
     * current entry's own hash. This links entries together so that modifying
     * any earlier entry invalidates all subsequent chain hashes.
     *
     * @param prevHash  the {@code prevHash} stored on the immediately preceding entry,
     *                  or an empty string / {@code null} for the first entry
     * @param entry     the new entry being appended to the chain
     * @return lowercase hex-encoded 64-character SHA-256 chain hash
     */
    public String computeChainHash(String prevHash, AuditLog entry) {
        String combined = (prevHash == null ? "" : prevHash) + computeEntryHash(entry);
        return sha256(combined);
    }

    /**
     * Verifies the integrity of a chain of audit entries.
     *
     * <p>The entries <strong>must</strong> be ordered by {@code changedAt} ascending
     * (earliest first). The method re-computes chain hashes and compares each entry's
     * stored {@code prevHash} against the expected value.
     *
     * @param entries audit log entries for a single entity+id, ordered oldest-first
     * @return a {@link ChainVerificationResult} indicating whether the chain is intact
     *         and, if not, the id of the first broken entry
     */
    public ChainVerificationResult verifyChain(List<AuditLog> entries) {
        if (entries == null || entries.isEmpty()) {
            return new ChainVerificationResult(true, null);
        }

        String runningHash = null;

        for (AuditLog entry : entries) {
            String storedPrevHash = entry.getPrevHash();

            // First entry: both stored and running hash should be null
            if (runningHash == null && storedPrevHash == null) {
                // Correct — first link in the chain
            } else if (!java.util.Objects.equals(runningHash, storedPrevHash)) {
                return new ChainVerificationResult(false, entry.getId());
            }

            // Advance the running hash: what the *next* entry's prevHash should be
            runningHash = computeChainHash(storedPrevHash, entry);
        }

        return new ChainVerificationResult(true, null);
    }

    // ── Nested record ─────────────────────────────────────────────────────────

    /**
     * Result of a chain verification pass.
     *
     * @param valid       {@code true} when every entry's {@code prevHash} is consistent
     * @param brokenAtId  the {@link AuditLog#getId()} of the first inconsistent entry,
     *                    or {@code null} when {@code valid} is {@code true}
     */
    public record ChainVerificationResult(boolean valid, String brokenAtId) { }

    // ── Private helpers ───────────────────────────────────────────────────────

    private String sha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance(SHA_256);
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is mandated by the JVM spec — this should never happen
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }
}
