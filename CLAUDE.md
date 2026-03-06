# Camunda 8 Monorepo

Camunda 8 is a distributed process automation platform. This monorepo contains: Zeebe (process
engine), Operate (monitoring), Tasklist (user tasks), Identity/Admin (auth), Optimize (analytics),
and supporting libraries. Java 21 backend, React/Carbon frontends.

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
| `search/`   | Search client abstraction (Elasticsearch, OpenSearch)               |
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
# Build a module and its dependencies
./mvnw install -pl <module> -am -Dquickly -T1C

# Build only a single module (requires dependencies already installed)
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

# Full build (no tests)
./mvnw clean install -DskipChecks -DskipTests -T1C

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

## Development Environment

- REST API: `http://localhost:8080`
- gRPC API: `http://localhost:26500`
- Management API: `http://localhost:9600/actuator`
- Main class: `io.camunda.application.StandaloneCamunda`
- Spring profiles: `admin,tasklist,operate,broker,consolidated-auth,dev,insecure`

## Code Style

- Enforced by the Maven Spotless plugin (Google Java Format).
- Always run `./mvnw license:format spotless:apply -T1C` before committing.
- Follow conventions in `CONTRIBUTING.md` and the
  [Zeebe Code Style wiki](https://github.com/camunda/camunda/wiki/Code-Style).

## Testing Conventions

- Prefix test methods with `should` (e.g., `shouldRejectInvalidInput`).
- Structure tests with `// given`, `// when`, `// then` comments.
- Use AssertJ for assertions. Do not introduce JUnit or Hamcrest assertions.
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
- Branch naming: `issueId-description` (e.g., `123-adding-bpel-support`)
- Follow the commit and PR guidelines in `CONTRIBUTING.md`.

## GitHub Actions CI

- Use Hashicorp Vault for secrets (not GitHub Secrets directly)
- Set `permissions: {}` at job level with least privilege
- Set `timeout-minutes` on every job
- Default shell must be `bash`
- Last step of each job must be `observe-build-status`
- Validate with: `actionlint` and `conftest test --rego-version v0 -o github --policy .github`
- Composite actions go in `.github/actions/` (kebab-case, with README.md)
- Reference: `docs/monorepo-docs/ci.md`

## Frontend Development

The webapps (Operate, Tasklist, Identity, Optimize) each have a frontend under `client/`.
Read the module's `client/README.md` before making changes.

- Tech stack: React, TypeScript, Vite, Carbon Design System (`@carbon/react`)
- Testing: Vitest (Operate, Tasklist), react-scripts (Optimize legacy)
- Linting: ESLint, Prettier; Tasklist also uses Stylelint
- E2E: Playwright (Operate, Tasklist)
- Package managers: npm (Operate, Tasklist, Identity), Yarn (Optimize)
- Frontends are built as part of the Maven build. Skip with `-PskipFrontendBuild`.

## Boundaries

**Always:**
- Run `./mvnw license:format spotless:apply -T1C` before committing
- Use module-scoped builds for single-module work
- Follow the "Before Submitting" checklist below

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
3. Run tests only for the changed module: `./mvnw verify -pl <module> -DskipTests=false -DskipITs -Dquickly -T1C`
4. Verify tests pass (zero failures)
5. Commit with conventional commit format

## Documentation

- Module-level READMEs before modifying code
- Zeebe engine: `zeebe/engine/README.md`
- Development docs: `docs/` directory
- Architecture decisions: `docs/adr/`
- Testing guides: `docs/testing.md`, `docs/testing/`
