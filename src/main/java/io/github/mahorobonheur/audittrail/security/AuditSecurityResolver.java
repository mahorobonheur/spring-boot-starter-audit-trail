package io.github.mahorobonheur.audittrail.security;

import org.springframework.util.ClassUtils;

/**
 * Resolves the currently authenticated user from Spring Security's
 * {@code SecurityContextHolder}.
 *
 * <p>Spring Security is an <em>optional</em> dependency of this starter. When it is
 * not on the classpath, or when there is no active authentication (e.g. background
 * jobs, scheduled tasks, or unauthenticated requests), this resolver falls back to
 * {@code "anonymous"}.
 *
 * @author Bonheur Mahoro
 */
public class AuditSecurityResolver {

    private static final String ANONYMOUS = "anonymous";

    private static final boolean SPRING_SECURITY_PRESENT = ClassUtils.isPresent(
            "org.springframework.security.core.context.SecurityContextHolder",
            AuditSecurityResolver.class.getClassLoader());

    /**
     * Returns the username of the currently authenticated principal.
     *
     * @return the principal name, or {@code "anonymous"} if Spring Security is absent
     *         or no user is authenticated
     */
    public String getCurrentUser() {
        if (!SPRING_SECURITY_PRESENT) {
            return ANONYMOUS;
        }
        return SpringSecurityLookup.currentPrincipalName();
    }

    /**
     * Isolates all references to Spring Security classes so that this class can be
     * loaded and used even when Spring Security is not on the classpath. The inner
     * class is only initialised when {@link #SPRING_SECURITY_PRESENT} is {@code true}.
     */
    private static final class SpringSecurityLookup {

        static String currentPrincipalName() {
            org.springframework.security.core.Authentication auth =
                    org.springframework.security.core.context.SecurityContextHolder
                            .getContext().getAuthentication();
            if (auth == null || !auth.isAuthenticated()) {
                return ANONYMOUS;
            }
            String name = auth.getName();
            return (name == null || name.isBlank()) ? ANONYMOUS : name;
        }
    }
}
