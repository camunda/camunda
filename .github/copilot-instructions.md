# Camunda 8 Monorepo

Camunda 8 delivers scalable, on-demand process automation as-a-service, with execution engines for
BPMN processes and DMN decisions. This monorepo contains: Zeebe (process engine), Operate
(monitoring), Tasklist (user tasks), Identity (auth), Optimize (analytics), and supporting
libraries. Java 21 backend, React/Carbon frontends. Product documentation: https://docs.camunda.io

You are a contributor to the Camunda 8 monorepo. Scope changes to individual Maven modules,
follow established code conventions, and validate changes with module-scoped builds and tests
before committing. Don't overengineer, follow YAGNI and KISS mentality.

## Working in This Monorepo

This is a large monorepo. Always scope your work to the relevant module(s) rather than building or
testing the entire project.

Before modifying code in any module:

1. Read the module's `README.md` (if present) and any documentation in its directory.
2. Check `docs/` for cross-cutting guides (e.g., `docs/testing.md`, `docs/rest-controller.md`, or
   documents `docs/data-layer` when there are changes relating to secondary storage).
3. If working on the workflow engine, read `zeebe/engine/README.md`.

Not every module has a README yet — when one exists, treat it as the primary reference for that
module.

### Key Modules

|     Module      |                        Description                        |
|-----------------|-----------------------------------------------------------|
| `zeebe/`        | Core process engine (broker, engine, protocol, exporters) |
| `zeebe/gateway` | gRPC gateway                                              |
| `operate/`      | Process monitoring webapp                                 |
| `tasklist/`     | User task management webapp                               |
| `identity/`     | Authentication and authorization                          |
| `optimize/`     | Process analytics (skipped with `-Dquickly`)              |
| `db/`           | Database layer (rdbms, rdbms-schema)                      |
| `search/`       | Search client abstraction (Elasticsearch, OpenSearch)     |
| `service/`      | Internal services                                         |
| `clients/`      | Client libraries (Java, Spring Boot starters)             |
| `gateways/`     | Gateway implementations (HTTP mapping, MCP)               |
| `security/`     | Security core, protocol, validation                       |
| `qa/`           | Cross-component acceptance tests                          |
| `testing/`      | Process testing libraries                                 |

## Build Commands

All builds use the Maven wrapper (`./mvnw`). Use `-T1C` (one thread per CPU core) for standalone
builds. Use `-T2` (two threads total) when running builds alongside other resource-intensive
processes (e.g., an IDE, Docker containers, or other concurrent builds).

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

Note: `-Dquickly` skips tests, checks, and Optimize. Add `-DskipTests=false` to re-enable tests.

## Code Style

- Enforced by the Maven Spotless plugin (Google Java Format).
- Follow conventions in `CONTRIBUTING.md` and the
  [Zeebe Code Style wiki](https://github.com/camunda/camunda/wiki/Code-Style).

## Testing Conventions

- Test behavior, not implementation — assert on observable outcomes rather than internal state.
- Prefix test methods with `should` (e.g., `shouldRejectInvalidInput`).
- Structure tests with `// given`, `// when`, `// then` comments.
- Prefer AssertJ for assertions. Avoid introducing new JUnit or Hamcrest assertions unless the
  surrounding test already uses them.
- Use [Awaitility](http://www.awaitility.org/) for async waiting. Never use `Thread.sleep`.
- Use JUnit 5. Migrate JUnit 4 tests when modifying them.
- Detailed guide: `docs/testing.md` and `docs/testing/`.
- Reference example: `qa/acceptance-tests/src/test/java/io/camunda/it/StandaloneCamundaTest.java`

## Commit Conventions

Uses [Conventional Commits](https://www.conventionalcommits.org/). Max 120 chars for the header.

```
<type>: <description>
```

Types: `build`, `ci`, `deps`, `docs`, `feat`, `fix`, `merge`, `perf`, `refactor`, `revert`, `style`,
`test`

- Separate behavioral changes from structural/refactoring changes into distinct commits
- Commit messages should explain *why*, not just *what* changed

## Pull Request Conventions

- PR title follows conventional commit format (e.g., `feat: add user validation`)
- Reference the issue number in the description (e.g., `Closes #1234`)
- Keep PRs focused on a single concern
- Describe why the changes are necessary and note alternatives considered
- Keep descriptions brief and concise

## Git Workflow

- Never use `git push --force` on the `main` branch; use `--force-with-lease` on feature branches.
- Follow the commit and PR guidelines in `CONTRIBUTING.md`.

## Boundaries

**Always** follow the "Before Submitting" checklist below.

**Ask first:**

- Modifying shared libraries (`webapps-common/`, `webapp/client/`, `security/`)
- Changing public API contracts (REST controllers, gRPC, exported types)
- Adding new dependencies to `pom.xml` or `package.json`

**Never:**

- Run full-repo builds for single-module work
- Commit secrets, tokens, or credentials
- Force-push `main`
- Skip formatting checks

## Before Submitting

Always run these steps before every commit — never skip them, even for "obvious" or single-line
changes. Skipping formatting reliably breaks the `Java checks` CI job.

1. Format code: `./mvnw license:format spotless:apply -T1C` — **mandatory** before every commit
   that touches Java sources or `pom.xml` files. Run it again after any subsequent edit.
2. Build the changed module (see "Module-scoped builds" above for commands)
3. Run module tests and verify zero failures
4. Commit with conventional commit format

## Scoped Instructions

Additional instruction files are auto-loaded when you edit matching paths:

- Frontend code (`client/` directories) → `.github/instructions/frontend.instructions.md`
- MCP gateway (`gateways/gateway-mcp/`) → `.github/instructions/gateway-mcp-tools.instructions.md`
- Load tests (`load-tests/`, load test workflows) →
  `.github/instructions/load-tests.instructions.md`

