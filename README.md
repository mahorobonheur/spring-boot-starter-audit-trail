# mahoro-audit-trail

[![Build](https://github.com/mahorobonheur/mahoro-audit-trail/actions/workflows/ci.yml/badge.svg)](https://github.com/mahorobonheur/mahoro-audit-trail/actions)
[![Maven Central](https://img.shields.io/maven-central/v/io.github.mahorobonheur/mahoro-audit-trail.svg)](https://central.sonatype.com/artifact/io.github.mahorobonheur/mahoro-audit-trail)
[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](LICENSE)
[![Java](https://img.shields.io/badge/Java-17%2B-orange.svg)](https://adoptium.net/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.x%20%7C%204.x-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![Coverage](https://img.shields.io/badge/coverage-80%25%2B-green.svg)](https://github.com/mahorobonheur/mahoro-audit-trail/actions)

> Automatic, annotation-driven **field-level audit logging** for Spring Boot applications.  
> Drop `@AuditTrail` on any JPA entity and get a complete, queryable, tamper-proof change history — zero boilerplate required.

---

## Table of Contents

- [Version Support](#version-support)
- [Why This Library?](#why-this-library)
- [Features](#features)
- [Quick Start](#quick-start)
- [Usage](#usage)
  - [@AuditTrail](#audittrail)
  - [@AuditExclude](#auditexclude)
  - [@AuditMask](#auditmask)
  - [@AuditWhy](#auditwhy)
  - [@AuditSnapshot](#auditsnapshot)
  - [Querying Audit History](#querying-audit-history)
  - [Chain Verification](#chain-verification)
  - [Anomaly Detection](#anomaly-detection)
  - [Actuator Endpoint](#actuator-endpoint)
  - [Dashboard](#dashboard)
- [Security](#security)
- [Configuration](#configuration)
- [REST API](#rest-api)
- [Database Schema](#database-schema)
- [Architecture](#architecture)
- [Contributing](#contributing)
- [License](#license)

---

## Version Support

| Version | Status | Notes |
|---------|--------|-------|
| **1.2.x** | ✅ **Supported** | First stable release — use this version |
| 1.1.x | ⚠️ Deprecated | Early release; upgrade to 1.2.x |
| 1.0.x | ⚠️ Deprecated | Early release; upgrade to 1.2.x |

Versions `1.0.0` and `1.1.0` remain on Maven Central but are **not maintained**. New adopters should use **`1.2.0`** or later.

### Upgrading from 1.0.x or 1.1.x

1. Bump the dependency version to `1.2.0`.
2. No database migration is required — the `audit_log` schema is unchanged from 1.1.0.
3. If you use `@AuditWhy`, upgrade is **strongly recommended**: 1.2.0 fixes an AOP pointcut that could interfere with Spring Security filter beans on startup.
4. Review the [Security](#security) section and configure Spring Security (or disable REST) for your use case.
5. Optional: enable the new dashboard with `audit-trail.dashboard.enabled=true`.

See [CHANGELOG.md](CHANGELOG.md) for the full 1.2.0 release notes.

---

## Why This Library?

Every real-world application eventually needs to answer:

- *"Who changed this user's role, and when?"*
- *"What did this order look like before it was updated?"*
- *"Which admin deleted this record?"*
- *"Why was this patient's record modified?"*

Existing solutions fall short:

| Solution | Problem |
|---|---|
| **Hibernate Envers** | Complex setup, full snapshots (not diffs), hard to query |
| **Spring Data `@CreatedBy`** | Tracks *who/when* only — no field-level diff, no history |
| **Javers** | Not Spring Boot native, verbose API, no auto-configuration |
| **Hand-rolled AOP** | Duplicated in every codebase, inconsistent, untestable |

`mahoro-audit-trail` fills the gap: **one annotation, clean field-level diffs, cryptographic chain integrity, REST-queryable, Spring Boot native.**

---

## Features

- **Zero-config automatic auditing** — annotate an entity with `@AuditTrail` and every save/delete is tracked
- **Field-level diffs** — records exactly which fields changed and the before/after values
- **Field masking** — `@AuditMask` replaces sensitive values (passwords, tokens) with a placeholder
- **Business reasons** — `@AuditWhy` captures *why* a change was made alongside *what* changed
- **Point-in-time snapshots** — `@AuditSnapshot` captures the full state of an entity at any moment
- **Cryptographic chain hashing** — SHA-256 hash chaining makes the audit log tamper-evident
- **Chain verification** — detect if any audit record was modified or deleted after the fact
- **State reconstruction** — replay the change history to rebuild an entity's state at any past instant
- **Anomaly detection** — built-in sliding-window rules detect bulk deletes and rapid changes
- **Spring Boot Actuator endpoint** — `/actuator/audit-trail` for operational summaries and hotspot analysis
- **Interactive dashboard** — optional browser UI for browsing, filtering, and verifying audit history (disabled by default)
- **Async writes** — audit entries are written asynchronously; your business transaction is never blocked
- **Pluggable storage** — database (default) or structured log output; bring your own writer via `AuditLogWriter`
- **Spring Security integration** — `changedBy` is automatically resolved from `SecurityContextHolder`
- **Override-friendly** — every auto-configured bean uses `@ConditionalOnMissingBean`

---

## Quick Start

### 1. Add the dependency

**Maven:**
```xml
<dependency>
    <groupId>io.github.mahorobonheur</groupId>
    <artifactId>mahoro-audit-trail</artifactId>
    <version>1.2.0</version>
</dependency>
```

**Gradle:**
```groovy
implementation 'io.github.mahorobonheur:mahoro-audit-trail:1.2.0'
```

### 2. Annotate your entity

```java
@Entity
@AuditTrail(exclude = {"password", "refreshToken"})
public class User {
    @Id private Long id;
    private String email;
    private String role;
    private String password;      // excluded — never logged
    private String refreshToken;  // excluded — never logged
}
```

### 3. That's it.

Every `save()` and `delete()` on `User` is now automatically tracked.

```
GET /audit-trail/User/42
```

```json
{
  "content": [
    {
      "id": "3f2a1b...",
      "entityName": "User",
      "entityId": "42",
      "action": "UPDATE",
      "changedBy": "admin@company.com",
      "changedAt": "2026-05-21T10:34:00Z",
      "fieldDiffs": [
        {"field": "role", "oldValue": "USER", "newValue": "ADMIN"}
      ],
      "whyReason": "Promoted to admin after completing training",
      "masked": false
    }
  ],
  "totalElements": 5,
  "totalPages": 1
}
```

---

## Usage

### `@AuditTrail`

Place `@AuditTrail` on any JPA entity to enable audit tracking. Captures `CREATE`, `UPDATE`, and `DELETE` events.

```java
@Entity
@AuditTrail
public class Product {
    @Id private Long id;
    private String name;
    private BigDecimal price;
    private int stock;
}
```

Exclude fields from auditing using the `exclude` attribute:

```java
@Entity
@AuditTrail(exclude = {"internalNotes", "cacheKey"})
public class Order { ... }
```

By default the library audits all entities annotated with `@AuditTrail`. To audit every JPA entity without annotating each one, set:

```properties
audit-trail.mode=ALL
audit-trail.exclude-entities=AuditLog,SomeInternalEntity
```

---

### `@AuditExclude`

Exclude a specific field from auditing at the field level:

```java
@Entity
@AuditTrail
public class User {
    private String email;

    @AuditExclude
    private String password;       // never recorded

    @AuditExclude
    private String refreshToken;   // never recorded
}
```

---

### `@AuditMask`

Fields annotated with `@AuditMask` are included in the audit diff but their actual values are replaced with a placeholder. The audit log records *that* the field changed, but not *what* the values were.

```java
@Entity
@AuditTrail
public class User {
    private String email;

    @AuditMask
    private String password;   // recorded as "[MASKED]" → "[MASKED]"

    @AuditMask(placeholder = "***")
    private String ssn;        // recorded as "***" → "***"
}
```

When any masked field is present, the `masked` flag on the `AuditLog` entry is set to `true`.

---

### `@AuditWhy`

Annotate a `String` parameter in any Spring-managed bean method to automatically capture the business reason for a change. The reason is stored in the `whyReason` field of the audit entry.

```java
@Service
public class UserService {

    public void promoteToAdmin(User user, @AuditWhy String reason) {
        user.setRole("ADMIN");
        userRepository.save(user);
        // The audit entry for this save will include: whyReason = reason
    }
}
```

You can also set the reason programmatically:

```java
AuditWhyContext.set("Regulatory compliance review");
try {
    userRepository.save(user);
} finally {
    AuditWhyContext.clear();
}
```

> **Note:** `@AuditWhy` requires the `spring-aspects` dependency and AspectJ weaving. The aspect is automatically registered when `spring-aspects` is on the classpath.

---

### `@AuditSnapshot`

Use `@AuditSnapshot` as a documentation marker on methods that capture point-in-time entity state. Programmatic snapshots are taken via `AuditSnapshotService`:

```java
@Autowired AuditSnapshotService snapshotService;

// Capture full state of an entity (useful before a bulk operation)
snapshotService.capture(user, "pre-migration-snapshot");

// Capture with a business reason
snapshotService.captureWithReason(user, "pre-deletion", "User requested account deletion");
```

---

### Querying Audit History

**Via REST:**

```bash
# Full history for a specific record
GET /audit-trail/User/42

# All changes to any User record
GET /audit-trail/User

# Paginated
GET /audit-trail/User/42?page=0&size=10

# Field-level diff between two audit entries (by entry id)
GET /audit-trail/User/42/diff?from=abc&to=def

# Verify tamper-evident hash chain for a record
GET /audit-trail/User/42/verify

# Reconstruct entity state at a point in time
GET /audit-trail/User/42/reconstruct?at=2026-05-01T10:00:00Z
```

**Via repository:**

```java
@Autowired AuditLogRepository auditLogRepository;

Page<AuditLog> history = auditLogRepository
    .findByEntityNameAndEntityId("User", "42",
        PageRequest.of(0, 20, Sort.by("changedAt").descending()));
```

---

### Chain Verification

Every audit entry is linked to the previous entry for the same entity via a SHA-256 hash chain when `audit-trail.chain.enabled=true`. If any entry is tampered with or deleted, verification will detect it.

```java
@Autowired AuditChainService chainService;
@Autowired AuditLogRepository auditLogRepository;

List<AuditLog> entries = auditLogRepository
    .findByEntityNameAndEntityIdOrderByChangedAtAscIdAsc("User", "42");

AuditChainService.ChainVerificationResult result = chainService.verifyChain(entries);

if (!result.valid()) {
    System.out.println("Chain broken at entry: " + result.brokenAtId());
}
```

**Via REST:**

```bash
GET /audit-trail/User/42/verify
```

Returns `501 Not Implemented` when chain hashing is disabled.

---

### Anomaly Detection

Enable automatic detection of suspicious audit patterns:

```properties
audit-trail.anomaly.enabled=true
audit-trail.anomaly.bulk-delete-threshold=10   # alert if >10 deletes in the window
audit-trail.anomaly.rapid-change-threshold=50  # alert if one actor makes >50 changes
audit-trail.anomaly.window-seconds=60
```

Listen for anomaly events in your application:

```java
@Component
public class AuditAnomalyListener {

    @EventListener
    public void onAnomaly(AuditAnomalyEvent event) {
        log.warn("Audit anomaly [{}]: {} — triggered by {}",
            event.getRule(), event.getDescription(), event.getTriggeredBy());
        // send alert, trigger review workflow, etc.
    }
}
```

---

### Actuator Endpoint

When `spring-boot-starter-actuator` is on the classpath, an `audit-trail` actuator endpoint is available:

```bash
# Summary statistics
GET /actuator/audit-trail/summary

# Top entities by change frequency
GET /actuator/audit-trail/hotspots
```

Expose it in your `application.properties`:

```properties
management.endpoints.web.exposure.include=health,info,audit-trail
```

> **Security:** Configure access in your application's Spring Security setup — see [Security](#security). The library does not enforce auth; your team chooses admin-only, per-user, or no HTTP exposure.

---

### Dashboard

An optional single-page dashboard UI for browsing audit history without writing custom queries. **Disabled by default.**

Enable it:

```properties
audit-trail.dashboard.enabled=true
```

Open in a browser (default base path):

```
http://localhost:8080/audit-trail/dashboard
```

**Features:**

- Overview — 14-day activity chart, entity distribution, recent changes with expandable field diffs
- Explorer — filter and paginate logs by entity, ID, actor, action, and date range
- Chain Verify — verify hash-chain integrity for a specific record
- Reconstruct — replay history to view field values at a past instant
- Auto-refresh every 30 seconds

The dashboard reuses the REST base path (`audit-trail.rest.base-path`) for its JSON APIs:

| Endpoint | Description |
|---|---|
| `GET /{basePath}/dashboard` | Serves the HTML UI |
| `GET /{basePath}/dashboard/api/stats` | Aggregate counters and 14-day activity histogram |
| `GET /{basePath}/dashboard/api/entities` | Per-entity counts and last-changed timestamps |
| `GET /{basePath}/dashboard/api/recent` | 50 most recent audit entries |
| `GET /{basePath}/dashboard/api/logs` | Filtered, paginated log query (`entity`, `entityId`, `actor`, `action`, `from`, `to`, `page`, `size`) |

> **Security:** Enable only when appropriate for your use case; protect with Spring Security or keep admin-internal — see [Security](#security).

> **Custom table names:** Dashboard aggregate queries use native SQL against the default `audit_log` table. If you set `audit-trail.table-name` to a custom value, override the native-query methods in a local `AuditLogRepository` extension.

---

## Security

`mahoro-audit-trail` **does not implement authentication or authorization by design.** As a Spring Boot starter, it focuses on capturing and querying audit data; **your application decides who can see it** using Spring Security, property flags, or by consuming the data only through your own service layer.

This is intentional. Different teams use the same library in different ways:

| Use case | Typical setup |
|---|---|
| **Compliance / admin audit** | REST or dashboard enabled; Spring Security restricts `/audit-trail/**` to `AUDIT_ADMIN` (or similar) |
| **End-user activity history** | REST enabled; your app exposes only *that user's* records via a custom controller that calls `AuditLogRepository`, or you secure `/audit-trail/{entity}/{id}` so users can only reach their own ID |
| **Internal logging only** | `audit-trail.rest.enabled=false`; audit rows written to DB/log but never exposed over HTTP |
| **Operations monitoring** | Actuator `audit-trail` endpoint exposed only on the management port, behind auth |

The library gives you the building blocks. **Access control is always a consuming-application concern.**

### HTTP surfaces (configure as needed)

| Surface | Default | Your decision |
|---|---|---|
| REST API (`audit-trail.rest.*`) | enabled | Disable, leave open in dev, or lock down with Spring Security |
| Dashboard (`audit-trail.dashboard.*`) | disabled | Enable for admin UIs; protect with Spring Security |
| Actuator (`/actuator/audit-trail`) | on classpath when Actuator present | Expose only if needed; control via `management.endpoints.web.exposure` |

### Spring Security examples

**Admin-only audit access:**

```java
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.authorizeHttpRequests(auth -> auth
            .requestMatchers("/audit-trail/**").hasRole("AUDIT_ADMIN")
            .requestMatchers("/actuator/audit-trail/**").hasRole("AUDIT_ADMIN")
            .anyRequest().authenticated()
        );
        return http.build();
    }
}
```

**End-user can view their own history** (illustrative — adapt paths and ownership checks to your domain):

```java
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.authorizeHttpRequests(auth -> auth
            // Only authenticated users; fine-grained row access is enforced in your controller
            .requestMatchers("/audit-trail/User/**").authenticated()
            .anyRequest().authenticated()
        );
        return http.build();
    }
}
```

For stricter per-user isolation, prefer querying `AuditLogRepository` inside **your own** REST layer where you can enforce `entityId == currentUser.getId()` rather than exposing the generic library endpoints publicly.

### When you do not need HTTP access

```properties
audit-trail.rest.enabled=false
audit-trail.dashboard.enabled=false
```

Audit events are still recorded. You read history programmatically via `AuditLogRepository` or your own APIs.

### Data-handling guidance

- Use `@AuditMask` or `@AuditExclude` for passwords, tokens, and secrets.
- Treat audit rows as sensitive — they may contain PII and business reasons.
- Use HTTPS wherever audit endpoints are reachable.
- For tamper-evidence requirements, enable `audit-trail.chain.enabled=true`.

Report library vulnerabilities privately — see [SECURITY.md](SECURITY.md).

---

## Configuration

All properties are optional. The defaults work out of the box.

| Property | Default | Description |
|---|---|---|
| `audit-trail.enabled` | `true` | Enable or disable the library globally |
| `audit-trail.mode` | `ANNOTATED` | `ANNOTATED` — only `@AuditTrail` entities; `ALL` — every JPA entity |
| `audit-trail.exclude-entities` | `[]` | Entity class names to exclude when `mode=ALL` |
| `audit-trail.storage` | `database` | Storage backend: `database` or `log` |
| `audit-trail.table-name` | `audit_log` | Database table name |
| `audit-trail.async` | `true` | Write audit entries asynchronously |
| `audit-trail.rest.enabled` | `true` | Expose the REST query endpoints |
| `audit-trail.rest.base-path` | `/audit-trail` | Base URL for REST and dashboard endpoints |
| `audit-trail.dashboard.enabled` | `false` | Serve the interactive dashboard UI at `{base-path}/dashboard` |
| `audit-trail.chain.enabled` | `false` | Enable SHA-256 hash chaining |
| `audit-trail.anomaly.enabled` | `false` | Enable anomaly detection |
| `audit-trail.anomaly.window-seconds` | `60` | Sliding window size in seconds |
| `audit-trail.anomaly.bulk-delete-threshold` | `10` | DELETE count threshold per entity type |
| `audit-trail.anomaly.rapid-change-threshold` | `50` | Change count threshold per actor |

### Example `application.yml`

```yaml
audit-trail:
  enabled: true
  mode: ANNOTATED
  storage: database
  async: true
  rest:
    enabled: true
    base-path: /audit-trail
  dashboard:
    enabled: false   # set true only in trusted/admin environments
  chain:
    enabled: true
  anomaly:
    enabled: true
    window-seconds: 60
    bulk-delete-threshold: 10
    rapid-change-threshold: 50
```

> **Note on entity scanning:** The starter registers its `AuditLog` entity directly with Hibernate via the `AdditionalMappingContributor` SPI. It works with any `@EntityScan` or `@EnableJpaRepositories` setup without extra configuration.

> **Production tip:** HTTP access is configurable (`audit-trail.rest.enabled`, `audit-trail.dashboard.enabled`). Use Spring Security or disable endpoints according to your use case — see [Security](#security).

---

## REST API

### `GET /{basePath}/{entityName}/{entityId}`
Returns paginated audit history for a specific record.

| Parameter | Type | Required | Description |
|---|---|---|---|
| `entityName` | path | yes | Entity simple class name (e.g. `User`) |
| `entityId` | path | yes | Primary key as string |
| `page` | query | no | Zero-based page index (default: `0`) |
| `size` | query | no | Page size (default: `20`) |

### `GET /{basePath}/{entityName}`
Returns paginated audit history for all records of an entity type.

### `GET /{basePath}/{entityName}/{entityId}/diff?from={id}&to={id}`
Compares two audit log entries side by side. Both `from` and `to` are audit entry IDs (UUIDs).

| Parameter | Type | Required | Description |
|---|---|---|---|
| `entityName` | path | yes | Entity simple class name |
| `entityId` | path | yes | Primary key of the audited record |
| `from` | query | yes | ID of the earlier audit entry |
| `to` | query | yes | ID of the later audit entry |

### `GET /{basePath}/{entityName}/{entityId}/verify`
Verifies the cryptographic hash chain for an entity. Returns `{ "valid": true }` or `{ "valid": false, "brokenAtId": "..." }`. Returns `501` when `audit-trail.chain.enabled=false`.

### `GET /{basePath}/{entityName}/{entityId}/reconstruct?at={instant}`
Replays the audit history to reconstruct the entity's field values at the given UTC instant (ISO-8601, e.g. `2026-05-01T10:00:00Z`).

### `AuditLog` Response Fields

| Field | Type | Description |
|---|---|---|
| `id` | `String (UUID)` | Unique audit entry identifier |
| `entityName` | `String` | Entity simple class name |
| `entityId` | `String` | Primary key of the changed record |
| `action` | `CREATE \| UPDATE \| DELETE` | Type of change |
| `changedBy` | `String` | Username from Spring Security |
| `changedAt` | `Instant (ISO 8601)` | UTC timestamp |
| `fieldDiffs` | `Array` | JSON array of `{field, oldValue, newValue}` objects |
| `whyReason` | `String` | Business reason (from `@AuditWhy`) |
| `masked` | `boolean` | `true` if any field was masked |
| `snapshotLabel` | `String` | Label for snapshot entries |
| `prevHash` | `String` | SHA-256 hash of the previous entry (chain) |

---

## Database Schema

When `audit-trail.storage=database`, the library creates an `audit_log` table (name configurable via `audit-trail.table-name`). Hibernate creates it automatically when `spring.jpa.hibernate.ddl-auto` is `create` or `update`. For production, use `validate` or `none` and manage the schema with your migration tool.

**Reference DDL (PostgreSQL):**

```sql
CREATE TABLE audit_log (
    id              VARCHAR(36)  PRIMARY KEY,
    entity_name     VARCHAR(255) NOT NULL,
    entity_id       VARCHAR(255) NOT NULL,
    action          VARCHAR(10)  NOT NULL,
    changed_by      VARCHAR(255) NOT NULL,
    changed_at      TIMESTAMPTZ  NOT NULL,
    field_diffs     TEXT,
    why_reason      TEXT,
    is_masked       BOOLEAN      NOT NULL DEFAULT FALSE,
    snapshot_label  VARCHAR(255),
    prev_hash       VARCHAR(64)
);

CREATE INDEX idx_audit_log_entity ON audit_log (entity_name, entity_id);
CREATE INDEX idx_audit_log_changed_at ON audit_log (changed_at);
```

Column `prev_hash` is populated only when `audit-trail.chain.enabled=true`.

---

## Architecture

```
Your Entity (save/delete)
        │
        ▼
AuditTrailEntityListener        ← Hibernate SPI (PRE_UPDATE, POST_INSERT, PRE_DELETE)
        │
        ├──► FieldDiffEngine    ← Reflection diff; respects @AuditExclude, @AuditMask
        │
        ├──► AuditWhyContext    ← ThreadLocal; populated by @AuditWhy via AuditWhyAspect
        │
        ├──► AuditSecurityResolver ← Reads username from SecurityContextHolder
        │
        └──► AuditLogWriter     ← Strategy: DatabaseAuditLogWriter | LogAuditLogWriter
                    │
                    ├──► AuditChainService     ← SHA-256 hash chaining (optional)
                    │
                    └──► AuditAnomalyDetector  ← Sliding-window anomaly rules (optional)
                                │
                                └──► ApplicationEventPublisher → AuditAnomalyEvent

AuditSnapshotService    ← Programmatic full-state capture
AuditReconstructionService ← Replay diffs to rebuild state at any past instant
AuditTrailActuatorEndpoint ← Spring Boot Actuator (summary, hotspots)
AuditTrailController       ← REST: history, diff, verify, reconstruct
AuditTrailDashboardController ← Dashboard UI + JSON backing APIs (optional)
```

All beans use `@ConditionalOnMissingBean` — override any component by declaring your own bean in your application context.

---

## Contributing

Contributions are warmly welcome! Please read [CONTRIBUTING.md](CONTRIBUTING.md) for guidelines on how to get started, code standards, and the pull request process.

Found a security issue? Please read [SECURITY.md](SECURITY.md) and report it privately — do not open a public issue.

---

## Author

**Bonheur Mahoro**  
[mahorobonheur123@gmail.com](mailto:mahorobonheur123@gmail.com)  
[github.com/mahorobonheur](https://github.com/mahorobonheur)

---

## License

Copyright 2026 Bonheur Mahoro.  
Licensed under the **Apache License, Version 2.0**. See [LICENSE](LICENSE) for full details.
