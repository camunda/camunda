# Agent Instructions

You are a contributor to the C8Run module inside the Camunda 8 monorepo. C8Run is the local, single-machine Camunda 8 distribution — it starts the Camunda stack and Connectors runtime without Docker, manages process lifecycles, and records PIDs for clean shutdown.

Scope all changes to this Go module (`c8run/`) unless the task explicitly requires another module.

## Critical Rules

- NEVER commit secrets, tokens, LDAP credentials, or passwords from `.env`
- NEVER edit generated artifacts — see [Package Layout](docs/package-layout.md) for the full list
- NEVER add new external dependencies unless clearly necessary
- ALWAYS scope changes to the smallest affected package
- ALWAYS run `gofmt` on touched Go files before committing
- ALWAYS run `go test ./...` from `c8run/` before submitting
- ALWAYS cover platform behavior — changes under `internal/unix/` or `internal/windows/` need matching tests or an explicit reason why only one platform is affected

## Quick Start

1. Read `README.md`.
2. Check [Package Layout](docs/package-layout.md) to identify the right package for your change.
3. Determine platform scope: is this unix-only, windows-only, or shared across both?
4. Check existing tests near the code you are changing.
5. Make focused edits — preserve existing package boundaries and patterns.

## Build Commands

```bash
# Build the end-user CLI (Linux/macOS)
go build -o c8run ./cmd/c8run

# Build the end-user CLI (Windows)
go build -o c8run.exe ./cmd/c8run

# Build the packager CLI
go build -o packager ./cmd/packager
```

For packaging a full distribution archive, see [Package Layout — Packaging a Distribution](docs/package-layout.md#packaging-a-distribution).

## Lint and Format

```bash
# Format all Go files (must be clean before committing)
gofmt -w .

# Check formatting without writing
gofmt -l .

# Vet for common errors
go vet ./...
```

For Java, Markdown, or `pom.xml` changes outside `c8run/`, also run from the repository root:

```bash
./mvnw license:format spotless:apply -T1C
```

## Test Commands

```bash
# Run all Go tests in this module
go test ./...

# Run tests in a single package
go test ./internal/start/...

# Run a single test by name
go test ./internal/start/... -run TestHealthCheck

# Run with verbose output
go test -v ./...
```

## Toolchain

Go minimum: `1.25` (from `go.mod`). Toolchain: `go1.26.2`.

Verify with:

```bash
go version
```

## Code Style

### Formatting

- Use `gofmt` — output must be clean before committing.
- Formatting is managed by `gofmt`; do not override manually.

### Package Design

- Keep package APIs narrow and aligned with the current layout.
- Do not add new dependencies unless clearly necessary.
- Respect the unix/windows split — shared logic lives in packages above `internal/unix/` and `internal/windows/`.

### Configuration Handling

- Prefer structured YAML handling for runtime configuration. Avoid ad hoc string parsing of `application.yaml`.
- Treat `.env` as local configuration only. Never commit its contents.
- Update help text, `README.md`, and tests when changing public commands or flags.

### Error Handling

- Return errors with useful context — do not swallow them silently.
- In tests, use `require` for setup/fail-fast checks and `assert` for non-fatal assertions.

## Commit and Branch Conventions

- Branches: `issueId-description` (e.g., `52119-add-agents-md`)
- Commit format: Conventional Commits — `<type>: <description>` in present tense, under 120 characters
- Valid types: `feat`, `fix`, `refactor`, `test`, `docs`, `style`, `build`, `ci`, `perf`, `deps`
- No scope required

## Additional Agent Context

| File / Path | Purpose |
|---|---|
| `README.md` | Build setup, Go version requirement, `.env` config, packaging steps |
| `docs/local-development.md` | Prerequisites, quick start, and testing code changes against a local Camunda build |
| `docs/package-layout.md` | Key package areas, runtime artifact list, packaging a distribution |
| `docs/runtime-rules.md` | Config precedence, H2 rules, Connectors compatibility, ports, health check timeout, quickstart marker |
| `docs/testing-guide.md` | Platform coverage, process lifecycle, packaging, startup test expectations |
| `docs/process-lifecycle.md` | PID locking semantics, 4-state restart machine, signal handling, graceful shutdown, detached mode stub |
| `docs/configuration.md` | JAVA_HOME fallback chain, config directory handling, H2 cleanup decision tree, RDBMS driver detection |
| `docs/platform-differences.md` | Unix vs Windows: process groups, kill implementation, path/classpath conventions, archive formats, test tags |
| `.env` | Local credentials and version pins — never committed |
| `configuration/application.yaml` | Default runtime config loaded on every start |

## Recommended Agent Workflow

1. Read `README.md` and the relevant `docs/` file for your change area.
2. Identify the target package and platform scope.
3. Make focused edits — one package at a time where possible.
4. Run `go test ./...` from `c8run/`.
5. Run `gofmt -w .` and `go vet ./...`.
6. Build the changed binary to confirm it compiles: `go build -o c8run ./cmd/c8run`.
7. For packaging changes, also build and verify `packager`.
8. Commit with Conventional Commits format, no scope.
