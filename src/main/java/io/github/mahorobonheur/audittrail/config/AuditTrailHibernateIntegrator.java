package io.github.mahorobonheur.audittrail.config;

import io.github.mahorobonheur.audittrail.listener.AuditTrailEntityListener;
import org.hibernate.boot.Metadata;
import org.hibernate.boot.spi.BootstrapContext;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.event.service.spi.EventListenerRegistry;
import org.hibernate.event.spi.EventType;
import org.hibernate.event.spi.PostLoadEventListener;
import org.hibernate.event.spi.PreDeleteEventListener;
import org.hibernate.event.spi.PreInsertEventListener;
import org.hibernate.event.spi.PreUpdateEventListener;
import org.hibernate.integrator.spi.Integrator;
import org.hibernate.service.spi.ServiceRegistryImplementor;

/**
 * Registers {@link AuditTrailEntityListener} with Hibernate so entities annotated with
 * {@link io.github.mahorobonheur.audittrail.annotation.AuditTrail} are audited without
 * declaring {@code @EntityListeners} on each entity class.
 */
public class AuditTrailHibernateIntegrator implements Integrator {

    private final AuditTrailEntityListener listener = new AuditTrailEntityListener();

    @Override
    public void integrate(Metadata metadata,
                          BootstrapContext bootstrapContext,
                          SessionFactoryImplementor sessionFactory) {
        ServiceRegistryImplementor serviceRegistry = sessionFactory.getServiceRegistry();
        EventListenerRegistry registry = serviceRegistry.getService(EventListenerRegistry.class);

        registry.getEventListenerGroup(EventType.PRE_INSERT).appendListener((PreInsertEventListener) event -> {
            listener.onPrePersist(event.getEntity());
            return false;
        });
        registry.getEventListenerGroup(EventType.POST_LOAD).appendListener((PostLoadEventListener) event ->
                listener.onPostLoad(event.getEntity()));
        registry.getEventListenerGroup(EventType.PRE_UPDATE).appendListener((PreUpdateEventListener) event -> {
            listener.onPreUpdate(event.getEntity());
            return false;
        });
        registry.getEventListenerGroup(EventType.PRE_DELETE).appendListener((PreDeleteEventListener) event -> {
            listener.onPreRemove(event.getEntity());
            return false;
        });
    }
}
