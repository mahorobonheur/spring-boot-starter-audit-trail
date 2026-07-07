package io.github.mahorobonheur.audittrail.support;

import io.github.mahorobonheur.audittrail.model.AuditAction;
import io.github.mahorobonheur.audittrail.model.AuditLog;

import java.lang.reflect.Field;
import java.time.Instant;

/** Test helper for building {@link AuditLog} instances with a stable id. */
public final class TestAuditLogs {

    private TestAuditLogs() { }

    public static AuditLog entry(String id,
                                 String entityName,
                                 String entityId,
                                 AuditAction action,
                                 Instant changedAt,
                                 String fieldDiffs,
                                 String prevHash) {
        AuditLog log = new AuditLog(
                entityName, entityId, action, "alice", changedAt, fieldDiffs,
                null, false, null, prevHash);
        setId(log, id);
        return log;
    }

    public static void setId(AuditLog log, String id) {
        try {
            Field f = AuditLog.class.getDeclaredField("id");
            f.setAccessible(true);
            f.set(log, id);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
