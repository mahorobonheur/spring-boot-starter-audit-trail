# mahoro-audit-trail — by Bonheur Mahoro

[![Build](https://github.com/mahorobonheur/mahoro-audit-trail/actions/workflows/build.yml/badge.svg)](https://github.com/mahorobonheur/mahoro-audit-trail/actions)
[![Maven Central](https://img.shields.io/maven-central/v/io.github.mahorobonheur/mahoro-audit-trail.svg)](https://central.sonatype.com/artifact/io.github.mahorobonheur/mahoro-audit-trail)
[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](LICENSE)
[![Java](https://img.shields.io/badge/Java-17%2B-orange.svg)](https://adoptium.net/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.x-brightgreen.svg)](https://spring.io/projects/spring-boot)

> Automatic, annotation-driven **field-level audit logging** for Spring Boot applications.  
> Drop `@AuditTrail` on any JPA entity and get a complete, queryable change history — zero boilerplate required.

---

## Table of Contents

- [Why This Library?](#why-this-library)
- [Quick Start](#quick-start)
- [Usage](#usage)
  - [@AuditTrail Annotation](#audittrail-annotation)
  - [@AuditExclude Annotation](#auditexclude-annotation)
  - [Querying Audit History](#querying-audit-history)
- [Configuration](#configuration)
- [REST API](#rest-api)
- [Architecture](#architecture)
- [How It Works](#how-it-works)
- [Roadmap](#roadmap)
- [Contributing](#contributing)
- [License](#license)

---

## Why This Library?

Every real-world application eventually needs to answer:

- *"Who changed this user's role, and when?"*
- *"What did this order look like before it was updated?"*
- *"Which admin deleted this record?"*

Existing solutions fall short:

| Solution | Problem |
|---|---|
| **Hibernate Envers** | Complex setup, full snapshots (not diffs), hard to query |
| **Spring Data `@CreatedBy`** | Tracks *who/when* only — no field-level diff, no history |
| **Javers** | Not Spring Boot native, verbose API, no auto-configuration |
| **Hand-rolled AOP** | Duplicated in every codebase, inconsistent, untestable |

`mahoro-audit-trail` fills the gap: **one annotation, clean field-level diffs, REST-queryable, Spring Boot native.**

---

## Quick Start

### 1. Add the dependency

```xml
<dependency>
    <groupId>io.github.mahorobonheur</groupId>
    <artifactId>mahoro-audit-trail</artifactId>
    <version>1.0.0</version>
</dependency>
```

### 2. Annotate your entity

```java
@Entity
@AuditTrail(exclude = {"password", "refreshToken"})
public class User {
    @Id
    private Long id;
    private String email;
    private String role;
    private String password;      // excluded — never logged
    private String refreshToken;  // excluded — never logged
}
```

### 3. That's it.

Every `save()` and `delete()` on `User` is now automatically tracked. No configuration, no additional code.

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
      "fieldDiffs": "[{\"field\":\"role\",\"oldValue\":\"USER\",\"newValue\":\"ADMIN\"}]"
    }
  ],
  "totalElements": 5,
  "totalPages": 1
}
```

---

## Usage

### `@AuditTrail` Annotation

Place `@AuditTrail` on any JPA entity class to enable audit tracking for that entity.

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

The library automatically captures `CREATE`, `UPDATE`, and `DELETE` events, recording:

- Which fields changed
- The old and new values of each changed field
- Who made the change (from Spring Security)
- When the change occurred (UTC timestamp)

#### Excluding fields via the annotation

```java
@Entity
@AuditTrail(exclude = {"password", "internalNotes", "cacheKey"})
public class User { ... }
```

### `@AuditExclude` Annotation

For field-level exclusion, annotate the field directly:

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

Both `@AuditTrail(exclude = {...})` and `@AuditExclude` can be used together.

### Querying Audit History

**Via the REST endpoint** (enabled by default):

```bash
# Full history for record id=42
GET /audit-trail/User/42

# All changes to any User record
GET /audit-trail/User

# Paginated
GET /audit-trail/User/42?page=0&size=10
```

**Via the repository** (programmatic access):

```java
@Autowired
AuditLogRepository auditLogRepository;

// Latest 20 changes to User #42
Page<AuditLog> history = auditLogRepository
    .findByEntityNameAndEntityId("User", "42", PageRequest.of(0, 20,
        Sort.by("changedAt").descending()));
```

---

## Configuration

All properties are optional. The defaults work out of the box.

| Property | Default | Description |
|---|---|---|
| `audit-trail.enabled` | `true` | Enable or disable the library globally |
| `audit-trail.storage` | `database` | Storage backend: `database` (queryable table) or `log` (structured lines on the `audit-trail` log category) |
| `audit-trail.table-name` | `audit_log` | Name of the audit log database table |
| `audit-trail.async` | `true` | Write audit logs asynchronously (non-blocking) |
| `audit-trail.rest.enabled` | `true` | Expose the REST query endpoint |
| `audit-trail.rest.base-path` | `/audit-trail` | Base URL path for REST endpoints |

### Example `application.properties`

```properties
# Disable the REST endpoint (use repository directly instead)
audit-trail.rest.enabled=false

# Use a custom table name
audit-trail.table-name=my_audit_log

# Disable async for testing or debugging
audit-trail.async=false
```

### Note on entity and repository registration

The starter registers its `AuditLog` entity directly with Hibernate (via the
`AdditionalMappingContributor` SPI) and builds its repository programmatically.
It therefore works with **any** entity-scanning setup — including applications
that declare their own `@EntityScan` or `@EnableJpaRepositories` — without any
extra configuration, and never interferes with the scanning of your own
entities and repositories.

### Example `application.yml`

```yaml
audit-trail:
  enabled: true
  table-name: audit_log
  async: true
  rest:
    enabled: true
    base-path: /audit-trail
```

---

## REST API

### `GET /{basePath}/{entityName}/{entityId}`

Returns the paginated audit history for a specific entity record.

**Parameters:**

| Parameter | Type | Required | Description |
|---|---|---|---|
| `entityName` | path | yes | Simple class name of the entity (e.g. `User`) |
| `entityId` | path | yes | The record's primary key as a string |
| `page` | query | no | Zero-based page index (default: `0`) |
| `size` | query | no | Page size (default: `20`) |

**Response:** `200 OK` — Spring `Page<AuditLog>` JSON

---

### `GET /{basePath}/{entityName}`

Returns the paginated audit history for all records of a given entity type.

**Parameters:**

| Parameter | Type | Required | Description |
|---|---|---|---|
| `entityName` | path | yes | Simple class name of the entity |
| `page` | query | no | Zero-based page index (default: `0`) |
| `size` | query | no | Page size (default: `20`) |

---

### `AuditLog` Response Fields

| Field | Type | Description |
|---|---|---|
| `id` | `String (UUID)` | Unique audit log entry identifier |
| `entityName` | `String` | Simple class name of the audited entity |
| `entityId` | `String` | Primary key of the changed record |
| `action` | `CREATE \| UPDATE \| DELETE` | Type of change |
| `changedBy` | `String` | Username from Spring Security context |
| `changedAt` | `Instant (ISO 8601)` | UTC timestamp of the change |
| `fieldDiffs` | `String (JSON array)` | Array of `{field, oldValue, newValue}` objects |

---

## Architecture

```
Your Entity (save/delete)
        │
        ▼
AuditTrailEntityListener        ← JPA @PrePersist / @PostLoad / @PreUpdate / @PreRemove
        │
        ├──► FieldDiffEngine    ← Reflection-based old vs. new comparison
        │
        ├──► AuditSecurityResolver ← Reads username from SecurityContextHolder
        │
        └──► AuditLogWriter     ← Strategy: Database (default) | Log | Webhook
                    │
                    ▼
             audit_log table    ← Queryable via AuditLogRepository or REST endpoint
```

The auto-configuration (`AuditTrailAutoConfiguration`) wires all components together automatically. Every bean uses `@ConditionalOnMissingBean` — override any component by declaring your own bean.

---

## How It Works

1. You call `repository.save(entity)` — business logic is unchanged.
2. JPA fires `@PostLoad` when the entity is first loaded, storing a shallow snapshot.
3. JPA fires `@PreUpdate` before the save. The listener retrieves the snapshot and calls `FieldDiffEngine.diff(snapshot, entity)`.
4. `FieldDiffEngine` walks all fields via Java reflection, skipping excluded ones, and returns a list of `FieldDiff(field, oldValue, newValue)` records.
5. `AuditSecurityResolver` reads the current username from Spring Security's `SecurityContextHolder`.
6. `DatabaseAuditLogWriter` serialises the diffs to JSON and persists an `AuditLog` entity asynchronously — the original `save()` is not blocked.

---

## Roadmap

| Version | Features |
|---|---|
| **v1.0** (current) | `@AuditTrail`, `@AuditExclude`, DB storage, log backend, REST endpoint, async writes |
| **v1.1** | Webhook backend, Spring Boot Actuator metrics |
| **v1.2** | Multi-tenancy support, GraphQL API |
| **v1.3** | Reactive support (Spring WebFlux / R2DBC) |

See the [full roadmap in the proposal document](https://github.com/mahorobonheur/mahoro-audit-trail/blob/main/docs/proposal.md).

---

## Contributing

Contributions are warmly welcome! Please read [CONTRIBUTING.md](CONTRIBUTING.md) for guidelines on how to get started, code standards, and the pull request process.

---

## Author

**Bonheur Mahoro**  
[mahorobonheur123@gmail.com](mailto:bonheur.mahoro@amalitechtraining.org)  
[github.com/mahorobonheur](https://github.com/mahorobonheur)

---

## License

This project is licensed under the **Apache License, Version 2.0**.  
See the [LICENSE](LICENSE) file for full details.
