package io.github.mahorobonheur.audittrail.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Documents a manual snapshot point in application code.
 *
 * <p>This is a documentation-only marker annotation — it is <em>not</em> backed
 * by AOP. Use it to annotate methods that explicitly call
 * {@link io.github.mahorobonheur.audittrail.service.AuditSnapshotService#capture}
 * or {@link io.github.mahorobonheur.audittrail.service.AuditSnapshotService#captureWithReason}
 * to make snapshot points discoverable during code review and documentation
 * generation.
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * @AuditSnapshot("pre-approval-state")
 * public void submitForApproval(Contract contract) {
 *     snapshotService.capture(contract, "pre-approval-state");
 *     contract.setStatus(Status.PENDING_APPROVAL);
 *     contractRepository.save(contract);
 * }
 * }</pre>
 *
 * @author Bonheur Mahoro
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface AuditSnapshot {

    /**
     * A human-readable label identifying this snapshot point.
     * Defaults to an empty string.
     *
     * @return the snapshot label
     */
    String value() default "";
}
