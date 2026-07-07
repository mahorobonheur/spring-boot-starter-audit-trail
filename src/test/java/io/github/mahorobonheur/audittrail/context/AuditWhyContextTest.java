package io.github.mahorobonheur.audittrail.context;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AuditWhyContextTest {

    @AfterEach
    void tearDown() {
        AuditWhyContext.clear();
    }

    @Test
    @DisplayName("set and get round-trip the reason on the current thread")
    void setAndGet_roundTrip() {
        AuditWhyContext.set("ticket-123");
        assertThat(AuditWhyContext.get()).contains("ticket-123");
    }

    @Test
    @DisplayName("clear removes the stored reason")
    void clear_removesReason() {
        AuditWhyContext.set("reason");
        AuditWhyContext.clear();
        assertThat(AuditWhyContext.get()).isEmpty();
    }
}
