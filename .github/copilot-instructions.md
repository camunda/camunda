# Camunda 8 Monorepo

Camunda 8 is a distributed process automation platform. This monorepo contains: Zeebe (process
engine), Operate (monitoring), Tasklist (user tasks), Identity/Admin (auth), Optimize (analytics),
and supporting libraries. Java 21 backend, React/Carbon frontends.

You are a contributor to the Camunda 8 monorepo. Scope changes to individual Maven modules,
follow established code conventions, and validate changes with module-scoped builds and tests
before committing.

## Working in This Monorepo

This is a large monorepo. Always scope your work to the relevant module(s) rather than building or
testing the entire project.

Before modifying code in any module:

1. Read the module's README.md and any documentation in its directory.
2. Check `docs/` for cross-cutting guides (e.g., `docs/testing.md`, `docs/rest-controller.md`).
3. If working on the workflow engine, read `zeebe/engine/README.md`.

### Key Modules

|   Module    |                            Description                             |
|-------------|--------------------------------------------------------------------|
| `zeebe/`    | Core process engine (broker, engine, gateway, protocol, exporters) |
| `operate/`  | Process monitoring webapp                                          |
| `tasklist/` | User task management webapp                                        |
| `identity/` | Authentication and authorization                                   |
| `optimize/` | Process analytics (skipped with `-Dquickly`)                       |
| `db/`       | Database layer (rdbms, rdbms-schema)                               |
| `search/`   | Search client abstraction (Elasticsearch, OpenSearch)              |
| `service/`  | Internal services                                                  |
| `clients/`  | Client libraries (Java, Spring Boot starters)                      |
| `gateways/` | Gateway implementations (HTTP mapping, MCP)                        |
| `security/` | Security core, protocol, validation                                |
| `qa/`       | Cross-component acceptance tests                                   |
| `testing/`  | Process testing libraries                                          |

## Build Commands

All builds use the Maven wrapper (`./mvnw`). Use `-T1C` for parallel module builds.

### Module-scoped builds (preferred)

```bash
# Build a module and its dependencies (recommended for monorepo work)
./mvnw install -pl <module> -am -Dquickly -T1C

# Build only a single module (requires dependencies to be already installed and unchanged)
./mvnw install -pl <module> -Dquickly -T1C

# Run a single test class in a module
./mvnw verify -pl <module> -Dtest=MyTestClass -DskipTests=false -DskipITs -Dquickly

# Run a single integration test in a module
./mvnw verify -pl <module> -Dit.test=MyIT -DskipTests=false -DskipUTs -Dquickly
```

### Full-repo builds

```bash
# Quick build (skips tests, checks, Optimize)
./mvnw clean install -Dquickly -T1C

# Lint and format check
./mvnw license:check spotless:check -T1C

# Auto-format (run before committing)
./mvnw license:format spotless:apply -T1C

# Unit tests only
./mvnw verify -Dquickly -DskipTests=false -DskipITs -T1C

# Integration tests only
./mvnw verify -Dquickly -DskipTests=false -DskipUTs -T1C
```

Note: `-Dquickly` skips tests, checks, and Optimize. Add `-DskipTests=false` to re-enable tests.

## Code Style

- Enforced by the Maven Spotless plugin (Google Java Format).
- Follow conventions in `CONTRIBUTING.md` and the
  [Zeebe Code Style wiki](https://github.com/camunda/camunda/wiki/Code-Style).

## Testing Conventions

- Prefix test methods with `should` (e.g., `shouldRejectInvalidInput`).
- Structure tests with `// given`, `// when`, `// then` comments.
- Prefer AssertJ for assertions. Avoid introducing new JUnit or Hamcrest assertions unless the surrounding test already uses them.
- Use [Awaitility](http://www.awaitility.org/) for async waiting. Never use `Thread.sleep`.
- Use JUnit 5. Migrate JUnit 4 tests when modifying them.
- Detailed guide: `docs/testing.md` and `docs/testing/`.

Example:

```java
@Test
void shouldRejectInvalidInput() {
  // given
  final var input = new ProcessInput("invalid");

  // when
  final var result = validator.validate(input);

  // then
  assertThat(result.isValid()).isFalse();
  assertThat(result.errors()).containsExactly("Input is not valid");
}
```

## Commit Conventions

Uses [Conventional Commits](https://www.conventionalcommits.org/). Max 120 chars for the header.

```
<type>: <description>
```

Types: `build`, `ci`, `deps`, `docs`, `feat`, `fix`, `merge`, `perf`, `refactor`, `revert`, `style`, `test`

## Pull Request Conventions

- PR title follows conventional commit format (e.g., `feat: add user validation`)
- Reference the issue number in the description (e.g., `Closes #1234`)
- Keep PRs focused on a single concern

## Git Workflow

- Never use `git push --force` on the `main` branch; use `--force-with-lease` on feature branches.
- Follow the commit and PR guidelines in `CONTRIBUTING.md`.

## Boundaries

**Always** follow the "Before Submitting" checklist below.

**Ask first:**
- Modifying shared libraries (`webapps-common/`, `client-components/`, `security/`)
- Changing public API contracts (REST controllers, gRPC, exported types)
- Adding new dependencies to `pom.xml` or `package.json`

**Never:**
- Run full-repo builds for single-module work
- Commit secrets, tokens, or credentials
- Force-push `main`
- Skip formatting checks

## Before Submitting

1. Format code: `./mvnw license:format spotless:apply -T1C`
2. Build the changed module and its dependencies: `./mvnw install -pl <module> -am -Dquickly -T1C`
3. Run tests only for the changed module: `./mvnw verify -pl <module> -DskipTests=false -DskipITs -Dquickly -T1C` (add `-DskipUTs` instead of `-DskipITs` for integration tests only)
4. Verify tests pass (zero failures)
5. Commit with conventional commit format

## Scoped Instructions

Additional instruction files are auto-loaded when you edit matching paths:

- CI/workflow files (`*.yml` in `.github/`) → `.github/instructions/ci-workflows.instructions.md`
- Frontend code (`client/` directories) → `.github/instructions/frontend.instructions.md`
- MCP gateway (`gateways/gateway-mcp/`) → `.github/instructions/gateway-mcp-tools.instructions.md`

