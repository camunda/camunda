```yaml
---
applyTo: "zeebe/docker/**"
---
```
# zeebe/docker — Docker Entrypoint Utilities

## Purpose

This directory contains the Docker container entrypoint script (`utils/startup.sh`) used exclusively by the legacy Zeebe-only `Dockerfile` (root-level). It routes container startup to one of three Zeebe binaries based on environment variables. The newer unified `camunda.Dockerfile` does NOT use this script — it uses `/usr/local/camunda/bin/camunda` directly.

## Architecture

The directory has a single file: `utils/startup.sh`. This shell script is the `ENTRYPOINT` for the root `Dockerfile` (line 158), copied into the image at `/usr/local/bin/startup.sh` (line 151).

### Startup Routing Logic

The script uses two environment variables to determine which binary to exec:

1. **`ZEEBE_STANDALONE_GATEWAY=true`** → exec `/usr/local/zeebe/bin/gateway` (standalone gateway mode)
2. **`ZEEBE_RESTORE=true`** → exec `/usr/local/zeebe/bin/restore` with optional flags:
   - `ZEEBE_RESTORE_FROM_BACKUP_ID` → `--backupId=<value>`
   - `ZEEBE_RESTORE_FROM_TIMESTAMP` → `--from=<value>`
   - `ZEEBE_RESTORE_TO_TIMESTAMP` → `--to=<value>`
   - Combinations of from/to timestamps are handled
3. **Default** → exec `/usr/local/zeebe/bin/broker` (broker mode)

### Consumer Relationship

| File | Relationship |
|------|-------------|
| `Dockerfile` (root, line 151) | Copies `startup.sh` into the Zeebe-only image |
| `camunda.Dockerfile` (root) | Does NOT use this script; uses `camunda` binary directly |
| `operate.Dockerfile`, `tasklist.Dockerfile` | Do NOT use this script |

## Key Constraints

- The script uses `#!/bin/sh -xeu` — POSIX shell with trace (`-x`), exit-on-error (`-e`), and undefined-variable-error (`-u`). Do NOT use bash-specific syntax.
- All branches use `exec` to replace the shell process with the target binary. Never add commands after `exec`.
- The `ZEEBE_STANDALONE_GATEWAY` and `ZEEBE_RESTORE` env vars default to `false` in the `Dockerfile` (line 122–123). The script relies on string comparison, not boolean evaluation.
- Parameter substitution uses `${VAR:-}` pattern to avoid `-u` flag errors on unset optional variables.

## Modification Rules

- Keep the script POSIX-compatible (`/bin/sh`). Do not introduce bashisms.
- Preserve `exec` semantics — the Zeebe binary must be PID 1 for proper signal handling in containers.
- When adding new restore flags, follow the existing `elif` chain pattern checking `${NEW_VAR:-}`.
- Test changes with `hadolint` on the consuming `Dockerfile` and `shellcheck` on the script itself.
- Coordinate with the root `Dockerfile` — any path changes here must match the `COPY` and `ENTRYPOINT` directives there.

## Common Pitfalls

- Do not add logic after `exec` calls — it will never execute.
- Do not add quotes around `true` in comparisons — `"$ZEEBE_STANDALONE_GATEWAY" = "true"` is the established pattern.
- Do not confuse this with `camunda.Dockerfile` — that Dockerfile has its own entrypoint (`/usr/local/camunda/bin/camunda`) and does not use `startup.sh`.
- The `-u` flag means every variable must be either set (via `ENV` in Dockerfile) or guarded with `${VAR:-}` default syntax.

## Reference Files

- `zeebe/docker/utils/startup.sh` — the entrypoint script (only file in this directory)
- `Dockerfile` (root) — the Zeebe-only Docker image that consumes `startup.sh`
- `camunda.Dockerfile` (root) — the unified Camunda image (does NOT use this script)