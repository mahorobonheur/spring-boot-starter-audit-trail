package io.github.mahorobonheur.audittrail.integration;

import io.github.mahorobonheur.audittrail.annotation.AuditTrail;
import io.github.mahorobonheur.audittrail.listener.AuditTrailEntityListener;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Test-only JPA entity used by {@link AuditTrailIntegrationTest}.
 * Mapped to the {@code test_user} table in the H2 in-memory database.
 */
@Entity
@Table(name = "test_user")
@AuditTrail(exclude = {"password"})
@EntityListeners(AuditTrailEntityListener.class)
public class TestUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    String username;
    String email;
    String password;
    String role;

    protected TestUser() { }

    public TestUser(String username, String email, String password, String role) {
        this.username = username;
        this.email    = email;
        this.password = password;
        this.role     = role;
    }
}
