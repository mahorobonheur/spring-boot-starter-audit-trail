# Changelog

All notable changes to `spring-boot-starter-audit-trail` are documented in this file.

The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [Unreleased]

### Planned
- Webhook storage backend (`audit-trail.storage=webhook`)
- Spring Boot Actuator metrics endpoint (`/actuator/audit-trail`)
- Multi-tenancy support with tenant-scoped audit queries
- GraphQL API for querying audit history

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

[Unreleased]: https://github.com/mahorobonheur/spring-boot-starter-audit-trail/compare/v1.0.0...HEAD
[1.0.0]: https://github.com/mahorobonheur/spring-boot-starter-audit-trail/releases/tag/v1.0.0
