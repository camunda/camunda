# Camunda 8 Monorepo

Camunda 8 is a distributed process automation platform. This monorepo contains: Zeebe (process
engine), Operate (monitoring), Tasklist (user tasks), Identity/Admin (auth), Optimize (analytics),
and supporting libraries. Java 21 backend, React/Carbon frontends.

## Working in This Monorepo

Always scope work to the relevant module(s). Never build or test the entire project for single-module changes.

Before modifying code, read the module's README.md and check `docs/` for cross-cutting guides.

### Key Modules

| Module | Description |
|---|---|
| `zeebe/` | Core process engine (broker, engine, gateway, protocol, exporters) |
| `operate/` | Process monitoring webapp |
| `tasklist/` | User task management webapp |
| `identity/` | Authentication and authorization |
| `optimize/` | Process analytics (skipped with `-Dquickly`) |
| `db/` | Database layer (rdbms, rdbms-schema) |
| `search/` | Search client abstraction (Elasticsearch, OpenSearch) |
| `clients/` | Client libraries (Java, Spring Boot starters) |
| `gateways/` | Gateway implementations (HTTP mapping, MCP) |
| `security/` | Security core, protocol, validation |

## Build Commands

All builds use `./mvnw`. Use `-T1C` for parallel builds, or `-T2` if running builds alongside other resource-intensive processes. Prefer module-scoped builds.

```bash
# Build a module and its dependencies
./mvnw install -pl <module> -am -Dquickly -T1C

# Run a single test class
./mvnw verify -pl <module> -Dtest=MyTestClass -DskipTests=false -DskipITs -Dquickly

# Run a single integration test
./mvnw verify -pl <module> -Dit.test=MyIT -DskipTests=false -DskipUTs -Dquickly

# Auto-format (run before committing)
./mvnw license:format spotless:apply -T1C
```

Note: `-Dquickly` skips tests, checks, and Optimize. Add `-DskipTests=false` to re-enable tests.

## Testing Conventions

- Prefix test methods with `should` (e.g., `shouldRejectInvalidInput`)
- Structure tests with `// given`, `// when`, `// then` comments
- Use AssertJ for assertions — never JUnit or Hamcrest assertions
- Use Awaitility for async waiting — never `Thread.sleep`
- Use JUnit 5. Migrate JUnit 4 tests when modifying them.
- Detailed guide: `docs/testing.md`

## Commit Conventions

[Conventional Commits](https://www.conventionalcommits.org/) format, max 120 chars header.

Types: `build`, `ci`, `deps`, `docs`, `feat`, `fix`, `merge`, `perf`, `refactor`, `revert`, `style`, `test`

- Separate behavioral changes from structural/refactoring changes into distinct commits
- Commit messages should explain *why*, not just *what* changed
- In PRs, describe why the changes are necessary and note alternatives considered

## Before Submitting

1. Format: `./mvnw license:format spotless:apply -T1C`
2. Build the changed module: `./mvnw install -pl <module> -am -Dquickly -T1C`
3. Test the changed module: `./mvnw verify -pl <module> -DskipTests=false -DskipITs -Dquickly -T1C`
4. Verify zero test failures
5. Commit with conventional commit format

## Boundaries

**Always:** Format before committing. Use module-scoped builds. Read module docs first.

**Ask first:** Modifying shared libraries (`webapps-common/`, `client-components/`, `security/`). Changing public API contracts. Adding new dependencies.

**Never:** Full-repo builds for single-module work. Commit secrets. Force-push `main`.

## Key References

- Code style: enforced by Spotless (Google Java Format) — see `CONTRIBUTING.md`
- CI conventions: `docs/monorepo-docs/ci.md`
- Frontend conventions: each app's `client/README.md`
- Architecture decisions: `docs/adr/`
- Zeebe engine internals: `zeebe/engine/README.md`
