package io.github.mahorobonheur.audittrail.annotation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import static org.assertj.core.api.Assertions.assertThat;

class AuditTrailAnnotationTest {

    @Test
    @DisplayName("@AuditTrail is retained at runtime on entity types")
    void auditTrail_isRuntimeRetentionOnTypes() {
        assertThat(AuditTrail.class.getAnnotation(Retention.class).value())
                .isEqualTo(RetentionPolicy.RUNTIME);
        assertThat(AuditTrail.class.getAnnotation(Target.class).value())
                .containsExactly(java.lang.annotation.ElementType.TYPE);
    }
}
