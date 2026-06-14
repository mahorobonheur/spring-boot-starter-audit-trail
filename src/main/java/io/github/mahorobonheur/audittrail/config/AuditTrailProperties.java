package io.github.mahorobonheur.audittrail.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Collections;
import java.util.List;

/**
 * Configuration properties for the audit trail starter.
 *
 * <p>All properties are prefixed with {@code audit-trail} in
 * {@code application.properties} / {@code application.yml}.
 *
 * <h2>Example {@code application.properties}</h2>
 * <pre>
 * audit-trail.enabled=true
 * audit-trail.mode=ANNOTATED
 * audit-trail.storage=database
 * audit-trail.table-name=audit_log
 * audit-trail.async=true
 * audit-trail.rest.enabled=true
 * audit-trail.rest.base-path=/audit-trail
 * audit-trail.chain.enabled=false
 * audit-trail.anomaly.enabled=false
 * audit-trail.anomaly.bulk-delete-threshold=10
 * audit-trail.anomaly.rapid-change-threshold=50
 * audit-trail.anomaly.window-seconds=60
 * audit-trail.dashboard.enabled=false
 * </pre>
 *
 * @author Bonheur Mahoro
 */
@ConfigurationProperties(prefix = "audit-trail")
public class AuditTrailProperties {

    /** Whether the audit trail feature is globally enabled. Defaults to {@code true}. */
    private boolean enabled = true;

    /**
     * Storage backend for audit events. Defaults to {@link Storage#DATABASE}.
     */
    private Storage storage = Storage.DATABASE;

    /** Available storage backends. */
    public enum Storage {
        /** Persist audit entries to the relational database (default). */
        DATABASE,
        /** Write audit entries to the application log. */
        LOG
    }

    /**
     * Auditing mode. {@link Mode#ANNOTATED} (default) — only entities annotated with
     * {@link io.github.mahorobonheur.audittrail.annotation.AuditTrail} are audited.
     * {@link Mode#ALL} — all entities are audited except those in {@link #excludeEntities}.
     */
    private Mode mode = Mode.ANNOTATED;

    /** Available auditing modes. */
    public enum Mode {
        /** Only audit entities explicitly marked with {@code @AuditTrail}. */
        ANNOTATED,
        /** Audit all JPA entities, honouring the {@code excludeEntities} list. */
        ALL
    }

    /**
     * When {@link #mode} is {@link Mode#ALL}, entity simple class names in this list
     * are excluded from auditing. Ignored when mode is {@link Mode#ANNOTATED}.
     */
    private List<String> excludeEntities = Collections.emptyList();

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

    /** Chain hashing configuration. */
    private Chain chain = new Chain();

    /** Anomaly detection configuration. */
    private Anomaly anomaly = new Anomaly();

    /** Dashboard configuration (reserved for future use). */
    private Dashboard dashboard = new Dashboard();

    // ── Nested config classes ─────────────────────────────────────────────────

    /**
     * Configuration for the optional REST query endpoint.
     */
    public static class Rest {

        private boolean enabled  = true;
        private String  basePath = "/audit-trail";

        public boolean isEnabled()        { return enabled; }
        public void setEnabled(boolean v) { this.enabled = v; }
        public String getBasePath()       { return basePath; }
        public void setBasePath(String v) { this.basePath = v; }
    }

    /**
     * Configuration for tamper-evident chain hashing.
     */
    public static class Chain {

        /** Whether SHA-256 chain hashing is enabled. Defaults to {@code false}. */
        private boolean enabled = false;

        public boolean isEnabled()        { return enabled; }
        public void setEnabled(boolean v) { this.enabled = v; }
    }

    /**
     * Configuration for in-memory sliding-window anomaly detection.
     */
    public static class Anomaly {

        /** Whether anomaly detection is enabled. Defaults to {@code false}. */
        private boolean enabled = false;

        /**
         * Maximum number of DELETE events for the same entity type within
         * {@link #windowSeconds} before a {@code BULK_DELETE} anomaly is fired.
         * Defaults to {@code 10}.
         */
        private int bulkDeleteThreshold = 10;

        /**
         * Maximum number of changes by the same actor within {@link #windowSeconds}
         * before a {@code RAPID_CHANGES} anomaly is fired. Defaults to {@code 50}.
         */
        private int rapidChangeThreshold = 50;

        /**
         * Sliding window duration in seconds. Defaults to {@code 60}.
         */
        private int windowSeconds = 60;

        public boolean isEnabled()                   { return enabled; }
        public void    setEnabled(boolean v)         { this.enabled = v; }
        public int     getBulkDeleteThreshold()      { return bulkDeleteThreshold; }
        public void    setBulkDeleteThreshold(int v) { this.bulkDeleteThreshold = v; }
        public int     getRapidChangeThreshold()     { return rapidChangeThreshold; }
        public void    setRapidChangeThreshold(int v){ this.rapidChangeThreshold = v; }
        public int     getWindowSeconds()            { return windowSeconds; }
        public void    setWindowSeconds(int v)       { this.windowSeconds = v; }
    }

    /**
     * Configuration for the optional audit trail dashboard (reserved for future use).
     */
    public static class Dashboard {

        /** Whether the dashboard is enabled. Defaults to {@code false}. */
        private boolean enabled = false;

        public boolean isEnabled()        { return enabled; }
        public void setEnabled(boolean v) { this.enabled = v; }
    }

    // ── Getters & setters ─────────────────────────────────────────────────────

    public boolean         isEnabled()                          { return enabled; }
    public void            setEnabled(boolean v)                { this.enabled = v; }
    public Storage         getStorage()                         { return storage; }
    public void            setStorage(Storage v)                { this.storage = v; }
    public Mode            getMode()                            { return mode; }
    public void            setMode(Mode v)                      { this.mode = v; }
    public List<String>    getExcludeEntities()                 { return excludeEntities; }
    public void            setExcludeEntities(List<String> v)   { this.excludeEntities = v; }
    public String          getTableName()                       { return tableName; }
    public void            setTableName(String v)               { this.tableName = v; }
    public boolean         isAsync()                            { return async; }
    public void            setAsync(boolean v)                  { this.async = v; }
    public Rest            getRest()                            { return rest; }
    public void            setRest(Rest v)                      { this.rest = v; }
    public Chain           getChain()                           { return chain; }
    public void            setChain(Chain v)                    { this.chain = v; }
    public Anomaly         getAnomaly()                         { return anomaly; }
    public void            setAnomaly(Anomaly v)                { this.anomaly = v; }
    public Dashboard       getDashboard()                       { return dashboard; }
    public void            setDashboard(Dashboard v)            { this.dashboard = v; }
}
