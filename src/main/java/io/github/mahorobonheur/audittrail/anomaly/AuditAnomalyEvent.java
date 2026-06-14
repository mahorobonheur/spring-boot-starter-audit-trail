package io.github.mahorobonheur.audittrail.anomaly;

import org.springframework.context.ApplicationEvent;

import java.time.Instant;

/**
 * Application event published when the {@link AuditAnomalyDetector} detects
 * suspicious activity in the audit stream.
 *
 * <p>Listen to this event by declaring a Spring bean that implements
 * {@link org.springframework.context.ApplicationListener ApplicationListener&lt;AuditAnomalyEvent&gt;}
 * or by using {@link org.springframework.context.event.EventListener @EventListener}:
 *
 * <pre>{@code
 * @EventListener
 * public void onAnomalyDetected(AuditAnomalyEvent event) {
 *     alertService.send("Anomaly detected: " + event.getDescription());
 * }
 * }</pre>
 *
 * @author Bonheur Mahoro
 */
public class AuditAnomalyEvent extends ApplicationEvent {

    private final String  entityName;
    private final String  entityId;
    private final String  triggeredBy;
    private final String  rule;
    private final String  description;
    private final Instant occurredAt;

    /**
     * Creates a new anomaly event.
     *
     * @param source      the bean that published the event (typically the detector)
     * @param entityName  simple class name of the entity that triggered the anomaly
     * @param entityId    string primary key of the entity (may be {@code null} for actor-scoped rules)
     * @param triggeredBy username of the actor whose actions triggered the anomaly
     * @param rule        short rule name (e.g., {@code "BULK_DELETE"}, {@code "RAPID_CHANGES"})
     * @param description human-readable description of the detected anomaly
     */
    public AuditAnomalyEvent(Object source,
                              String entityName,
                              String entityId,
                              String triggeredBy,
                              String rule,
                              String description) {
        super(source);
        this.entityName  = entityName;
        this.entityId    = entityId;
        this.triggeredBy = triggeredBy;
        this.rule        = rule;
        this.description = description;
        this.occurredAt  = Instant.now();
    }

    public String  getEntityName()  { return entityName; }
    public String  getEntityId()    { return entityId; }
    public String  getTriggeredBy() { return triggeredBy; }
    public String  getRule()        { return rule; }
    public String  getDescription() { return description; }
    public Instant getOccurredAt()  { return occurredAt; }
}
