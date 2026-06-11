# Testing Guide

## General Principles

- Test observable behavior, not implementation details.
- Keep tests small and focused on the package under test.

## Platform Coverage

Changes under `internal/unix/` or `internal/windows/` should include matching tests, or include an explicit comment explaining why only one platform is affected. Shared behavior should be tested at the shared package level above those directories.

## Process Lifecycle

For process lifecycle changes, cover:

- PID file creation and cleanup
- `.lock` file creation and cleanup
- Stale process detection and handling
- Foreground shutdown (Ctrl+C / signal handling)
- `./c8run stop` flow
- Windows child process tracking where applicable

## Startup and Connectors

For startup changes, cover both Camunda and Connectors when behavior is shared or coordinated. Verify health check behavior and the startup URL opening flow.

## Packaging

For packaging changes:

- Verify the archive file list — the expected set of files must be present
- Verify that runtime artifacts (PID files, `log/`, `camunda-data/`, `jre/`, extracted dirs, built binaries) are not included in the archive
- Verify `JavaVersion.class` is compiled at class file version 65 (Java 21), not the version of whatever javac happened to be in PATH:

  ```bash
  file c8run/JavaVersion.class | grep "version 65.0"
  ```

  A higher version (e.g. 69 = Java 25) means `BuildJavaScripts()` ran without `--release 21` or without a pinned Java version before the packager step. The artifact will crash with `UnsupportedClassVersionError` on users with Java 21–24. Two conditions must both hold: `BuildJavaScripts()` must pass `--release 21` to `javac`, and the packaging step in both `.github/actions/setup-c8run/action.yml` and `.github/workflows/c8run-build.yaml` must call `setup-java` (temurin 21) **before** invoking the packager — relying on the runner default is not safe.

- For macOS builds, verify the bundled JRE is signed with JIT entitlements (required for JVM JIT under the hardened runtime on Apple Silicon):

  ```bash
  codesign -d --entitlements - c8run/jre/bin/java 2>&1 | grep "allow-jit"
  ```

  Missing entitlements cause an immediate `EXC_BREAKPOINT` crash (`signal: trace/BPT trap`) during `Threads::create_vm` on M-series Macs. See `.github/actions/sign-and-notarize/sign_and_notarize.sh` for the required entitlements plist.

## E2E Tests

API and Playwright-based E2E tests live in `e2e_tests/`. These are run by C8Run CI. Run locally only against a fully started C8Run instance.

### Repositories

|                  Repo                  |                              Role                              |
|----------------------------------------|----------------------------------------------------------------|
| `camunda/camunda`                      | In-repo smoke tests (`c8run/e2e_tests/`) + PR dispatch trigger |
| `camunda/c8-cross-component-e2e-tests` | Full QA E2E suite (Playwright + TestRail)                      |

### Layer 1 — In-repo smoke tests (`camunda/camunda`)

