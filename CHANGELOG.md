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

## [1.2.0] — 2026-07-07

> **First stable release.** Versions `1.0.0` and `1.1.0` were early releases published before this baseline; they are deprecated and no longer supported. All adopters should use `1.2.0` or later.

### Added
- Interactive dashboard UI served at `{rest.basePath}/dashboard`; enable with `audit-trail.dashboard.enabled=true`. Features: 14-day activity line chart, entity distribution bar chart, recent activity table with field diff expansion, Explorer tab (filter by entity/ID/actor), Chain Verify tab, and Reconstruct tab. Dark navy theme, collapsible sidebar, auto-refresh every 30 s.
- `AuditTrailDashboardController` — serves the dashboard HTML from classpath and exposes backing JSON endpoints (`/dashboard/api/stats`, `/dashboard/api/entities`, `/dashboard/api/recent`, `/dashboard/api/logs`)
- `AuditLogRepository` — added `findTop50ByOrderByChangedAtDesc`, `findTopByEntityNameOrderByChangedAtDesc`, `findDistinctEntityNames`, `findDistinctActors`, and `countGroupedByEntityName` query methods used by the dashboard
- Comprehensive test suite: 70 test methods across 18 test classes covering REST controller, dashboard controller, chain service, reconstruction service, anomaly detector, actuator endpoint, `@AuditWhy` aspect, snapshot service, and async writer; JaCoCo line-coverage gate enforced at 80 % in `mvn verify`

### Changed
- README Security section reframed: auth is intentionally delegated to consuming apps; added use-case table (admin audit, user history, logging-only) and Spring Security examples
- README: corrected REST endpoint paths, added Dashboard and Database Schema sections, version support table, and upgrade guide from 1.0.x/1.1.x
- SECURITY.md: clarified that endpoint access control is a host-application responsibility

### Fixed
- `AuditWhyAspect` pointcut narrowed from `within(@Component/Service/Repository/Controller *)` to `execution(* *(.., @AuditWhy (*), ..))` — prevents accidental CGLIB proxying of `GenericFilterBean` subclasses (e.g. JWT filters) which caused `NullPointerException` on Tomcat startup in consuming applications

---

## [1.1.0] — 2026-06-14

> ⚠️ **Deprecated.** Early release — not stable. Upgrade to [1.2.0](https://github.com/mahorobonheur/mahoro-audit-trail/releases/tag/v1.2.0).

### Added
- `@AuditMask` annotation — field-level annotation that records a masked placeholder (default `"[MASKED]"`, configurable) instead of the real value; sets the `masked` flag on the `AuditLog` entry when any masked field is present
- `@AuditWhy` annotation — method-parameter annotation (AOP-driven via `AuditWhyAspect`) that automatically captures the business reason for a change into the `whyReason` field of the audit entry; `AuditWhyContext` allows programmatic fallback without AOP
- `@AuditSnapshot` annotation — marker annotation on methods that take point-in-time snapshots; programmatic API exposed via `AuditSnapshotService` with `capture()` and `captureWithReason()` methods
- `AuditChainService` — SHA-256 hash chaining across audit entries for a given entity, making the log tamper-evident; `verifyChain(List)` returns a `ChainVerificationResult` indicating whether the chain is intact and, if not, which entry broke it
- `AuditReconstructionService` — replays the stored field diffs to rebuild the full entity state at any past UTC instant (`reconstruct(entityName, entityId, Instant)`)
- `AuditAnomalyDetector` / `AuditAnomalyEvent` — in-memory sliding-window anomaly detection; fires Spring `ApplicationEvent`s for `BULK_DELETE` and `RAPID_CHANGES` within a configurable window
- `AuditTrailActuatorEndpoint` — Spring Boot Actuator endpoint (`/actuator/audit-trail`) exposing operational summary statistics and hotspot analysis
- `AsyncAuditLogWriter` — decorator that wraps any `AuditLogWriter` implementation and offloads writes to a background thread
- `AuditWriteRequest` model — encapsulates the full context of an audit write
- `snapshotLabel`, `whyReason`, `masked`, and `prevHash` columns added to the `AuditLog` entity and `audit_log` table
- REST endpoints expanded: `GET /audit-trail/{entityName}/{entityId}/diff?from=&to=`, `GET /audit-trail/{entityName}/{entityId}/verify`, `GET /audit-trail/{entityName}/{entityId}/reconstruct?at=`
- `AuditTrailPostgresIntegrationTest` — Testcontainers-based integration test suite against PostgreSQL
- CodeQL security analysis workflow, Dependabot, GitHub issue and PR templates

### Changed
- `AuditTrailProperties` extended with `chain`, `anomaly`, and `dashboard` nested configuration groups
- Spring Boot parent bumped to **4.0.6**

### Fixed
- `FieldDiffEngine` now correctly walks the full class hierarchy including abstract superclasses when collecting fields for comparison

---

## [1.0.0] — 2026-05-21

> ⚠️ **Deprecated.** Early release — not stable. Upgrade to [1.2.0](https://github.com/mahorobonheur/mahoro-audit-trail/releases/tag/v1.2.0).

### Added
- `@AuditTrail` annotation — marks a JPA entity for automatic audit tracking with optional `exclude` attribute for field-level suppression
- `@AuditExclude` annotation — field-level annotation to prevent individual fields from being recorded in the audit log
- `FieldDiffEngine` — reflection-based engine that compares old vs. new entity state and returns a typed list of `FieldDiff(field, oldValue, newValue)` records
- `AuditTrailEntityListener` — JPA EntityListener hooking into Hibernate lifecycle events to capture CREATE, UPDATE, and DELETE automatically
- `AuditSecurityResolver` — resolves the currently authenticated username from Spring Security's `SecurityContextHolder`
- `AuditLogWriter` interface — pluggable strategy for persisting audit events
- `DatabaseAuditLogWriter` and `LogAuditLogWriter` storage backends
- `AuditLog` JPA entity, `AuditAction` enum, `FieldDiff` record, `AuditLogRepository`
- `AuditTrailProperties`, `AuditTrailAutoConfiguration`, `AuditTrailController`
- `FieldDiffEngineTest`, `AuditTrailIntegrationTest`

### Technical details
- Minimum Java version: 17
- Spring Boot compatibility: 4.x
- Database: any JPA-compatible RDBMS (PostgreSQL, MySQL, H2, etc.)

---

[Unreleased]: https://github.com/mahorobonheur/mahoro-audit-trail/compare/v1.2.0...HEAD
[1.2.0]: https://github.com/mahorobonheur/mahoro-audit-trail/compare/v1.1.0...v1.2.0
[1.1.0]: https://github.com/mahorobonheur/mahoro-audit-trail/compare/v1.0.0...v1.1.0
[1.0.0]: https://github.com/mahorobonheur/mahoro-audit-trail/releases/tag/v1.0.0
