```yaml
---
applyTo: "**"
---
```
# Camunda 8 — Repository Overview

Camunda 8 is a cloud-native BPMN/DMN process automation platform (v8.10.0-SNAPSHOT). Java 21 monorepo with React/Carbon frontends, built with Maven, exposing REST, gRPC, and MCP APIs. Core engine is Zeebe (event-sourced, Raft consensus, RocksDB state).

## Directory Structure

| Directory | Purpose |
|-----------|---------|
| `zeebe/` | Core process engine: broker, gateway-rest, gateway-grpc, gateway-protocol, engine, atomix (Raft), exporters, protocol, zb-db, scheduler, stream-platform, bpmn-model |
| `operate/` | Process monitoring webapp (React 18 + Spring Boot backend) |
| `tasklist/` | Human task management webapp (React 19 + Spring Boot backend) |
| `optimize/` | Process analytics webapp (React 19 + Spring Boot backend) |
| `identity/` | User/role/tenant admin UI (React 18 + Vite) |
| `gateways/` | API translation: `gateway-model` (OpenAPI codegen), `gateway-mapping-http` (mappers), `gateway-mcp` (MCP tools) |
| `service/` | Central business services (~35 classes extending `ApiServices<T>`) |
| `search/` | Backend-agnostic search abstraction (ES, OS, RDBMS) |
| `db/` | RDBMS layer: `rdbms` (MyBatis CQRS), `rdbms-schema` (Liquibase migrations) |
| `security/` | Auth validation, permissions (5 submodules) |
| `authentication/` | Spring Security auth modes (BASIC, OIDC, unprotected) |
| `configuration/` | Unified config mapping (`camunda.*` properties) |
| `clients/` | SDKs: Java (`CamundaClient`), Spring Boot starter |
| `dist/` | Spring Boot assembly (`StandaloneCamunda`, `StandaloneBroker`, etc.) |
| `qa/` | Acceptance, ArchUnit, compatibility, multi-DB E2E tests |
| `testing/` | Process testing SDK (JUnit 5 + Testcontainers) |
| `document/` | Pluggable blob storage (S3, GCP, local) |
| `docs/` | Developer documentation (testing, observability, RDBMS) |
| `.github/` | CI workflows (118), composite actions (25), Rego policies |
| `c8run/` | Go CLI for local Camunda distribution |

## Build Commands

| Task | Command |
|------|---------|
| Quick build | `./mvnw install -Dquickly -T1C` |
| Clean | `./mvnw clean -T1C` |
| Auto-format | `./mvnw license:format spotless:apply -T1C` |
| Lint | `./mvnw license:check spotless:check -T1C` |
| Unit tests | `./mvnw verify -Dquickly -DskipTests=false -DskipITs -T1C` |
| Integration tests | `./mvnw verify -Dquickly -DskipTests=false -DskipUTs -T1C` |
| All tests | `./mvnw verify -T1C` |
| Static analysis | `./mvnw verify -Dquickly -DskipChecks=false -P'!autoFormat,checkFormat,spotbugs' -T1C` |
| Scoped test | `./mvnw -pl <module> -am test -DskipITs -DskipChecks -Dtest=<Class> -T1C` |
| Docker image | `./mvnw install -DskipChecks -DskipTests -T1C && docker build -f camunda.Dockerfile --target app -t "camunda/camunda:current-test" --build-arg DISTBALL=dist/target/camunda-zeebe-*.tar.gz .` |

## Module System and Dependencies

- Package prefix: `io.camunda.*` (e.g., `io.camunda.service`, `io.camunda.zeebe.engine`)
- Maven multi-module; parent POM at `parent/pom.xml` manages all versions (~160 properties)
- Use `./mvnw` wrapper (Maven 3.9.12); never install deps globally
- Frontend builds via `frontend-maven-plugin`; Yarn for JS packages

## Key Entry Points

- `dist/.../StandaloneCamunda.java` — all-in-one Spring Boot app (broker + webapps)
- `dist/.../ModesAndProfilesProcessor.java` — profile-driven component composition
- REST API: port 8080 (`/v2/*`), gRPC: port 26500, Management: port 9600

## Error Handling Patterns

- **Functional Either**: `Either<L, R>` in `zeebe/util` — `.fold()`, `.map()`, `.flatMap()` for railway-oriented flow (see `zeebe/util/.../Either.java`)
- **REST**: RFC 7807 `ProblemDetail` via `RestErrorMapper` — `.fold(RestErrorMapper::mapProblemToCompletedResponse, ...)` (see `zeebe/gateway-rest/.../ProcessInstanceController.java`)
- **MCP tools**: `CallToolResultMapper.mapErrorToResult(e)` / `.mapProblemToResult(problem)` (see `gateways/gateway-mcp/.../CallToolResultMapper.java`)
- **Engine**: No-throw processors returning rejection records; `ErrorMapper` in service layer

## Logging

- SLF4J API (`org.slf4j.Logger` / `LoggerFactory`) with Log4j2 backend
- Use `private static final Logger LOG = LoggerFactory.getLogger(MyClass.class);`

## Configuration

- Spring Boot properties under `camunda.*` namespace
- `UnifiedConfigurationHelper` for backwards-compatible property migration
- Secrets via HashiCorp Vault (never GitHub Secrets); env vars for sensitive data
- Spring `@Conditional` annotations for backend selection (ES vs OS vs RDBMS)

## File Naming

- Java: PascalCase classes; `*Test` suffix (unit), `*IT` suffix (integration)
- GitHub Actions: kebab-case for workflows and composite action directories
- XML/YAML: 2-space indent; Java: Google Java Format via Spotless

## Before Committing

1. Always run `./mvnw license:format spotless:apply -T1C`
2. Follow Conventional Commits: `<type>: <description>` (max 120 chars, no scope)
3. Valid types: `build`, `ci`, `deps`, `docs`, `feat`, `fix`, `merge`, `perf`, `refactor`, `revert`, `style`, `test`
4. Read module `README.md` and `docs/` before modifying any module