# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Camunda 8 monorepo — a distributed, cloud-native process automation platform. Java 21 backend (Maven), React/Carbon frontends. Contains the Orchestration Cluster components: Zeebe (process engine), Operate (monitoring), Tasklist (human tasks), Identity/Admin (auth), and Optimize (analytics).

## Build Commands

```bash
# Quick build (skips tests, checks, and Optimize)
./mvnw clean install -Dquickly -T1C

# Full build (skip tests/checks only)
./mvnw clean install -DskipChecks -DskipTests -T1C

# Lint / check formatting
./mvnw license:check spotless:check -T1C

# Auto-format code and license headers
./mvnw license:format spotless:apply -T1C

# Run unit tests only
./mvnw verify -Dquickly -DskipTests=false -DskipITs -T1C

# Run integration tests only
./mvnw verify -Dquickly -DskipTests=false -DskipUTs -T1C

# Run all tests
./mvnw verify -T1C

# Run static checks (formatting + spotbugs)
./mvnw verify -Dquickly -DskipChecks=false -P'!autoFormat,checkFormat,spotbugs' -T1C

# Build single module (example: zeebe/engine)
./mvnw install -Dquickly -T1C -pl zeebe/engine -am

# Run tests in a single module
./mvnw verify -Dquickly -DskipTests=false -pl zeebe/engine

# Build profiling (generates report in target/)
./mvnw install -Dquickly -T1C -Dprofile
```

## Running Locally

Entry point: `io.camunda.application.StandaloneCamunda` in `dist/`.
Requires Elasticsearch (or OpenSearch) running locally.

```bash
# Start Elasticsearch via Docker (from operate dir)
docker compose -f operate/docker-compose.yml up -d elasticsearch

# IntelliJ run config profiles:
#   admin,tasklist,operate,broker,consolidated-auth,dev,insecure
```

Default endpoints: REST API `localhost:8080`, gRPC `localhost:26500`, Actuator `localhost:9600/actuator`.

## Architecture

### Module Structure

| Module | Purpose |
|--------|---------|
| `zeebe/` | Process engine core — broker, engine, gateway (gRPC + REST), protocol, exporters, clustering (Atomix), RocksDB state, logstreams |
| `operate/` | Process monitoring webapp (Spring Boot + React) |
| `tasklist/` | Human task management webapp (Spring Boot + React) |
| `identity/` | Authentication & authorization (admin panel) |
| `optimize/` | Process analytics (excluded from `-Dquickly` builds) |
| `dist/` | Distribution assembly and `StandaloneCamunda` entry point |
| `clients/java` | Java client library (language level 8) |
| `clients/camunda-spring-boot-*-starter` | Spring Boot starter SDKs (2.x, 3.x, 4.x variants) |
| `search/` | Search engine abstraction (Elasticsearch + OpenSearch implementations) |
| `service/` | Shared business logic and data access services |
| `db/` | RDBMS schema and implementations (PostgreSQL, MySQL, H2) |
| `schema-manager/` | Database schema management |
| `authentication/` | Shared auth mechanisms |
| `webapps-common/` | Shared webapp code |
| `client-components/` | Shared React component library |
| `qa/` | Acceptance tests, E2E tests, ArchUnit tests |
| `testing/` | Process test framework (`camunda-process-test-java`, Spring, DSL) |
| `gateways/` | Gateway model, HTTP mapping, MCP gateway |
| `configuration/` | Unified config management |

### Key Architectural Patterns

- **Spring Boot profiles** control which components run: `broker`, `operate`, `tasklist`, `admin`, `consolidated-auth`, `gateway`
- **Exporter pattern**: pluggable record exporters ship data from Zeebe to Elasticsearch/OpenSearch/RDBMS
- **Search abstraction**: `search/` provides unified client interface over Elasticsearch and OpenSearch
- **Event sourcing**: Zeebe uses append-only logs (`logstreams/`) with RocksDB (`zb-db/`) for state
- **Atomix**: handles clustering, consensus, and transport between broker nodes

### Zeebe Engine Internals

The engine (`zeebe/engine/`) is the stream processor that executes BPMN. Key sub-areas:
- `broker/` — server-side broker with partitioned state
- `gateway/`, `gateway-grpc/`, `gateway-rest/` — client-facing APIs
- `protocol/` — SBE (Simple Binary Encoding) message definitions
- `exporters/` — camunda-exporter, elasticsearch-exporter, opensearch-exporter, rdbms-exporter
- `atomix/` — transport, membership, Raft consensus
- `bpmn-model/`, `dmn/`, `feel/` — model APIs and expression evaluation
- `scheduler/` — actor-based task scheduler

## Commit Conventions

[Conventional Commits](https://www.conventionalcommits.org/) format, enforced by commitlint. No scope. Max header 120 chars.

```
<type>: <description>
```

Types: `build`, `ci`, `deps`, `docs`, `feat`, `fix`, `merge`, `perf`, `refactor`, `revert`, `style`, `test`

Always lint and format before committing: `./mvnw license:format spotless:apply -T1C`

## Testing Conventions

- Use JUnit 5. Migrate JUnit 4 tests when modifying them.
- Use **AssertJ** for assertions (never JUnit or Hamcrest assertions).
- Test names: prefix with `should...`
- Structure tests with `// given`, `// when`, `// then` comments.
- Use [Awaitility](http://www.awaitility.org/) for async waits, never `Thread.sleep`.
- Detailed guides: `docs/testing.md` and `docs/testing/`.

## Code Style

- Enforced by Spotless Maven plugin (Google Java Format).
- License headers enforced via `license:check` / `license:format`.
- Module-specific docs: always check for README.md and markdown files in the module directory before making changes.
- Engine-specific: see `zeebe/engine/README.md`.

## GitHub Actions CI

- Validate workflow changes: `actionlint` and `conftest test --rego-version v0 -o github --policy .github`
- Use `permissions: {}` at job level, `bash` as default shell, always set `timeout-minutes`
- Use Vault for secrets (no GitHub Secrets), `setup-maven-cache` for Maven jobs
- Each job must end with the `observe-build-status` action step

## Important Notes

- JDK 21 required. Some client/protocol modules target language level 8.
- macOS Apple Silicon: install Rosetta (`softwareupdate --install-rosetta`) if protoc fails.
- Optimize is excluded from `-Dquickly` builds (use `-P include-optimize` to include it).
- Only open the project in one IDE at a time to avoid Maven conflicts.
