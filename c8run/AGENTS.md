# Camunda 8 Run

C8Run is the local, packaged distribution of Camunda 8. It starts a
single-machine Camunda 8 stack without Docker by running the Camunda
distribution and Connectors runtime, waiting for Camunda health, opening the
startup URL, printing available endpoints, and recording process IDs so the
stack can be stopped later.

You are a contributor to the C8Run module inside the Camunda 8 monorepo. Scope
changes to this Go module unless the task explicitly requires another module.
Keep changes small, follow the existing package boundaries, and validate with
C8Run-specific builds and tests.

## Working in C8Run

Before modifying code in this directory:

1. Read `README.md`.
2. Check the relevant package tests near the code you are changing.
3. If changing default secondary storage or RDBMS behavior, also check
   repository-level `docs/data-layer` guidance.

### Key Areas

|             Path              |                         Description                         |
|-------------------------------|-------------------------------------------------------------|
| `cmd/c8run/`                  | End-user CLI for `start`, `stop`, and `help`                |
| `cmd/packager/`               | CLI for building distributable C8Run archives               |
| `internal/start/`             | Java resolution, config locations, startup, health checks   |
| `internal/shutdown/`          | Stop flow and H2 data cleanup rules                         |
| `internal/processmanagement/` | PID files, lock files, process trees, killing, restart flow |
| `internal/unix/`              | Linux and macOS command/process implementation              |
| `internal/windows/`           | Windows command/process implementation                      |
| `internal/connectors/`        | Connectors launcher compatibility logic                     |
| `internal/packages/`          | Distribution download, clean, extract, and package logic    |
| `configuration/`              | Default runtime configuration loaded by every start         |
| `e2e_tests/`                  | API and Playwright checks used by C8Run CI                  |

Generated local artifacts such as extracted `camunda-zeebe-*` directories,
`camunda8-run-*` archives, `jre/`, `camunda-data/`, `log/`, built binaries,
PID files, and lock files are runtime or packaging output. Do not edit them as
source and do not commit them.

## Build Commands

Run C8Run commands from `c8run/`.

```bash
# Run all Go tests in this module
go test ./...

# Build the end-user CLI
go build -o c8run ./cmd/c8run

# Build the packager CLI
go build -o packager ./cmd/packager
```

On Windows, build the end-user CLI as:

```bash
go build -o c8run.exe ./cmd/c8run
```

To package a distribution, ensure `.env` contains `CAMUNDA_VERSION`,
`CONNECTORS_VERSION`, `JAVA_ARTIFACTS_USER`, and `JAVA_ARTIFACTS_PASSWORD`, then
run:

```bash
./package.sh
```

The package script builds `packager` and runs `./packager package`.

## Code Style

- Use `gofmt` for Go files.
- Keep package APIs narrow and aligned with the current layout. Do not add new
  dependencies unless they are clearly necessary.
- Prefer structured YAML handling for runtime configuration. Avoid ad hoc string
  parsing of `application.yaml`.
- Treat `.env` as local configuration. Never commit secrets, tokens, LDAP
  credentials, or passwords.
- Update help text, README guidance, and tests when changing public commands or
  flags.

## Testing Conventions

- Test observable behavior, not implementation details.
- Keep platform behavior covered. Changes under `internal/unix/` or
  `internal/windows/` should normally include matching tests or an explicit
  reason why only one platform is affected.
- For process lifecycle changes, cover PID files, `.lock` files, stale process
  handling, foreground shutdown, `./c8run stop`, and Windows child process
  tracking where applicable.
- For startup changes, cover both Camunda and Connectors when behavior is shared
  or coordinated.
- For packaging changes, verify the archive file list and avoid including local
  runtime artifacts.

## Runtime Rules

- C8Run is for local development and testing. It is not a production deployment
  mechanism.
- Preserve config precedence: always load `configuration/application.yaml`, then
  append the user-provided `--config` file or directory last.
- Keep the default secondary storage local-development friendly. H2 is supported
  for C8Run convenience, not for production workloads.
- Preserve Connectors launcher compatibility. Versions `8.9.0` and newer use
  Spring Boot `PropertiesLauncher`; older versions use the legacy connector
  runtime main class.
- C8Run starts Connectors from the connector bundle plus `custom_connectors/*`.
  If the user did not set `CAMUNDA_CLIENT_ZEEBE_REST_ADDRESS`, it defaults to
  the selected Camunda port.

## Run And Stop

From a prepared C8Run directory:

```bash
./c8run start
./c8run stop
```

Unix convenience wrappers do the same:

```bash
./start.sh
./shutdown.sh
```

On Windows, use:

```bash
c8run.exe start
c8run.exe stop
```

Useful start options include:

```bash
./c8run start --port 9090
./c8run start --config ./my-config.yaml
./c8run start --extra-driver ./driver.jar
./c8run start --log-level debug
./c8run start --username demo --password demo
./c8run start --startup-url http://localhost:8080/operate
```

Use `./c8run help` to print the supported commands and flags.

When running in the foreground, `Ctrl+C` initiates graceful shutdown. Logs are
written to `log/camunda.log` and `log/connectors.log`. By default the main web
and REST endpoints use port `8080`, Connectors health is on `8086`, Camunda
metrics are on `9600`, and Zeebe gRPC is on `26500`.

## Before Submitting

1. Run `gofmt` on touched Go files.
2. Run `go test ./...` from `c8run/`.
3. Build the changed binaries, usually `go build -o c8run ./cmd/c8run` and, for
   packaging changes, `go build -o packager ./cmd/packager`.
4. For markdown, Java, or `pom.xml` changes, also follow the repository-level
   formatting requirement: `./mvnw license:format spotless:apply -T1C` from the
   repository root.
5. Commit with the repository's conventional commit format and no scope.
