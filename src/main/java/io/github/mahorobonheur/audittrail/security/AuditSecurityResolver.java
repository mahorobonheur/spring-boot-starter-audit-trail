package io.github.mahorobonheur.audittrail.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
/**
 * Resolves the currently authenticated user from Spring Security's
 * {@link SecurityContextHolder}.
 *
 * <p>Falls back to {@code "anonymous"} when there is no active authentication
 * (e.g. in background jobs, scheduled tasks, or unauthenticated requests).
 *
 * @author Bonheur Mahoro
 */
public class AuditSecurityResolver {

    private static final String ANONYMOUS = "anonymous";

    /**
     * Returns the username of the currently authenticated principal.
     *
     * @return the principal name, or {@code "anonymous"} if not authenticated
     */
    public String getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return ANONYMOUS;
        }
        String name = auth.getName();
        return (name == null || name.isBlank()) ? ANONYMOUS : name;
    }
}
