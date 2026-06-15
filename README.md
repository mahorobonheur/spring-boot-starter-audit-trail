# mahoro-audit-trail

[![Build](https://github.com/mahorobonheur/spring-boot-starter-audit-trail/actions/workflows/ci.yml/badge.svg)](https://github.com/mahorobonheur/spring-boot-starter-audit-trail/actions)
[![Maven Central](https://img.shields.io/maven-central/v/io.github.mahorobonheur/mahoro-audit-trail.svg)](https://central.sonatype.com/artifact/io.github.mahorobonheur/mahoro-audit-trail)
[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](LICENSE)
[![Java](https://img.shields.io/badge/Java-17%2B-orange.svg)](https://adoptium.net/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.x%20%7C%204.x-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![Coverage](https://img.shields.io/badge/coverage-80%25%2B-green.svg)](https://github.com/mahorobonheur/spring-boot-starter-audit-trail/actions)

> Automatic, annotation-driven **field-level audit logging** for Spring Boot applications.  
> Drop `@AuditTrail` on any JPA entity and get a complete, queryable, tamper-proof change history — zero boilerplate required.

---

## Table of Contents

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
- [Configuration](#configuration)
- [REST API](#rest-api)
- [Architecture](#architecture)
- [Contributing](#contributing)
- [License](#license)

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
    <version>1.1.0</version>
</dependency>
```

**Gradle:**
```groovy
implementation 'io.github.mahorobonheur:mahoro-audit-trail:1.1.0'
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
      "fieldDiffs": "[{\"field\":\"role\",\"oldValue\":\"USER\",\"newValue\":\"ADMIN\"}]",
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

# Field-level diff between two audit entries
GET /audit-trail/diff?fromId=abc&toId=def

# Reconstruct entity state at a point in time
GET /audit-trail/reconstruct/User/42?at=2026-05-01T10:00:00Z
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

Every audit entry is linked to the previous entry for the same entity via a SHA-256 hash chain. If any entry is tampered with or deleted, verification will detect it.

```java
@Autowired AuditChainService chainService;

// Verify the complete chain for an entity
AuditChainService.ChainVerificationResult result =
    chainService.verifyChain("User", "42");

if (!result.valid()) {
    System.out.println("Chain broken at entry: " + result.brokenAtId());
}
```

**Via REST:**

```bash
GET /audit-trail/verify/User/42
```

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
| `audit-trail.rest.base-path` | `/audit-trail` | Base URL for REST endpoints |
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
  chain:
    enabled: true
  anomaly:
    enabled: true
    window-seconds: 60
    bulk-delete-threshold: 10
    rapid-change-threshold: 50
```

> **Note on entity scanning:** The starter registers its `AuditLog` entity directly with Hibernate via the `AdditionalMappingContributor` SPI. It works with any `@EntityScan` or `@EnableJpaRepositories` setup without extra configuration.

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

### `GET /{basePath}/diff?fromId={id}&toId={id}`
Returns the field-level diff between two specific audit entries.

### `GET /{basePath}/verify/{entityName}/{entityId}`
Verifies the cryptographic hash chain for an entity. Returns `{ "valid": true }` or `{ "valid": false, "brokenAtId": "..." }`.

### `GET /{basePath}/reconstruct/{entityName}/{entityId}?at={instant}`
Replays the audit history to reconstruct the entity's field values at the given UTC instant.

### `AuditLog` Response Fields

| Field | Type | Description |
|---|---|---|
| `id` | `String (UUID)` | Unique audit entry identifier |
| `entityName` | `String` | Entity simple class name |
| `entityId` | `String` | Primary key of the changed record |
| `action` | `CREATE \| UPDATE \| DELETE` | Type of change |
| `changedBy` | `String` | Username from Spring Security |
| `changedAt` | `Instant (ISO 8601)` | UTC timestamp |
| `fieldDiffs` | `String (JSON array)` | Array of `{field, oldValue, newValue}` |
| `whyReason` | `String` | Business reason (from `@AuditWhy`) |
| `masked` | `boolean` | `true` if any field was masked |
| `snapshotLabel` | `String` | Label for snapshot entries |
| `prevHash` | `String` | SHA-256 hash of the previous entry (chain) |

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
AuditTrailController    ← REST: history, diff, verify, reconstruct
```

All beans use `@ConditionalOnMissingBean` — override any component by declaring your own bean in your application context.

---

## Contributing

Contributions are warmly welcome! Please read [CONTRIBUTING.md](CONTRIBUTING.md) for guidelines on how to get started, code standards, and the pull request process.

Found a security issue? Please read [SECURITY.md](SECURITY.md) and report it privately — do not open a public issue.

---

## Author

**Bonheur Mahoro**  
[bonheur.mahoro@amalitechtraining.org](mailto:bonheur.mahoro@amalitechtraining.org)  
[github.com/mahorobonheur](https://github.com/mahorobonheur)

---

## License

Copyright 2026 Bonheur Mahoro.  
Licensed under the **Apache License, Version 2.0**. See [LICENSE](LICENSE) for full details.
