package io.github.mahorobonheur.audittrail.integration;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for {@link TestUser}, used in integration tests.
 * Must be a top-level interface so Spring Data can create a proxy for it.
 */
@Repository
public interface TestUserRepository extends JpaRepository<TestUser, Long> {
}
