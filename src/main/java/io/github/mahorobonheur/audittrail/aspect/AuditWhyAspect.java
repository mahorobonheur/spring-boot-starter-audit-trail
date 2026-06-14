package io.github.mahorobonheur.audittrail.aspect;

import io.github.mahorobonheur.audittrail.annotation.AuditWhy;
import io.github.mahorobonheur.audittrail.context.AuditWhyContext;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;

import java.lang.reflect.Parameter;

/**
 * AOP aspect that intercepts Spring-managed bean methods, detects parameters
 * annotated with {@link AuditWhy}, and stores the string value in
 * {@link AuditWhyContext} for the duration of the method call.
 *
 * <p>The reason is automatically cleared in a {@code finally} block, so it is
 * safe even if the method throws.
 *
 * <h2>Pointcut strategy</h2>
 * <p>The aspect intercepts methods on beans annotated with common Spring
 * stereotypes ({@code @Service}, {@code @Component}, {@code @Repository},
 * {@code @Controller}/{@code @RestController}). This keeps the scope tight —
 * only Spring-managed beans participate, which is where {@code @AuditWhy}
 * parameters are expected.
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * @Service
 * public class UserService {
 *     public void promoteToAdmin(User user, @AuditWhy String reason) {
 *         user.setRole("ADMIN");
 *         userRepository.save(user);   // audit entry carries the reason
 *     }
 * }
 * }</pre>
 *
 * @author Bonheur Mahoro
 */
@Aspect
public class AuditWhyAspect {

    /**
     * Intercepts any method on a Spring stereotype bean. If any parameter is
     * annotated with {@link AuditWhy} and is a non-null {@link String}, that value
     * is stored in {@link AuditWhyContext} before proceeding. The context is always
     * cleared in the {@code finally} block.
     */
    @Around(
        "within(@org.springframework.stereotype.Service *) || " +
        "within(@org.springframework.stereotype.Component *) || " +
        "within(@org.springframework.stereotype.Repository *) || " +
        "within(@org.springframework.web.bind.annotation.RestController *) || " +
        "within(@org.springframework.stereotype.Controller *)"
    )
    public Object captureWhyReason(ProceedingJoinPoint pjp) throws Throwable {
        MethodSignature sig    = (MethodSignature) pjp.getSignature();
        Parameter[]     params = sig.getMethod().getParameters();
        Object[]        args   = pjp.getArgs();

        String reason = null;
        for (int i = 0; i < params.length; i++) {
            if (params[i].isAnnotationPresent(AuditWhy.class) && args[i] instanceof String s) {
                reason = s;
                break;
            }
        }

        if (reason != null) {
            AuditWhyContext.set(reason);
        }

        try {
            return pjp.proceed();
        } finally {
            if (reason != null) {
                AuditWhyContext.clear();
            }
        }
    }
}
