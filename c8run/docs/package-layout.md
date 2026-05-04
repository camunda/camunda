# Package Layout

## Key Areas

| Path | Description |
|---|---|
| `cmd/c8run/` | End-user CLI — `start`, `stop`, and `help` commands |
| `cmd/packager/` | CLI for building distributable C8Run archives |
| `internal/start/` | Java resolution, config locations, startup, health checks |
| `internal/shutdown/` | Stop flow and H2 data cleanup rules |
| `internal/processmanagement/` | PID files, lock files, process trees, killing, restart flow |
| `internal/unix/` | Linux and macOS command/process implementation |
| `internal/windows/` | Windows command/process implementation |
| `internal/connectors/` | Connectors launcher compatibility logic |
| `internal/packages/` | Distribution download, clean, extract, and package logic |
| `configuration/` | Default runtime configuration loaded by every start |
| `e2e_tests/` | API and Playwright checks used by C8Run CI |

## Runtime and Generated Artifacts

The following are runtime or packaging output. Do not edit them as source and do not commit them:

- Extracted `camunda-zeebe-*/` directories
- Built `camunda8-run-*/` archives
- `jre/` — downloaded Java runtime
- `camunda-data/` — runtime data directory
- `log/` — log output (`camunda.log`, `connectors.log`)
- Built binaries: `c8run`, `c8run.exe`, `packager`
- PID files and lock files

## Packaging a Distribution

Ensure `.env` in `c8run/` contains the required variables:

```dotenv
CAMUNDA_VERSION=<version>
CONNECTORS_VERSION=<version>
JAVA_ARTIFACTS_USER=<firstname.lastname>
JAVA_ARTIFACTS_PASSWORD=<your Okta password>
```

Then run from `c8run/`:

```bash
./package.sh
```

The script builds the `packager` binary and runs `./packager package`. The output is a `camunda8-run-*.tar.gz` (Linux/macOS) or `.zip` (Windows) archive ready for distribution.
