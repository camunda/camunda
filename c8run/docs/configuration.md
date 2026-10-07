# Configuration

## Config File Precedence

C8Run always loads `configuration/application.yaml` first via `--spring.config.additional-location`, then appends the user-provided `--config` file or directory last. Spring Boot resolves conflicts in favour of the last-loaded source, so user config always wins.

If `--config` points to a directory, a trailing slash is added automatically so Spring Boot loads all YAML files inside it. If it points to a file, no slash is added. This directory-detection logic lives in `cmd/c8run/main.go` for startup config path building.

The shutdown handler (`internal/shutdown/shutdownhandler.go`) also handles directory config paths, but for a different purpose: it reads config files directly to determine the active RDBMS URL. When given a directory path it resolves `application.yaml` inside it. These are parallel concerns — a change to one does not mechanically require a change to the other, but both must agree on how a directory config path maps to a file.

## JAVA_HOME Resolution Fallback Chain

`resolveJavaHomeAndBinary` in `internal/start/startuphandler.go` resolves the Java binary through a chain of fallbacks:

1. **Bundled `jre/` exists** → use `<c8run>/jre/bin/java` directly
2. **`JAVA_HOME` env var set + symlink resolves** → use it directly
3. **`JAVA_HOME` env var set + symlink resolution fails** → retry by calling `getJavaHome()` (runs the bundled `JavaHome` class via `exec.Command(javaBinary, "JavaHome")`, which prints `System.getProperty("java.home")`)
4. **`JAVA_HOME` empty or still invalid** → `exec.LookPath("java")` to find the binary, then walk two directories up (`filepath.Dir(filepath.Dir(path))`) to derive `JAVA_HOME` from `bin/java`
5. **Walk finds no matching binary** → build a hardcoded path as last resort

Changes to this chain must ensure all fallback paths still produce a valid binary. The two-directory walk assumes a standard `{JAVA_HOME}/bin/java` layout — non-standard JDK layouts (e.g. macOS `jre/` subdirectory structures) may fall through to the hardcoded path.

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

|   JDBC URL prefix   |   Vendor   |                       Auto-detection                       |
|---------------------|------------|------------------------------------------------------------|
| `jdbc:oracle:*`     | Oracle     | Yes — searches for `ojdbc*.jar`                            |
| `jdbc:mysql:*`      | MySQL      | Yes — searches for `mysql-connector*.jar`                  |
| `jdbc:postgresql:*` | PostgreSQL | No — not auto-detected; provide `--extra-driver` if needed |
| `jdbc:mariadb:*`    | MariaDB    | No — not auto-detected; provide `--extra-driver` if needed |
| `jdbc:sqlserver:*`  | SQL Server | No — not auto-detected; provide `--extra-driver` if needed |

C8Run only fails early for missing drivers it explicitly validates during detection (currently Oracle/MySQL). For PostgreSQL, MariaDB, and SQL Server, a missing driver may only surface later at runtime.

If `CAMUNDA_VERSION` is not set when driver detection runs, the function cannot resolve the lib directory and will fail if a driver is needed.

## Environment Variable Injection

`internal/overrides/overrides.go` injects four environment variables required for local development (e.g. CSRF disabled) only if they are not already set. It does not override user-provided values.

`AdjustJavaOpts` appends to `JAVA_OPTS`:
- Username/password as `-Dcamunda.security.initialization.users[0].*` properties (only when non-default credentials are used)
- Port as a Spring property (only when not 8080)
- Keystore password if a keystore is configured

Keystore password is appended unquoted — special characters in the password may break argument parsing.

When the resolved runtime is Java 25 or newer, startup appends the required Java 25 compatibility flags to `JDK_JAVA_OPTIONS`:
- `--enable-native-access=ALL-UNNAMED`
- `--sun-misc-unsafe-memory-access=allow`

Existing `JDK_JAVA_OPTIONS` values are preserved and only missing flags are appended. Java 21–24 runtimes are left unchanged.

## Physical Tenants

`c8run tenants` (aliases `pt`, `physical-tenants`) and `start --physical-tenants` are implemented in `cmd/c8run/tenants.go`, `cmd/c8run/physicaltenants_start.go` and `internal/physicaltenants/`.

Resolution on `start` (`physicaltenants.Resolve`), first match wins:
1. Tenants declared outside c8run — a user `--config` with `camunda.physical-tenants`, `CAMUNDA_PHYSICALTENANTS_*` environment variables, or `-Dcamunda.physical-tenants.*` in `JAVA_OPTS`. c8run applies nothing and logs a notice. Combining any of these with `--physical-tenants` is an error.
2. `--physical-tenants a,b` — for this run only, every tenant uses the start login. Rejected when `C8RUN_TENANTS_MODE=external`.
3. The saved tenants file (`C8RUN_TENANTS_FILE`, default `physical-tenants.yaml` next to the local secrets directory), unless `C8RUN_TENANTS_MODE=external`.

