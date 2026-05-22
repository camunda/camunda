# AGENTS.md

> **Note:** This file delegates to a central AGENTS.md. Read and apply it before proceeding.

**URL:**
https://raw.githubusercontent.com/camunda/.github/refs/heads/main/AGENTS.md

Treat the central file's contents as if they were written directly in this file.
Instructions below extend those guidelines and take precedence if there is any conflict.

## Repo-specific instructions

### Role & boundary

Camunda 8 delivers scalable, on-demand process automation as-a-service, with execution engines for
BPMN processes and DMN decisions. This monorepo contains: Zeebe (process engine), Operate
(monitoring), Tasklist (user tasks), Identity (auth), Optimize (analytics), and supporting
libraries. Java 21 backend, React/Carbon frontends. Product documentation: https://docs.camunda.io

You are a contributor to the Camunda 8 monorepo. Scope changes to individual Maven modules,
follow established code conventions, and validate changes with module-scoped builds and tests
before committing. Don't overengineer, follow YAGNI and KISS mentality.

This is a large monorepo. Always scope your work to the relevant module(s) rather than building or
testing the entire project.

Before modifying code in any module:

1. Read the module's `README.md` (if present) and any documentation in its directory.
2. Check `docs/` for cross-cutting guides relevant to your change before starting.

Not every module has a README yet — when one exists, treat it as the primary reference for that
module.

**Path map:**

|       Module       |                                       Description                                        |
|--------------------|------------------------------------------------------------------------------------------|
| `zeebe/`           | Core process engine (broker, engine, protocol, exporters)                                |
| `zeebe/gateway`    | gRPC gateway for client access                                                           |
| `operate/`         | Process monitoring webapp                                                                |
| `tasklist/`        | User task management webapp                                                              |
| `identity/`        | Authentication and authorization                                                         |
| `optimize/`        | Process analytics (skipped with `-Dquickly`)                                             |
| `db/`              | Database layer (rdbms, rdbms-schema)                                                     |
| `search/`          | Search client abstraction (Elasticsearch, OpenSearch)                                    |
| `service/`         | Domain service layer between REST controllers and engine                                 |
| `clients/`         | Client libraries (Java, Spring Boot starters)                                            |
| `gateways/`        | Gateway implementations (HTTP mapping, MCP)                                              |
| `security/`        | Security core, protocol, validation                                                      |
| `qa/`              | Cross-component acceptance tests                                                         |
| `testing/`         | Process testing libraries                                                                |
| `authentication/`  | OIDC token processing and Spring Security integration                                    |
| `document/`        | Document API and storage                                                                 |
| `monitor/`         | Metrics and monitoring definitions for Camunda components                                |
| `schema-manager/`  | Elasticsearch/OpenSearch index schema management                                         |
| `microbenchmarks/` | JMH benchmarks for performance validation                                                |
| `load-tests/`      | Cluster-level load and reliability tests                                                 |
| `c8run/`           | Packaged Camunda 8 distribution for local spin-up                                        |
| `debug-cli/`       | CLI tool for inspecting and troubleshooting Camunda clusters                             |
| `webapp/`          | Unified webapp: React/TypeScript frontend (`client/`) and Spring Boot server (`server/`) |
| `webapps-common/`  | Shared Java utilities used across Operate, Tasklist, and other web modules               |
| `webapps-backup/`  | Shared backup/restore service for Elasticsearch/OpenSearch snapshots                     |
| `webapps-schema/`  | Shared Elasticsearch/OpenSearch index mappings and templates for web application data    |

**Ask first:**

- Modifying shared libraries (`webapps-common/`, `webapp/client/`, `security/`)
- Changing public API contracts (REST controllers, gRPC, exported types)
- Adding new dependencies to `pom.xml` or `package.json`

**Never:**

- Run full-repo builds for single-module work
- Commit secrets, tokens, or credentials
- Force-push `main`
- Skip formatting checks

