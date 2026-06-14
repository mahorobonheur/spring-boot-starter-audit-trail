package io.github.mahorobonheur.audittrail.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a {@code String} method parameter as the business reason / justification
 * for the change being made.
 *
 * <p>When a Spring-managed service method is annotated with a {@code @AuditWhy}
 * parameter, the audit trail AOP aspect ({@code AuditWhyAspect}) intercepts the call,
 * stores the reason in a thread-local context, and the next audit entry written on
 * that thread will include the reason in its {@code why_reason} column.
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * @Service
 * public class UserService {
 *
 *     public void promoteToAdmin(User user, @AuditWhy String reason) {
 *         user.setRole("ADMIN");
 *         userRepository.save(user);  // audit entry will include reason
 *     }
 * }
 *
 * // Caller:
 * userService.promoteToAdmin(user, "Approved by VP Engineering on 2026-06-14");
 * }</pre>
 *
 * <p>The reason can also be set programmatically without using this annotation:
 * <pre>{@code
 * AuditWhyContext.set("Manual data correction — ticket JIRA-1234");
 * try {
 *     userRepository.save(user);
 * } finally {
 *     AuditWhyContext.clear();
 * }
 * }</pre>
 *
 * @author Bonheur Mahoro
 * @see io.github.mahorobonheur.audittrail.context.AuditWhyContext
 */
@Documented
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface AuditWhy {
}
