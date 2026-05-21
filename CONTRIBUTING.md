# Contributing to spring-boot-starter-audit-trail

Thank you for taking the time to contribute! Every improvement — whether a bug fix, new feature, or documentation update — makes this library better for everyone. This guide explains how to get involved.

---

## Table of Contents

- [Code of Conduct](#code-of-conduct)
- [Getting Started](#getting-started)
- [Development Setup](#development-setup)
- [Project Structure](#project-structure)
- [How to Contribute](#how-to-contribute)
  - [Reporting Bugs](#reporting-bugs)
  - [Suggesting Features](#suggesting-features)
  - [Submitting a Pull Request](#submitting-a-pull-request)
- [Code Standards](#code-standards)
- [Commit Message Convention](#commit-message-convention)
- [Testing Guidelines](#testing-guidelines)
- [Review Process](#review-process)

---

## Code of Conduct

This project follows the [Contributor Covenant Code of Conduct](CODE_OF_CONDUCT.md). By participating, you agree to uphold a welcoming and respectful environment for all contributors.

---

## Getting Started

1. **Fork** the repository on GitHub.
2. **Clone** your fork locally:
   ```bash
   git clone https://github.com/YOUR_USERNAME/spring-boot-starter-audit-trail.git
   cd spring-boot-starter-audit-trail
   ```
3. **Add the upstream remote** so you can stay in sync:
   ```bash
   git remote add upstream https://github.com/mahorobonheur/spring-boot-starter-audit-trail.git
   ```
4. **Create a feature branch** from `main`:
   ```bash
   git checkout -b feat/my-feature
   ```

---

## Development Setup

**Prerequisites:**

- Java 17 or higher
- Apache Maven 3.8+
- Git

**Build and test:**

```bash
# Compile the project
mvn compile

# Run all tests
mvn test

# Full build including sources and Javadoc JARs
mvn clean package

# Run with coverage report
mvn clean verify
# Coverage report: target/site/jacoco/index.html
```

---

## Project Structure

```
src/
├── main/java/io/github/mahorobonheur/audittrail/
│   ├── annotation/        @AuditTrail, @AuditExclude
│   ├── config/            Auto-configuration and properties
│   ├── controller/        REST endpoint
│   ├── engine/            FieldDiffEngine — core diff logic
│   ├── listener/          JPA EntityListener
│   ├── model/             AuditLog entity, AuditAction enum, FieldDiff record
│   ├── repository/        Spring Data JPA repository
│   ├── security/          AuditSecurityResolver
│   └── writer/            AuditLogWriter interface + DatabaseAuditLogWriter
├── main/resources/
│   └── META-INF/spring/   AutoConfiguration.imports (starter registration)
└── test/java/
    ├── engine/            FieldDiffEngineTest (unit tests)
    └── integration/       AuditTrailIntegrationTest (full flow tests)
```

---

## How to Contribute

### Reporting Bugs

Before opening a new issue, please search [existing issues](https://github.com/mahorobonheur/spring-boot-starter-audit-trail/issues) to avoid duplicates.

When filing a bug report, please include:

- A clear, descriptive title
- Steps to reproduce the problem
- Expected behaviour vs. actual behaviour
- Your Java version, Spring Boot version, and library version
- A minimal code sample or repository link if possible

### Suggesting Features

Open a [GitHub Discussion](https://github.com/mahorobonheur/spring-boot-starter-audit-trail/discussions) or a GitHub Issue labelled `enhancement`. Describe:

- The problem you are trying to solve
- Your proposed solution and why it fits the library's design
- Any alternative approaches you considered

### Submitting a Pull Request

1. Ensure your branch is up to date with `upstream/main`:
   ```bash
   git fetch upstream
   git rebase upstream/main
   ```
2. Make your changes and write tests covering the new behaviour.
3. Run the full test suite and confirm it passes:
   ```bash
   mvn clean verify
   ```
4. Push your branch and open a Pull Request against `main`.
5. Fill in the PR template — describe what changed and why.
6. Link any related issues using `Closes #123`.

---

## Code Standards

- **Java version:** Use Java 17+ features where appropriate — records, sealed classes, text blocks, `var` are all encouraged.
- **Style:** Follow the [Google Java Style Guide](https://google.github.io/styleguide/javaguide.html).
- **Formatting:** 4-space indentation, no tabs, UTF-8 encoding.
- **Javadoc:** All `public` and `protected` APIs must have complete Javadoc, including `@param`, `@return`, and `@throws` where relevant.
- **No wildcard imports:** Always use explicit imports.
- **Null safety:** Prefer `Optional` or defensive null checks over returning raw `null` from public APIs.
- **Thread safety:** Any shared state must be documented and properly synchronised.

---

## Commit Message Convention

This project uses [Conventional Commits](https://www.conventionalcommits.org/):

```
<type>(<scope>): <short summary>

[optional body]

[optional footer: Closes #123]
```

**Types:**

| Type | When to use |
|---|---|
| `feat` | A new feature |
| `fix` | A bug fix |
| `docs` | Documentation only changes |
| `refactor` | Code restructuring without behaviour change |
| `test` | Adding or improving tests |
| `chore` | Build process, dependency updates, tooling |
| `perf` | Performance improvements |

**Examples:**

```
feat(engine): support nested object field comparison
fix(listener): handle entities without default constructor
docs(readme): add configuration reference table
test(integration): add DELETE event integration test
```

---

## Testing Guidelines

- **Unit tests** go in `src/test/java/.../engine/` or alongside the class being tested.
- **Integration tests** go in `src/test/java/.../integration/` and use Spring Boot Test with H2.
- **Coverage:** Test coverage must remain above **80%** (enforced by JaCoCo in CI).
- **Test naming:** Use `@DisplayName` with a plain English description of the scenario.
- **Assertions:** Use [AssertJ](https://assertj.github.io/doc/) — it produces clearer failure messages than JUnit assertions.
- **Async behaviour:** Use [Awaitility](http://www.awaitility.org/) for any tests involving async audit writes.

---

## Review Process

- All pull requests require at least **one approving review** from a maintainer.
- The CI pipeline must pass (build + tests + coverage check).
- Reviewers may request changes; please respond or update the PR within a reasonable time.
- Once approved, the maintainer will merge using **squash and merge** to keep the commit history clean.

---

Thank you for contributing — your effort is what makes open source great.  
**— Bonheur Mahoro** ([@mahorobonheur](https://github.com/mahorobonheur))
