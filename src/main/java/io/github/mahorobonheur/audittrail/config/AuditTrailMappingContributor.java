package io.github.mahorobonheur.audittrail.config;

import io.github.mahorobonheur.audittrail.model.AuditLog;
import org.hibernate.boot.ResourceStreamLocator;
import org.hibernate.boot.spi.AdditionalMappingContributions;
import org.hibernate.boot.spi.AdditionalMappingContributor;
import org.hibernate.boot.spi.InFlightMetadataCollector;
import org.hibernate.boot.spi.MetadataBuildingContext;

/**
 * Registers the {@link AuditLog} entity directly with Hibernate's metadata via the
 * {@link AdditionalMappingContributor} SPI (discovered through
 * {@code META-INF/services}).
 *
 * <p>This makes the entity available in <em>every</em> host application regardless
 * of its Spring entity-scanning configuration — including applications that declare
 * their own {@code @EntityScan}, which would otherwise exclude packages contributed
 * by this starter. It also means {@code ddl-auto} schema generation creates the
 * audit log table automatically.
 */
public class AuditTrailMappingContributor implements AdditionalMappingContributor {

    @Override
    public String getContributorName() {
        return "audit-trail";
    }

    @Override
    public void contribute(AdditionalMappingContributions contributions,
                           InFlightMetadataCollector metadata,
                           ResourceStreamLocator resourceStreamLocator,
                           MetadataBuildingContext buildingContext) {
        // Skip if the host application's own entity scanning already picked it up.
        if (metadata.getEntityBinding(AuditLog.class.getName()) == null) {
            contributions.contributeEntity(AuditLog.class);
        }
    }
}
