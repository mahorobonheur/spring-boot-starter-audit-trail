# Changelog

All notable changes to **mahoro-audit-trail** by Bonheur Mahoro are documented in this file.

The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [Unreleased]

### Planned
- Webhook storage backend (`audit-trail.storage=webhook`)
- Multi-tenancy support with tenant-scoped audit queries
- GraphQL API for querying audit history

---

## [1.1.0] — 2026-06-14

### Added
- `@AuditMask` annotation — field-level annotation that records a masked placeholder (default `"[MASKED]"`, configurable) instead of the real value; sets the `masked` flag on the `AuditLog` entry when any masked field is present
- `@AuditWhy` annotation — method-parameter annotation (AOP-driven via `AuditWhyAspect`) that automatically captures the business reason for a change into the `whyReason` field of the audit entry; `AuditWhyContext` allows programmatic fallback without AOP
- `@AuditSnapshot` annotation — marker annotation on methods that take point-in-time snapshots; programmatic API exposed via `AuditSnapshotService` with `capture()` and `captureWithReason()` methods
- `AuditChainService` — SHA-256 hash chaining across audit entries for a given entity, making the log tamper-evident; `verifyChain(entityName, entityId)` returns a `ChainVerificationResult` indicating whether the chain is intact and, if not, which entry broke it
- `AuditReconstructionService` — replays the stored field diffs to rebuild the full entity state at any past UTC instant (`reconstruct(entityName, entityId, Instant)`)
- `AuditAnomalyDetector` / `AuditAnomalyEvent` — in-memory sliding-window anomaly detection; fires Spring `ApplicationEvent`s for `BULK_DELETE` (configurable threshold of deletes per entity type) and `RAPID_CHANGES` (configurable threshold of changes per actor) within a configurable window
- `AuditTrailActuatorEndpoint` — Spring Boot Actuator endpoint (`/actuator/audit-trail`) exposing operational summary statistics and hotspot analysis; registered only when `spring-boot-starter-actuator` is on the classpath
- `AsyncAuditLogWriter` — decorator that wraps any `AuditLogWriter` implementation and offloads writes to a background thread, keeping the originating transaction non-blocking
- `AuditWriteRequest` model — encapsulates the full context of an audit write (entity name, entity ID, action, diffs, actor, reason, snapshot label) passed between components
- `snapshotLabel`, `whyReason`, `masked`, and `prevHash` columns added to the `AuditLog` entity and `audit_log` table
- REST endpoints expanded: `GET /audit-trail/diff?fromId=&toId=`, `GET /audit-trail/verify/{entityName}/{entityId}`, `GET /audit-trail/reconstruct/{entityName}/{entityId}?at=`
- `AuditTrailPostgresIntegrationTest` — Testcontainers-based integration test suite running against a real PostgreSQL instance, covering the full audit lifecycle including chain verification and anomaly detection
- `AuditSecurityResolverTest`, `DatabaseAuditLogWriterTest`, `LogAuditLogWriterTest` — focused unit test classes raising overall test count to 32 test methods across 7 test files
- `application-testcontainers.properties` — Testcontainers profile configuration for PostgreSQL integration tests
- CodeQL security analysis workflow (`codeql.yml`) — weekly scheduled scan plus scan on every push/PR to `main` using the `security-extended` and `security-and-quality` query suites
- Dependabot configuration (`dependabot.yml`) for automated Maven and GitHub Actions dependency updates
- GitHub issue templates (`bug_report.yml`, `feature_request.yml`) and pull request template

### Changed
- `AuditTrailProperties` extended with `chain`, `anomaly`, and `dashboard` nested configuration groups; all new properties are optional with safe defaults
- `AuditTrailAutoConfiguration` updated to conditionally register `AuditChainService`, `AuditAnomalyDetector`, `AuditTrailActuatorEndpoint`, and `AuditWhyAspect` based on classpath and property conditions
- Spring Boot parent bumped to **4.0.6**; README updated to reflect `3.x | 4.x` compatibility

### Fixed
- `FieldDiffEngine` now correctly walks the full class hierarchy including abstract superclasses when collecting fields for comparison

---

## [1.0.0] — 2026-05-21

### Added
- `@AuditTrail` annotation — marks a JPA entity for automatic audit tracking with optional `exclude` attribute for field-level suppression
- `@AuditExclude` annotation — field-level annotation to prevent individual fields from being recorded in the audit log
- `FieldDiffEngine` — reflection-based engine that compares old vs. new entity state and returns a typed list of `FieldDiff(field, oldValue, newValue)` records; respects both exclusion mechanisms; walks full class hierarchy including superclasses
- `AuditTrailEntityListener` — JPA EntityListener hooking into `@PrePersist`, `@PostLoad`, `@PreUpdate`, and `@PreRemove` to capture CREATE, UPDATE, and DELETE events automatically
- `AuditSecurityResolver` — resolves the currently authenticated username from Spring Security's `SecurityContextHolder`; falls back to `"anonymous"` when no authentication is present
- `AuditLogWriter` interface — pluggable strategy for persisting audit events; enables custom backends
- `DatabaseAuditLogWriter` — default implementation that serialises field diffs to JSON and persists `AuditLog` entries asynchronously via `@Async`
- `LogAuditLogWriter` — alternative backend (`audit-trail.storage=log`) that writes structured audit lines to the dedicated `audit-trail` SLF4J log category
- `AuditLog` JPA entity — maps to the `audit_log` table with fields: `id`, `entityName`, `entityId`, `action`, `changedBy`, `changedAt`, `fieldDiffs`
- `AuditAction` enum — `CREATE`, `UPDATE`, `DELETE`
- `FieldDiff` Java record — `(field, oldValue, newValue)` with a human-readable `toString()`
- `AuditLogRepository` — Spring Data JPA repository with `findByEntityNameAndEntityId` and `findByEntityName` paginated query methods
- `AuditTrailProperties` — `@ConfigurationProperties(prefix = "audit-trail")` binding for all configuration keys
- `AuditTrailAutoConfiguration` — Spring Boot auto-configuration with `@ConditionalOnMissingBean` guards on all beans, allowing full customisation
- `AuditTrailController` — REST controller exposing `GET /audit-trail/{entityName}/{entityId}` and `GET /audit-trail/{entityName}` with pagination support
- `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` — starter self-registration file
- `FieldDiffEngineTest` — 8 unit tests covering no-change, single-field change, multiple changes, CREATE/DELETE null entity handling, exclusion via annotation attribute, exclusion via `@AuditExclude`, both-null guard, and `FieldDiff.toString()`
- `AuditTrailIntegrationTest` — integration tests for CREATE, UPDATE, and DELETE flows using H2 in-memory database and Spring Boot Test
- `application-test.properties` — H2 test configuration with `audit-trail.async=false` for deterministic test execution

### Technical details
- Minimum Java version: 17
- Spring Boot compatibility: 4.x
- Database: any JPA-compatible RDBMS (PostgreSQL, MySQL, H2, etc.)
- Audit log writes are non-blocking by default (`@Async`)
- All auto-configured beans use `@ConditionalOnMissingBean` — every component can be overridden

---

[Unreleased]: https://github.com/mahorobonheur/mahoro-audit-trail/compare/v1.1.0...HEAD
[1.1.0]: https://github.com/mahorobonheur/mahoro-audit-trail/compare/v1.0.0...v1.1.0
[1.0.0]: https://github.com/mahorobonheur/mahoro-audit-trail/releases/tag/v1.0.0
