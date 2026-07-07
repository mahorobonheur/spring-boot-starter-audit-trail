# Security Policy

## Supported Versions

Only the latest stable release receives security fixes. Deprecated early releases are not maintained.

| Version | Status |
|---------|--------|
| **1.2.x** | ✅ Supported |
| 1.1.x | ⚠️ Deprecated — upgrade to 1.2.x |
| 1.0.x | ⚠️ Deprecated — upgrade to 1.2.x |

Versions `1.0.0` and `1.1.0` were early releases published before the first stable baseline (`1.2.0`). They remain on Maven Central but **must not be used** in new projects. Upgrade guidance is in [README.md](README.md#version-support).

## Reporting a Vulnerability

**Please do not open a public GitHub issue for security vulnerabilities.**

Report vulnerabilities privately so they can be assessed and patched before public disclosure.

### How to report

Send an email to **mahorobonheur123@gmail.com** with the subject line:

```
[SECURITY] mahoro-audit-trail — <short description>
```

Include as much of the following as possible:

- A description of the vulnerability and its potential impact
- Steps to reproduce or a minimal proof-of-concept
- The affected version(s)
- Any suggested mitigation or fix, if you have one

### What to expect

| Timeline | Action |
|----------|--------|
| Within **3 business days** | Acknowledgement of receipt |
| Within **14 days** | Initial assessment and severity rating |
| Within **90 days** | Fix released (sooner for critical issues) |

You will be credited in the release notes unless you prefer to remain anonymous.

### Scope

The following are in scope:

- Code execution, privilege escalation, or data exposure via the library's public API
- Sensitive data leaking through the audit log (e.g. `@AuditMask` bypass)
- Tamper-evident chain (`AuditChainService`) producing false-negative verification results
- Dependency vulnerabilities introduced by this library's transitive dependencies

The following are **out of scope**:

- Vulnerabilities in the consuming application's own code or configuration
- Issues requiring physical access to the host machine
- Denial-of-service attacks that require authenticated admin access
- Use of deprecated versions `1.0.x` or `1.1.x` (upgrade to 1.2.x first)

## Securing Audit Endpoints in Your Application

This library **deliberately does not ship authentication or authorization.** That is a design choice, not an omission: consuming applications have different needs (admin compliance consoles, per-user activity feeds, or no HTTP exposure at all), and Spring Security is the right place to express those policies.

| Surface | Property | Default |
|---------|----------|---------|
| REST API | `audit-trail.rest.enabled` | `true` |
| Dashboard | `audit-trail.dashboard.enabled` | `false` |
| Actuator | `management.endpoints.web.exposure` | Host-app controlled |

**What adopters should do:**

1. Decide how audit data is consumed (admin UI, user-facing history, repository-only, etc.).
2. Configure Spring Security `requestMatchers` for `/audit-trail/**` and `/actuator/audit-trail/**` as your threat model requires, **or** set `audit-trail.rest.enabled=false` and access data through your own APIs.
3. Use `@AuditMask` / `@AuditExclude` for fields that must never appear in the log.

Unauthenticated or misconfigured access in a deployed application is **out of scope** for library vulnerability reports — it is a host-application configuration issue. In-scope issues include defects in the library itself (e.g. `@AuditMask` bypass, chain verification false negatives).

See the [Security section in README.md](README.md#security) for use-case examples and Spring Security snippets.

## Preferred Languages

Reports in English or French are both welcome.
