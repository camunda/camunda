# Configuration

## Config File Precedence

C8Run always loads `configuration/application.yaml` first via `--spring.config.additional-location`, then appends the user-provided `--config` file or directory last. Spring Boot resolves conflicts in favour of the last-loaded source, so user config always wins.

If `--config` points to a directory, a trailing slash is added automatically so Spring Boot loads all YAML files inside it. If it points to a file, no slash is added. This directory-detection logic lives in `cmd/c8run/main.go` for startup config path building.

The shutdown handler (`internal/shutdown/shutdownhandler.go`) also handles directory config paths, but for a different purpose: it reads config files directly to determine the active RDBMS URL. When given a directory path it resolves `application.yaml` inside it. These are parallel concerns — a change to one does not mechanically require a change to the other, but both must agree on how a directory config path maps to a file.

## JAVA_HOME Resolution Fallback Chain

`resolveJavaHomeAndBinary` in `internal/start/startuphandler.go` resolves the Java binary through a chain of fallbacks:

1. **`JAVA_HOME` env var set + symlink resolves** → use it directly
2. **`JAVA_HOME` env var set + symlink resolution fails** → retry by calling `getJavaHome()` (runs the bundled `JavaHome` class via `exec.Command(javaBinary, "JavaHome")`, which prints `System.getProperty("java.home")`)
3. **`JAVA_HOME` empty or still invalid** → `exec.LookPath("java")` to find the binary, then walk two directories up (`filepath.Dir(filepath.Dir(path))`) to derive `JAVA_HOME` from `bin/java`
4. **Walk finds no matching binary** → build a hardcoded path as last resort

Changes to this chain must ensure all four fallback paths still produce a valid binary. The two-directory walk assumes a standard `{JAVA_HOME}/bin/java` layout — non-standard JDK layouts (e.g. macOS `jre/` subdirectory structures) may fall through to the hardcoded path.

## H2 Data Directory Cleanup

`shouldDeleteDataDir` in `internal/shutdown/shutdownhandler.go` controls whether the H2 data directory is deleted on stop. Deletion only occurs when all of the following are true:

1. `SecondaryStorageType` is explicitly `"rdbms"` — an empty value skips deletion entirely
2. The active RDBMS URL resolves to an in-memory H2 connection (`jdbc:h2:mem`)

The RDBMS URL is resolved in this precedence order (first match wins):
1. `CAMUNDA_DATA_SECONDARY_STORAGE_RDBMS_URL` environment variable
2. User-provided `--config` file (if supplied)
3. `configuration/application.yaml`

**File-based H2 (`jdbc:h2:file:*`) does NOT trigger deletion.** Only in-memory H2 is cleaned up on stop.

If the version string is empty, deletion is skipped with a warning. The version string is sanitized before building the data directory path (`camunda-zeebe-{VERSION}/data`) to prevent path traversal — do not remove this guard.

## YAML Config Parsing Resilience

If a config YAML file cannot be parsed, the shutdown handler logs a warning and continues searching remaining config files rather than failing. This allows graceful fallback when one config is malformed or empty.

## RDBMS Driver Detection

When an external RDBMS URL is configured, `cmd/c8run/main.go` attempts to auto-detect and copy the correct JDBC driver JAR to `camunda-zeebe-{VERSION}/lib/`.

Vendor detection from the JDBC URL prefix:

| JDBC URL prefix | Vendor | Auto-detection |
|---|---|---|
| `jdbc:oracle:*` | Oracle | Yes — searches for `ojdbc*.jar` |
| `jdbc:mysql:*` | MySQL | Yes — searches for `mysql-connector*.jar` |
| `jdbc:postgresql:*` | PostgreSQL | No — not auto-detected; provide `--extra-driver` if needed |
| `jdbc:mariadb:*` | MariaDB | No — not auto-detected; provide `--extra-driver` if needed |
| `jdbc:sqlserver:*` | SQL Server | No — not auto-detected; provide `--extra-driver` if needed |

C8Run only fails early for missing drivers it explicitly validates during detection (currently Oracle/MySQL). For PostgreSQL, MariaDB, and SQL Server, a missing driver may only surface later at runtime.

If `CAMUNDA_VERSION` is not set when driver detection runs, the function cannot resolve the lib directory and will fail if a driver is needed.

## Environment Variable Injection

`internal/overrides/overrides.go` injects four environment variables required for local development (e.g. CSRF disabled) only if they are not already set. It does not override user-provided values.

`AdjustJavaOpts` appends to `JAVA_OPTS`:
- Username/password as `-Dcamunda.security.initialization.users[0].*` properties (only when non-default credentials are used)
- Port as a Spring property (only when not 8080)
- Keystore password if a keystore is configured

Keystore password is appended unquoted — special characters in the password may break argument parsing.
