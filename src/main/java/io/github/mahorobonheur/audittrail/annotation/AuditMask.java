package io.github.mahorobonheur.audittrail.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks an entity field whose change should be <em>recorded structurally but not stored</em>.
 *
 * <p>When a field annotated with {@code @AuditMask} changes, the audit log will record
 * that the field was modified (the diff entry is included), but the old and new values
 * are replaced with {@code "[MASKED]"} instead of the actual data. The {@code is_masked}
 * flag on the {@link io.github.mahorobonheur.audittrail.model.AuditLog} entry is set
 * to {@code true}.
 *
 * <p>This satisfies compliance requirements that demand proof a change occurred (e.g.
 * HIPAA, PCI-DSS) without creating a secondary unencrypted store of sensitive values
 * such as passwords, tokens, credit card numbers, or health information.
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * @Entity
 * @AuditTrail
 * public class User {
 *     private String email;
 *
 *     @AuditMask
 *     private String password;       // audit records the change; value never stored
 *
 *     @AuditMask
 *     private String refreshToken;   // same — structural audit only
 * }
 * }</pre>
 *
 * <p>{@code @AuditMask} and {@link AuditExclude} are mutually exclusive: use
 * {@code @AuditExclude} when the field should not appear in the audit log at all, and
 * {@code @AuditMask} when it should appear but without its value.
 *
 * @author Bonheur Mahoro
 * @see AuditExclude
 */
@Documented
@Inherited
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface AuditMask {

    /**
     * The placeholder string written in place of the actual field value.
     * Defaults to {@code "[MASKED]"}.
     *
     * @return the masking placeholder
     */
    String placeholder() default "[MASKED]";
}
