package io.github.mahorobonheur.audittrail.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Excludes a specific field from audit trail tracking.
 *
 * <p>Apply this annotation directly to a field in an {@link AuditTrail}-annotated entity
 * to prevent that field from appearing in recorded diffs. This is the field-level
 * alternative to the {@code exclude} attribute on {@link AuditTrail}.
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * @Entity
 * @AuditTrail
 * public class User {
 *     private String email;
 *
 *     @AuditExclude
 *     private String password;   // never recorded in audit log
 *
 *     @AuditExclude
 *     private String refreshToken;
 * }
 * }</pre>
 *
 * @author Bonheur Mahoro
 * @see AuditTrail
 */
@Documented
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface AuditExclude {
}
