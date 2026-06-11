package io.github.mahorobonheur.audittrail.config;

import org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy;
import org.hibernate.boot.model.naming.Identifier;
import org.hibernate.boot.model.naming.PhysicalNamingStrategy;
import org.hibernate.engine.jdbc.env.spi.JdbcEnvironment;

/**
 * Maps the logical {@code audit_log} table to the name configured via {@code audit-trail.table-name}.
 *
 * <p>All other identifiers are delegated to {@link CamelCaseToUnderscoresNamingStrategy} —
 * the same strategy Spring Boot applies by default — so registering this bean does not
 * change the naming of the host application's own tables and columns.
 */
public class AuditTrailTableNamingStrategy implements PhysicalNamingStrategy {

    static final String DEFAULT_LOGICAL_TABLE = "audit_log";

    private final PhysicalNamingStrategy delegate;
    private final String auditTableName;

    AuditTrailTableNamingStrategy(String auditTableName) {
        this(new CamelCaseToUnderscoresNamingStrategy(), auditTableName);
    }

    AuditTrailTableNamingStrategy(PhysicalNamingStrategy delegate, String auditTableName) {
        this.delegate = delegate;
        this.auditTableName = auditTableName;
    }

    @Override
    public Identifier toPhysicalCatalogName(Identifier name, JdbcEnvironment context) {
        return delegate.toPhysicalCatalogName(name, context);
    }

    @Override
    public Identifier toPhysicalSchemaName(Identifier name, JdbcEnvironment context) {
        return delegate.toPhysicalSchemaName(name, context);
    }

    @Override
    public Identifier toPhysicalTableName(Identifier logicalName, JdbcEnvironment context) {
        if (logicalName != null && DEFAULT_LOGICAL_TABLE.equals(logicalName.getText())) {
            return Identifier.toIdentifier(auditTableName, logicalName.isQuoted());
        }
        return delegate.toPhysicalTableName(logicalName, context);
    }

    @Override
    public Identifier toPhysicalSequenceName(Identifier logicalName, JdbcEnvironment context) {
        return delegate.toPhysicalSequenceName(logicalName, context);
    }

    @Override
    public Identifier toPhysicalColumnName(Identifier logicalName, JdbcEnvironment context) {
        return delegate.toPhysicalColumnName(logicalName, context);
    }
}