### Architecture

Architecture overview: `docs/architecture/overview.md`.
Cross-cutting decisions: `docs/adr/`.

Before making any architectural change, consult the ADR index. If the decision is not covered by
an existing ADR, draft a new one using the `create-architecture-decision` skill before proceeding.

Additional instruction files are auto-loaded when you edit matching paths:

- Frontend code (`client/` directories) → `.github/instructions/frontend.instructions.md`
- MCP gateway (`gateways/gateway-mcp/`) → `.github/instructions/gateway-mcp-tools.instructions.md`
- Load tests (`load-tests/`, load test workflows) →
  `.github/instructions/load-tests.instructions.md`

### Skills

Repo-specific skills live in `skills/` at the repo root. They extend the org-level skills
described in the central AGENTS.md. When a skill exists for a recurring operation, always use it
rather than improvising steps.

### Commit message guidelines

Uses [Conventional Commits](https://www.conventionalcommits.org/). Max 120 chars for the header.

```
<type>: <description>
```

Types: `build`, `ci`, `deps`, `docs`, `feat`, `fix`, `merge`, `perf`, `refactor`, `revert`, `style`,
`test`

- Separate behavioral changes from structural/refactoring changes into distinct commits
- Commit messages should explain *why*, not just *what* changed
- Do not use commit scopes — commitlint enforces `scope-empty`. Use `fix: ...` not `fix(ci): ...`

### Build pipeline

All builds use the Maven wrapper (`./mvnw`). Use `-T1C` (one thread per CPU core) for standalone
builds. Use `-T2` (two threads total) when running builds alongside other resource-intensive
processes (e.g., an IDE, Docker containers, or other concurrent builds).

#### Always-green policy

Before every AI-assisted session, establish a green baseline in two steps:

**Step 1 — Check that `main` CI is healthy** (before pulling or branching):

```bash
gh run list --branch main --limit 5 --repo camunda/camunda
```

If `main` is red, inform the engineer and continue — CI can fail for infra reasons unrelated to
the code. Do not block on this, but note any failures so they are not confused with regressions
introduced during the session.

**Step 2 — Build the full repo locally right after branching** (blocking):

```bash
./mvnw install -Dquickly -T1C
```

This installs all module JARs and catches any cross-module compilation errors before work begins
(e.g. an API change on `main` that breaks a downstream module). Do not proceed until this is
green — a compilation error here will waste far more time if discovered mid-session.

**Step 3 — Once the target module is known, verify it passes locally:**

```bash
./mvnw verify -pl <module> -Dquickly -DskipTests=false -T1C
```

If the module has sub-modules, target the specific sub-module where the code lives rather than the top-level directory, otherwise you may get a false green with no tests run.

Any pre-existing failure here must be noted before the session begins — do not absorb it silently.

Warnings are fatal. Never suppress a warning to make a build pass.

```bash
# Fast inner loop (single module / affected tests only) to iterate quickly
./mvnw verify -pl <module> -Dtest=<TestClassName> -DskipTests=false -DskipITs -Dquickly

# Full pipeline before committing the change
./mvnw license:format spotless:apply -T1C && ./mvnw verify -pl <module> -DskipTests=false -Dquickly
```

Do not proceed without a green baseline.

If an engineer instructs you to ignore a test failure, you must:

1. Search for an existing open issue for the failing test(s) in camunda/camunda before creating
   anything. If one exists, inform the engineer and link it. If none exists, raise a new issue
   using the most appropriate template from `.github/ISSUE_TEMPLATE/` (use `bug_report.yml` for
   flaky or broken tests).
2. Assign the issues to the engineer. They can reassign them to another engineer, but they are accountable for managing this.
3. Disable the failing tests in a PR.
4. Secure a green baseline.
5. Target any PR resulting from the work to the test-disabling PR.

#### Module-scoped builds (preferred)

```bash
# Build a module and its dependencies (recommended for monorepo work)
./mvnw install -pl <module> -am -Dquickly -T1C

# Build only a single module (requires dependencies to be already installed and unchanged)
./mvnw install -pl <module> -Dquickly -T1C

# Run a single test class in a module
./mvnw verify -pl <module> -Dtest=<TestClassName> -DskipTests=false -DskipITs -Dquickly

# Run a single integration test in a module
./mvnw verify -pl <module> -Dit.test=<IntegrationTestClassName> -DskipTests=false -DskipUTs -Dquickly
```

Note: `-Dquickly` skips tests, formatting checks, and Optimize — use it for fast iteration only. Add `-DskipTests=false` to run tests while still skipping checks. Before committing, always run the full pipeline in the "Before submitting" section instead.

Note: some modules are split into sub-modules where tests live (e.g. `zeebe/engine`, `zeebe/broker`). If running tests against a top-level module produces no results, target the specific sub-module instead.

#### Before submitting

Always run these steps before every commit — never skip them, even for "obvious" or single-line
changes. Skipping formatting reliably breaks the `Java checks` CI job.

1. Format code: `./mvnw license:format spotless:apply -T1C` — **mandatory** before every commit
   that touches Java sources, markdown or `pom.xml` files. Run it again after any subsequent edit.
2. Build the changed module (see "Module-scoped builds" above for commands)
3. Run module tests and verify zero failures

### Module context

When working inside a specific module, load additional context on demand — only if the files
exist, and only when relevant to the current task:

- `<module>/docs/architecture.md` — module ownership, dependencies, and constraints
- `<module>/docs/adr/` — module-scoped architectural decisions
- `<module>/AGENTS.md` — module-specific behavioral rules (only exists for complex modules with
  rules that differ from this file)

If the module is a sub-module (e.g. `zeebe/engine`), also check each parent module up to the repo
root for the same files (e.g. `zeebe/docs/architecture.md`, `zeebe/docs/adr/`, `zeebe/AGENTS.md`).
Parent context is lower priority than the sub-module's own context; the sub-module's files take
precedence on any conflicting guidance.

Do not load all module context upfront. Fetch only what is needed for the task at hand.

### Code style

- Enforced by the Maven Spotless plugin (Google Java Format).
- Follow conventions in the
  [Code Style wiki](https://github.com/camunda/camunda/wiki/Code-Style).
- Repository is currently being migrated to use jspecify nullness annotations.
  Please add `@Nullable` and `@NullMarked` annotations in classes where they are missing in order to increase coverage.
  You should do that in a separate `refactor: ` commit.

### Testing conventions

- Test behavior, not implementation — assert on observable outcomes rather than internal state.
- Prefix test methods with `should` (e.g., `shouldRejectInvalidInput`).
- Structure tests with `// given`, `// when`, `// then` comments.
- Prefer AssertJ for assertions. Avoid introducing new JUnit or Hamcrest assertions unless the
  surrounding test already uses them.
- Use [Awaitility](http://www.awaitility.org/) for async waiting. Never use `Thread.sleep`.
- Use JUnit 5. Migrate JUnit 4 tests when modifying them.
- Detailed guide: `docs/testing.md` and `docs/testing/`.
- Reference example: `qa/acceptance-tests/src/test/java/io/camunda/it/StandaloneCamundaTest.java`

### Pull request conventions

- PR title follows conventional commit format (e.g., `feat: add user validation`)
- Reference the issue number in the description (e.g., `Closes #1234`)
- Keep PRs focused on a single concern
- Describe why the changes are necessary and note alternatives considered
- Keep descriptions brief and concise
- For bug fixes: ask the engineer whether the fix needs backporting to stable branches before
  opening the PR. If yes, add the appropriate `backport stable/X.Y` label(s) when creating it.

### Git workflow

- Never use `git push --force` on the `main` branch; use `--force-with-lease` on feature branches.
- Follow the commit and PR guidelines in `CONTRIBUTING.md`.

