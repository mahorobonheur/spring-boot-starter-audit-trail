package io.github.mahorobonheur.audittrail.writer;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.mahorobonheur.audittrail.model.AuditAction;
import io.github.mahorobonheur.audittrail.model.FieldDiff;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LogAuditLogWriterTest {

    private ListAppender<ILoggingEvent> appender;
    private Logger auditLogger;

    @BeforeEach
    void attachAppender() {
        auditLogger = (Logger) LoggerFactory.getLogger("audit-trail");
        appender = new ListAppender<>();
        appender.start();
        auditLogger.addAppender(appender);
    }

    @AfterEach
    void detachAppender() {
        auditLogger.detachAppender(appender);
    }

    @Test
    @DisplayName("Writes a structured audit line to the audit-trail log category")
    void write_emitsStructuredLogLine() {
        LogAuditLogWriter writer = new LogAuditLogWriter(new ObjectMapper());

        writer.write("User", "42", AuditAction.UPDATE, "alice",
                List.of(new FieldDiff("email", "a@x.com", "b@x.com")));

        assertThat(appender.list).hasSize(1);
        String message = appender.list.get(0).getFormattedMessage();
        assertThat(message)
                .contains("entity=User")
                .contains("id=42")
                .contains("action=UPDATE")
                .contains("by=alice")
                .contains("email");
    }

    @Test
    @DisplayName("Never throws — empty diff list serialises to an empty JSON array")
    void write_withEmptyDiffs_doesNotThrow() {
        LogAuditLogWriter writer = new LogAuditLogWriter(new ObjectMapper());

        writer.write("User", "1", AuditAction.DELETE, "anonymous", List.of());

        assertThat(appender.list).hasSize(1);
        assertThat(appender.list.get(0).getFormattedMessage()).contains("diffs=[]");
    }
}
