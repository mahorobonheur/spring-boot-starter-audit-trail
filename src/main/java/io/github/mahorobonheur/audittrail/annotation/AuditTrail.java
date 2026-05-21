package io.github.mahorobonheur.audittrail.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a JPA entity for automatic audit trail tracking.
 *
 * <p>When applied to an entity class, the library will automatically intercept
 * {@code CREATE}, {@code UPDATE}, and {@code DELETE} lifecycle events and persist
 * a structured, field-level diff to the audit log table.
 *
 * <h2>Basic usage</h2>
 * <pre>{@code
 * @Entity
 * @AuditTrail
 * public class User {
 *     private Long id;
 *     private String email;
 *     private String role;
 * }
 * }</pre>
 *
 * <h2>Excluding sensitive fields</h2>
 * <pre>{@code
 * @Entity
 * @AuditTrail(exclude = {"password", "refreshToken"})
 * public class User { ... }
 * }</pre>
 *
 * <p>Individual fields can also be excluded with {@link AuditExclude}.
 *
 * @author Bonheur Mahoro
 * @see AuditExclude
 */
@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface AuditTrail {

    /**
     * Field names to exclude from audit tracking.
     *
     * <p>These fields will not appear in the recorded diff even when their values change.
     * Use this for sensitive data such as passwords, tokens, or PII that should not
     * be stored in the audit log.
     *
     * @return array of field names to exclude (default: none)
     */
    String[] exclude() default {};
}
