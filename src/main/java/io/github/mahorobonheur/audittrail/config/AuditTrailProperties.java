package io.github.mahorobonheur.audittrail.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for the audit trail starter.
 *
 * <p>All properties are prefixed with {@code audit-trail} in
 * {@code application.properties} / {@code application.yml}.
 *
 * <h2>Example {@code application.properties}</h2>
 * <pre>
 * audit-trail.enabled=true
 * audit-trail.table-name=audit_log
 * audit-trail.async=true
 * audit-trail.rest.enabled=true
 * audit-trail.rest.base-path=/audit-trail
 * </pre>
 *
 * @author Bonheur Mahoro
 */
@ConfigurationProperties(prefix = "audit-trail")
public class AuditTrailProperties {

    /** Whether the audit trail feature is globally enabled. Defaults to {@code true}. */
    private boolean enabled = true;

    /**
     * Name of the database table where audit log entries are stored.
     * Defaults to {@code audit_log}.
     */
    private String tableName = "audit_log";

    /**
     * Whether audit log writes should happen asynchronously.
     * When {@code true} (default), writes are performed on a background thread
     * so that the originating request is not blocked.
     */
    private boolean async = true;

    /** REST endpoint configuration. */
    private Rest rest = new Rest();

    // Nested config

    /**
     * Configuration for the optional REST query endpoint.
     */
    public static class Rest {

        /** Whether to expose the audit trail REST endpoint. Defaults to {@code true}. */
        private boolean enabled = true;

        /**
         * Base path for the REST endpoint.
         * Defaults to {@code /audit-trail}.
         * The full URL pattern is {@code {basePath}/{entityName}/{entityId}}.
         */
        private String basePath = "/audit-trail";

        public boolean isEnabled()         { return enabled; }
        public void setEnabled(boolean v)  { this.enabled = v; }
        public String getBasePath()        { return basePath; }
        public void setBasePath(String v)  { this.basePath = v; }
    }

    // ── Getters & setters

    public boolean isEnabled()              { return enabled; }
    public void setEnabled(boolean v)       { this.enabled = v; }
    public String getTableName()            { return tableName; }
    public void setTableName(String v)      { this.tableName = v; }
    public boolean isAsync()                { return async; }
    public void setAsync(boolean v)         { this.async = v; }
    public Rest getRest()                   { return rest; }
    public void setRest(Rest v)             { this.rest = v; }
}
