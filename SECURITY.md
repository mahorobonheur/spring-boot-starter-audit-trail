# Security Policy

## Supported Versions

Only the latest release receives security fixes. Once a new version is published, the previous one is no longer supported.

| Version | Supported |
|---------|-----------|
| 1.1.x   | ✅ Yes     |
| 1.0.x   | ❌ No      |

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

## Preferred Languages

Reports in English or French are both welcome.
