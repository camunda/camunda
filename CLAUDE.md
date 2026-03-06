# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Camunda 8 is a distributed process automation platform built on BPMN. This monorepo contains all core components: Zeebe (the process engine), Operate, Tasklist, Identity, Optimize, and shared infrastructure. Java 21 (language level 21 for most modules, level 8 for some client/protocol modules).

## Build Commands

```bash
# Quick build (skips tests, checks, Optimize)
./mvnw clean install -Dquickly -T1C

# Full build without tests
./mvnw clean install -DskipChecks -DskipTests -T1C

# Full build with tests
./mvnw clean install -T1C

# Lint/format check
./mvnw license:check spotless:check -T1C

# Auto-format
./mvnw license:format spotless:apply -T1C

# Run unit tests only
./mvnw verify -Dquickly -DskipTests=false -DskipITs -T1C

# Run integration tests only
./mvnw verify -Dquickly -DskipTests=false -DskipUTs -T1C

# Run a single module's tests
./mvnw verify -pl <module-path> -DskipChecks

# Build profiling
./mvnw clean install -Dquickly -Dprofile

# Build Docker image
./mvnw install -DskipChecks -DskipTests -T1C && docker build -f camunda.Dockerfile --target app -t "camunda/camunda:current-test" --build-arg DISTBALL=dist/target/camunda-zeebe-*.tar.gz .
```

### Load Tester (load-tests/load-tester)

```bash
# Build load-tester module
./mvnw -am -pl load-tests/load-tester package -DskipTests -DskipChecks

# Build Docker images via Jib
./mvnw -pl load-tests/load-tester jib:build -Pstarter
./mvnw -pl load-tests/load-tester jib:build -Pworker
```

## Key Modules

| Path | Purpose |
|------|---------|
| `zeebe/` | Process automation engine (gRPC + REST APIs) |
| `operate/` | Process monitoring and troubleshooting UI |
| `tasklist/` | User task management UI and API |
| `identity/` | Authentication and authorization (being renamed to `admin`) |
| `clients/` | Client libraries including `camunda-spring-boot-starter` |
| `parent/` | Parent POM (`zeebe-parent`) managing all dependency versions |
| `bom/` | Bill of materials for dependency consumers |
| `dist/` | Distribution packaging |
| `load-tests/` | Load/performance testing tools and setup |
| `qa/` | Quality assurance and acceptance tests |
| `search/` | Search clients for Camunda 8 data |
| `testing/` | Testing libraries for process applications |

## Architecture Notes

- **Parent POM** (`parent/pom.xml`): Manages Spring Boot 4.0.3, Spring Framework 7.0.5, and all dependency versions. Module POMs inherit from `zeebe-parent`.
- **Camunda Spring Boot Starter** (`clients/camunda-spring-boot-starter`): Provides auto-configured `CamundaClient`, `@JobWorker` annotation support, `@ConfigurationProperties` under `camunda.client.*`, Spring Boot Actuator health checks, and credentials provider auto-configuration.
- **Load Tester** (`load-tests/load-tester`): Benchmark tool with two main classes — `Starter` (creates process instances at a configurable rate) and `Worker` (completes jobs). Currently uses Typesafe Config (HOCON), not Spring Boot.
- Local dev endpoints: REST API at `localhost:8080`, gRPC at `localhost:26500`, management at `localhost:9600/actuator`.

## Code Style and Conventions

- Formatting enforced by Maven Spotless plugin — always run `./mvnw spotless:apply` before committing.
- License headers managed by `license-maven-plugin` — run `./mvnw license:format` if missing.
- Use AssertJ for test assertions (never JUnit or Hamcrest assertions).
- Use JUnit 5 for tests. Prefer Awaitility over `Thread.sleep` for async waits.
- Test method names prefixed with `should...`; use `// given`, `// when`, `// then` comment sections.

## Commit and PR Guidelines

- **Conventional Commits** format: `type: description` (max 120 chars, prefer under 72)
- Types: `build`, `ci`, `deps`, `docs`, `feat`, `fix`, `perf`, `refactor`, `style`, `test`
- Branch naming: `authorInitials-issueId-description` (e.g., `ck-123-adding-bpel-support` where `ck` = Chris Kujawa)
- Never force-push to `main`. Use `--force-with-lease` on feature branches.
- Always lint and format before committing.

## GitHub Actions

- Use Hashicorp Vault for secrets (never GitHub Secrets).
- Specify `permissions: {}` at job level with least privilege.
- Use `bash` as default shell for pipefail behavior.
- Always set `timeout-minutes` on jobs.
- End every job with `observe-build-status` action.
- Run `actionlint` and `conftest test` on workflow changes.
