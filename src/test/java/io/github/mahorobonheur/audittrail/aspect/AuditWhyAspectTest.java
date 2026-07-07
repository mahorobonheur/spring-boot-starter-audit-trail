package io.github.mahorobonheur.audittrail.aspect;

import io.github.mahorobonheur.audittrail.annotation.AuditWhy;
import io.github.mahorobonheur.audittrail.context.AuditWhyContext;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuditWhyAspectTest {

    @Mock private ProceedingJoinPoint joinPoint;
    @Mock private MethodSignature methodSignature;

    private final AuditWhyAspect aspect = new AuditWhyAspect();

    @AfterEach
    void tearDown() {
        AuditWhyContext.clear();
    }

    @Test
    @DisplayName("captureWhyReason stores @AuditWhy parameter in context")
    void captureWhyReason_setsContext() throws Throwable {
        TestService target = new TestService();
        Method method = TestService.class.getDeclaredMethod("update", String.class, String.class);

        when(joinPoint.getSignature()).thenReturn(methodSignature);
        when(methodSignature.getName()).thenReturn("update");
        when(methodSignature.getParameterTypes()).thenReturn(method.getParameterTypes());
        when(joinPoint.getTarget()).thenReturn(target);
        when(joinPoint.getArgs()).thenReturn(new Object[]{"42", "approval workflow"});
        when(joinPoint.proceed()).thenAnswer(invocation -> {
            assertThat(AuditWhyContext.get()).contains("approval workflow");
            return "ok";
        });

        Object result = aspect.captureWhyReason(joinPoint);

        assertThat(result).isEqualTo("ok");
        assertThat(AuditWhyContext.get()).isEmpty();
    }

  static class TestService {
        String update(String id, @AuditWhy String reason) {
            return "ok";
        }
    }
}