**Workflow:** `.github/workflows/c8run-build.yaml`
**Triggers:** PRs touching `c8run/**`, nightly at 23:30 UTC, and `workflow_dispatch` (can be triggered manually on any branch from the [Actions UI](https://github.com/camunda/camunda/actions/workflows/c8run-build.yaml) — uploads `camunda8-run-build-<os>` artifacts to the run summary)

What runs:
- Playwright tests (`c8run/e2e_tests/`) on Linux, macOS ARM, macOS Intel
- `api_tests.sh` — v2 API smoke (process instances, user tasks, Zeebe topology)
- Windows: same tests via `c8run.exe start --config e2e_tests/prefix-config.yaml`

**RDBMS setup:** c8run starts with `--config e2e_tests/prefix-config.yaml`, which configures H2 file-based as the secondary storage. This is the only layer that passes an explicit config file.

```yaml
camunda:
  data:
    secondary-storage:
      type: rdbms
      rdbms:
        url: jdbc:h2:file:./camunda-data/h2db
        username: sa
        password:
        flushInterval: PT0.5S
        queueSize: 1000
```

Linux/macOS use the `.github/actions/setup-c8run` composite action to build and start c8run. Windows builds `c8run.exe` from source and starts it inline.

### Layer 2 — QA E2E suite (`camunda/c8-cross-component-e2e-tests`)

**PR trigger:** When a PR in `camunda/camunda` touches `c8run/**`, `.github/workflows/trigger-c8run-e2e-tests.yml` dispatches a `run-c8run-tests` event to the QA repo, which runs `playwright_c8Run_tests_pr_trigger_linux.yml` against `stable/8.9` (Linux, RDBMS/H2 default).

**Nightly runs:**

|                     Workflow                      |           OS           | Schedule (UTC) |      Versions       |              DB               |
|---------------------------------------------------|------------------------|----------------|---------------------|-------------------------------|
| `playwright_c8Run_nightly_tests_linux.yml`        | Linux                  | 00:00          | 8.7, 8.8, 8.9, 8.10 | H2 (8.9+), ES (8.8 and older) |
| `playwright_c8Run_nightly_tests_mac.yml`          | macOS                  | 01:00          | 8.10                | H2                            |
| `playwright_c8Run_nightly_tests_windows.yml`      | Windows                | 02:00          | 8.10                | H2                            |
| `playwright_c8Run_nightly_tests_docker_linux.yml` | Linux (Docker Compose) | 01:00          | 8.7, 8.8, 8.9       | H2 (8.9), ES (older)          |

No config file is passed — H2 is the c8run default for 8.9+. The matrix sets `database: RDBMS` as an env var used for test filtering and TestRail reporting, not for DB configuration.

**Release / on-demand runs:**

|                       Workflow                        |                 Trigger                  |           OS            |
|-------------------------------------------------------|------------------------------------------|-------------------------|
| `playwright_c8Run_release_test.yml`                   | `workflow_dispatch` (release validation) | Linux + macOS + Windows |
| `playwright_c8Run_tests_manual_linux/mac/windows.yml` | `workflow_dispatch`                      | Respective OS           |

The release workflow publishes results to TestRail. Each action accepts `database`, `tasklist_version`, and `version` inputs.

### Triggering the all-OS on-demand suite against a feature branch

Use `playwright_c8Run_release_test.yml` to validate a `camunda/camunda` feature branch across all platforms before merge. The workflow checks out the branch, builds c8run from source, downloads the Camunda distribution from Nexus, packages it, and runs the full Playwright suite.

**Required inputs:**

| Input               | Description                                      | Where to get it                                               |
|---------------------|--------------------------------------------------|---------------------------------------------------------------|
| `c8Version`         | Minor version, e.g. `8.10`                       | `major.minor` of `CAMUNDA_VERSION` in `c8run/.env`           |
| `branchName`        | Branch in `camunda/camunda` to check out         | Your feature branch name                                      |
| `camundaVersion`    | Full version, e.g. `8.10.0-alpha2`               | `CAMUNDA_VERSION` in `c8run/.env`                             |
| `connectorsVersion` | Full connectors version, e.g. `8.10.0-alpha2`   | `CONNECTORS_VERSION` in `c8run/.env`                          |

**Trigger via `gh` CLI:**

```bash
# Read versions from .env
CAMUNDA_VERSION=$(grep '^CAMUNDA_VERSION=' c8run/.env | cut -d= -f2)
C8_MINOR=$(echo "$CAMUNDA_VERSION" | cut -d. -f1-2)   # e.g. 8.10
CONNECTORS_VERSION=$(grep '^CONNECTORS_VERSION=' c8run/.env | cut -d= -f2)
BRANCH=$(git branch --show-current)

gh api repos/camunda/c8-cross-component-e2e-tests/actions/workflows/playwright_c8Run_release_test.yml/dispatches \
  -X POST \
  -f ref=main \
  -F "inputs[c8Version]=$C8_MINOR" \
  -F "inputs[branchName]=$BRANCH" \
  -F "inputs[camundaVersion]=$CAMUNDA_VERSION" \
  -F "inputs[connectorsVersion]=$CONNECTORS_VERSION"
```

**Verify it queued** (run within ~10 seconds of dispatch):

```bash
gh api "repos/camunda/c8-cross-component-e2e-tests/actions/workflows/playwright_c8Run_release_test.yml/runs?per_page=3" \
  --jq '.workflow_runs[] | {id, status, created_at}'
```

Then open the run URL from the `id` field: `https://github.com/camunda/c8-cross-component-e2e-tests/actions/runs/<id>`

**When to use:** For any packaging change (archive layout, JRE bundling, lib stripping, new artifacts). The PR trigger (Layer 2 above) only runs Linux; this covers macOS and Windows too.

### RDBMS configuration summary

|             Layer              |      DB       |             How configured              |
|--------------------------------|---------------|-----------------------------------------|
| In-repo smoke                  | H2 file-based | `--config e2e_tests/prefix-config.yaml` |
| QA nightly / PR trigger (8.9+) | H2 default    | No config file                          |
| QA release / on-demand         | H2 default    | No config file                          |

No external database (Postgres, MariaDB, etc.) is provisioned in any c8run E2E CI workflow. All RDBMS coverage is H2-only. External RDBMS testing (e.g. Postgres) is tracked but not yet wired into regular CI.