IDs are validated against the engine rule (`[a-z0-9]{1,64}`, not `default`) before Java starts. With RDBMS secondary storage (including H2 and an unset type) IDs are limited to 8 characters (`MaxRDBMSIDLength`): table and index names are `<ID>_` plus the schema name, the longest schema name is 54 characters, and PostgreSQL truncates identifiers at 63. `tenants add` checks this before saving, and `start` checks again because the storage type can change. Startup refuses tenants when `CAMUNDA_VERSION` is a parseable version below 8.10.

The generated file `configuration/physical-tenants.generated.yaml` holds only storage isolation:
- RDBMS / bundled H2 / unset type: `camunda.physical-tenants.<id>.data.secondary-storage.rdbms.prefix: <ID>_`. The default tenant's tables keep no prefix, so existing H2 data is untouched.
- Elasticsearch / OpenSearch: `index-prefix: <id>` plus per-tenant ILM/ISM policy names.
- `none`: no storage keys.

Isolation keys follow the effective storage type, in JVM and Spring order: `-Dcamunda.data.secondary-storage.type` in `JAVA_OPTS`, then in `JDK_JAVA_OPTIONS` (last `-D` wins), then `CAMUNDA_DATA_SECONDARYSTORAGE_TYPE`, then the YAML type. The file is written only after the port check passes, so a refused second `start` never changes what `tenants list` reports as active.

It is loaded via `--spring.config.additional-location` after `configuration/` and before the user `--config`, so user settings win. The file is removed when no tenants are active and is read by `tenants list` to tell "active" from "pending restart".

Logins are not written to the generated file. Spring does not merge lists across property sources, so the whole `security.initialization` block (user + `defaultRoles.admin`) is passed as `CAMUNDA_PHYSICALTENANTS_<ID>_SECURITY_INITIALIZATION_*` environment variables. Per-tenant passwords from `tenants add --username` are stored in the tenants file itself, which is always written with mode 0600.

Each tenant gets its own local file secret store in `tenant-secrets/<id>` next to the default secrets directory (`CAMUNDA_PHYSICALTENANTS_<ID>_SECRETS_STORES_FILE_DEFAULT_PATH`); `c8run secrets --tenant <id>` manages it. Per-tenant logins and secret paths are passed to the Camunda process only, never exported into c8run's own environment, and connectors runtimes get an environment with every `CAMUNDA_PHYSICALTENANTS_*` entry removed.

Tenants and their passwords live in one document, so every change is a single atomic rename and no crash (including SIGKILL) can leave them disagreeing. Changes are serialized with a file lock (`<tenants file>.lock`); reads take no lock and create nothing. Duplicate IDs in the file are rejected. Tenant readiness probes run concurrently under one shared deadline.

Per-tenant connectors reuse `ConnectorsCmd` and append `SERVER_PORT` and `CAMUNDA_CLIENT_PHYSICALTENANTID` (plus `CAMUNDA_CLIENT_AUTH_*` when the API is protected). PID files are `connectors-<id>.process`; `stop` stops every `connectors-*.process`. Ports start at 8087, skip the Camunda port, the `--connectors-port` port and c8run's fixed ports (9600, 26500-26502), and skip ports in use on any interface.

c8run-managed tenants require `C8RUN_SECRETS_MODE=local`; in external mode startup fails with guidance, because tenants would otherwise inherit one shared external store.

After Camunda reports healthy, each tenant is probed with `POST /physical-tenants/<id>/v2/process-definitions/search` using the tenant's login. That endpoint returns 503 until the tenant's secondary storage is ready, so a 2xx means the tenant can serve requests. Unknown tenants are rejected with 404 before security runs, so a 401/403 proves the tenant is configured; because security runs before the storage check, the tenant is then shown as "up (unverified)" with a warning (under OIDC, that no token was available; under Basic auth, that the seeded login was rejected). The startup summary prints a per-tenant table. If a tenant is not ready, Camunda and the healthy tenants keep running, and `start` exits 1 naming the failed tenants.

Wrappers set `C8RUN_CLI_NAME` (for example `c8ctl cluster`) so that printed commands can be run as shown. `internal/cliname` rewrites `c8run <subcommand>` and `./c8run <subcommand>` in help text, `tenants`/`secrets` output, startup errors and notices, and the startup summary. The `pt`/`physical-tenants` aliases are not rewritten and are hidden from help under a wrapper, because wrappers delegate only the canonical subcommands. Print new hints through `cliname.Writer` or `cliname.Rewrite`, and add new subcommands to its pattern. Values printed verbatim, such as `tenants path` and `secrets path`, use the command's `plainOutput` so they are never rewritten.

`e2e_tests/physical_tenants_tests.sh` checks REST and gRPC (`Camunda-Physical-Tenant`) routing, cross-tenant login rejection, deployment isolation, per-tenant secrets, and per-tenant connectors. CI runs it in the c8run unix job with authorizations on and a tenant-specific user.
