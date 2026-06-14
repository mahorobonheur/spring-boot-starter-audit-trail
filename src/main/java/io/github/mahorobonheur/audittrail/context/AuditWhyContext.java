package io.github.mahorobonheur.audittrail.context;

import java.util.Optional;

/**
 * Thread-local holder for the business reason ({@code why}) associated with the
 * next audit event recorded on the current thread.
 *
 * <p>The reason is typically set by
 * {@link io.github.mahorobonheur.audittrail.aspect.AuditWhyAspect} when a
 * Spring bean method carries an {@link io.github.mahorobonheur.audittrail.annotation.AuditWhy}
 * parameter, but it can also be set programmatically:
 *
 * <pre>{@code
 * AuditWhyContext.set("Manual data correction — ticket JIRA-1234");
 * try {
 *     userRepository.save(user);
 * } finally {
 *     AuditWhyContext.clear();
 * }
 * }</pre>
 *
 * <p><strong>Always call {@link #clear()} in a {@code finally} block</strong>
 * to prevent the reason from leaking to subsequent operations on a pooled thread.
 *
 * @author Bonheur Mahoro
 */
public final class AuditWhyContext {

    private static final ThreadLocal<String> HOLDER = new ThreadLocal<>();

    // Private constructor — utility class
    private AuditWhyContext() { }

    /**
     * Sets the business reason for the next audit entry on this thread.
     *
     * @param reason a non-null, human-readable justification string
     */
    public static void set(String reason) {
        HOLDER.set(reason);
    }

    /**
     * Returns the current business reason, if any.
     *
     * @return an {@link Optional} containing the reason, or empty if none is set
     */
    public static Optional<String> get() {
        return Optional.ofNullable(HOLDER.get());
    }

    /**
     * Removes the current business reason from the thread-local storage.
     * Should always be called after the audited operation completes.
     */
    public static void clear() {
        HOLDER.remove();
    }
}
