package io.github.mahorobonheur.audittrail.aspect;

import io.github.mahorobonheur.audittrail.annotation.AuditWhy;
import io.github.mahorobonheur.audittrail.context.AuditWhyContext;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;

/**
 * AOP aspect that intercepts Spring-managed bean methods, detects parameters
 * annotated with {@link AuditWhy}, and stores the string value in
 * {@link AuditWhyContext} for the duration of the method call.
 *
 * <h2>Timing</h2>
 * <p>Hibernate fires {@code PRE_UPDATE} and {@code PRE_DELETE} events during the
 * flush phase, which happens when the surrounding transaction commits — <em>after</em>
 * the service method has already returned. A naive {@code finally} block would
 * therefore clear the context before the audit listener ever reads it.
 *
 * <p>To handle this correctly, when a Spring transaction is active the aspect
 * registers a {@link TransactionSynchronization#afterCompletion} callback that
 * clears the context only after the transaction has fully committed or rolled back
 * (and the Hibernate flush is complete). When no transaction is active the context
 * is cleared in the normal {@code finally} block.
 *
 * <h2>Annotation resolution</h2>
 * <p>Spring proxies (CGLIB or JDK dynamic proxy) may expose the interface method
 * rather than the concrete implementation method via {@link MethodSignature#getMethod()}.
 * If {@link AuditWhy} is declared only on the implementation parameter, the interface
 * method will not carry it. This aspect resolves the <em>target class</em> method
 * directly to guarantee the annotation is found regardless of proxy type.
 *
 * @author Bonheur Mahoro
 */
@Aspect
public class AuditWhyAspect {

    /**
     * Intercepts methods that declare at least one parameter annotated with
     * {@link AuditWhy}, stores the reason in {@link AuditWhyContext}, and
     * schedules context cleanup to run after the surrounding transaction completes.
     */
    @Around("execution(* *(.., @io.github.mahorobonheur.audittrail.annotation.AuditWhy (*), ..))")
    public Object captureWhyReason(ProceedingJoinPoint pjp) throws Throwable {
        MethodSignature sig    = (MethodSignature) pjp.getSignature();
        // Resolve the actual implementation method so that @AuditWhy on the
        // implementation parameter is visible even through a proxy.
        Method      method = resolveMethod(pjp, sig);
        Parameter[] params = method.getParameters();
        Object[]    args   = pjp.getArgs();

        String reason = null;
        for (int i = 0; i < params.length; i++) {
            if (params[i].isAnnotationPresent(AuditWhy.class) && args[i] instanceof String s) {
                reason = s;
                break;
            }
        }

        if (reason != null) {
            AuditWhyContext.set(reason);

            // When a transaction is active the Hibernate flush (and therefore the
            // audit event) fires AFTER this method returns — during commit. Register
            // a synchronization to clear the context only after that flush is done.
            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronization() {
                        @Override
                        public void afterCompletion(int status) {
                            AuditWhyContext.clear();
                        }
                    }
                );
            }
        }

        try {
            return pjp.proceed();
        } finally {
            // Only clear immediately when there is NO active transaction.
            // With an active transaction, the synchronization callback above
            // handles cleanup after the commit/flush.
            if (reason != null && !TransactionSynchronizationManager.isSynchronizationActive()) {
                AuditWhyContext.clear();
            }
        }
    }

    /**
     * Returns the declared method on the target implementation class, falling back
     * to the proxy/interface method if the target class does not declare it directly.
     */
    private Method resolveMethod(ProceedingJoinPoint pjp, MethodSignature sig) {
        try {
            return pjp.getTarget().getClass()
                      .getDeclaredMethod(sig.getName(), sig.getParameterTypes());
        } catch (NoSuchMethodException e) {
            return sig.getMethod();
        }
    }
}
