# Camunda 8 Run

c8run is a packaged distribution of Camunda 8, which allows you to spin up Camunda 8 within seconds.
It packages the Java-based local Camunda runtime with a bundled JRE; the Docker Compose distribution is published separately.

Please refer to the [local installation with Camunda 8 Run guide](https://docs.camunda.io/docs/next/self-managed/quickstart/developer-quickstart/c8run/) for further details.

## Default secondary storage

Camunda 8 Run now starts with H2 as the secondary storage backend. No additional configuration or flags are required. Running `./c8run start` launches a full stack backed by H2 for local development and testing scenarios. H2 is not supported for production workloads.

1. Start Camunda 8 Run:

   ```bash
   ./c8run start
   ```
2. Stop Camunda 8 Run as usual:

   ```bash
   ./c8run stop
   ```

## Local secrets

c8run configures a local file secret store in your platform's user data directory. Add a value without exposing it in your shell history:

```bash
./c8run secrets set OPENAI_API_KEY
```

Reference it in Process Models with `=camunda.secrets.OPENAI_API_KEY`. To set several values, pass multiple names and enter each value at its prompt. Use `C8RUN_SECRETS_DIR` in your environment or c8run `.env` file to store secrets in another directory, and `C8RUN_SECRETS_CACHE_TTL` to change the default 20-minute resolution cache. Run `./c8run secrets path` to show the c8run-managed local directory. The default directory is shared across projects and c8run versions for the current OS user. This store is for local development only.

Use a dedicated dotenv file such as `.env.secrets` for imports. `c8run secrets` refuses to import the c8run `.env` because that file can contain runtime, download, and packaging credentials.

`C8RUN_SECRETS_MODE` defaults to `local`. Local mode makes `C8RUN_SECRETS_DIR` authoritative. Set `C8RUN_SECRETS_MODE=external` when `--config` or Spring settings configure a file, AWS, or GCP store; local `c8run secrets` commands are disabled in external mode.

## Physical tenants

Physical tenants are fully isolated engines inside one c8run. Each one has its own partitions, its own data in secondary storage, its own users, and its own Operate, Tasklist, Admin and Orchestration Cluster API. They require Camunda 8.10 or newer.

```bash
./c8run physical-tenants add sales # saved for the current OS user
./c8run start                     # starts "default" plus every saved physical tenant
./c8run physical-tenants list      # status, login, connectors and URLs per physical tenant
./c8run physical-tenants remove sales
```

A physical tenant is served under `http://localhost:8080/physical-tenants/<id>/`, for example `/physical-tenants/sales/operate` or `/physical-tenants/sales/v2/`. The `default` physical tenant always exists and keeps the unprefixed URLs. gRPC clients select a physical tenant with the `Camunda-Physical-Tenant` header; the Java client and Spring starter use `camunda.client.physical-tenant-id`. The startup summary lists every physical tenant's URLs and readiness.

Physical tenant IDs use lowercase letters and digits only, up to 64 characters (8 with RDBMS or H2 storage, see below). By default every physical tenant gets the same login as `c8run start` (`--username`/`--password`, `demo`/`demo` unless changed). To give a physical tenant its own user, run `./c8run physical-tenants add hr --username alice`; c8run prompts for the password (or reads it with `--password-stdin`) and saves it in the physical tenants file, which only you can read. It never appears in the generated Camunda configuration, a command line, or your shell history.

Each physical tenant gets its own connectors runtime on the next free port from 8087 upwards (logs in `log/connectors-<id>.log`). Use `--no-connectors` on `physical-tenants add` to skip it for one physical tenant, or `--disable-connectors` on `start` to skip all connectors. Each physical tenant has its own local secrets, so a physical tenant never resolves the default physical tenant's or another physical tenant's `camunda.secrets.*` values. Manage them with `--physical-tenant`, for example `./c8run secrets --physical-tenant sales set OPENAI_API_KEY` or `./c8run secrets --physical-tenant=sales set OPENAI_API_KEY`.

To run with physical tenants for one start only, without saving them (useful in CI), use `./c8run start --physical-tenants sales,hr`. Removing a physical tenant keeps its data in secondary storage under the physical tenant's prefix, so adding the same ID again restores it, including its users. `./c8run physical-tenants reset` removes all saved physical tenants.

`./c8run physical-tenants path` shows where physical tenants are saved; `C8RUN_TENANTS_FILE` selects another file. If your `--config` already declares `camunda.physical-tenants`, c8run uses it as-is and does not apply its saved physical tenants. Set `C8RUN_TENANTS_MODE=external` to leave physical tenants entirely to your `--config`: the `physical-tenants` commands are disabled, saved physical tenants are not applied, and `start --physical-tenants` is rejected.

Tools that wrap c8run, such as `c8ctl cluster`, can set `C8RUN_CLI_NAME` to the command users type, for example `C8RUN_CLI_NAME="c8ctl cluster"`. c8run then shows that name in help output and in command hints such as `c8ctl cluster physical-tenants list`. When the variable is unset, output names `c8run`.

### Breaking change: physical-tenant command names

The `tenants` and `pt` command aliases and the secrets selector `--tenant` have been removed. Update scripts and commands as follows:

- Replace `c8run tenants …` or `c8run pt …` with `c8run physical-tenants …`.
- Replace `c8run secrets --tenant <id> …` with `c8run secrets --physical-tenant <id> …`. The `--physical-tenant=<id>` form is also supported.
- With c8ctl, use `c8ctl cluster physical-tenants …` and `c8ctl cluster secrets --physical-tenant <id> …`.

Physical tenants are isolated engines; logical tenants, such as those managed by `c8ctl list tenants` and `c8ctl use tenant`, are a separate concept.

Upgrade c8run and c8ctl together to compatible releases. Older c8ctl versions translate the canonical names back to the removed forms and cannot manage physical tenants or physical-tenant secrets with this c8run. The coordinated c8ctl release must forward the canonical names unchanged and remove its legacy aliases. Existing saved physical tenant configuration, secret directories, and `C8RUN_TENANTS_FILE` / `C8RUN_TENANTS_MODE` settings do not need migration.

### Limitations

- Physical tenants require Camunda 8.10 or newer. On older versions every `physical-tenants` command is refused; `./c8run physical-tenants path` still shows the saved file if you need to delete it.
- With RDBMS secondary storage, including the bundled H2, physical tenant IDs can be at most 8 characters, because the physical tenant's tables are named `<ID>_<table>` and database identifier length is limited. Elasticsearch and OpenSearch allow up to 64.
- Removing a physical tenant does not delete its data; it stays in secondary storage under the physical tenant's prefix.
- c8run does not manage physical tenants when your `--config`, `CAMUNDA_PHYSICALTENANTS_*` environment variables or `JAVA_OPTS` already declare them, or when `C8RUN_TENANTS_MODE=external` is set.
- Each physical tenant's connectors runtime uses an extra local port (from 8087 upwards) and its own JVM.
- Physical tenants need `C8RUN_SECRETS_MODE=local`, the default, so each physical tenant gets its own secret store.

## CI requirement for merging

Only CI checks related to C8Run (those with "c8run" in the name) and CI runs marked as `required` are needed to merge. Non-C8Run-related CI checks can be ignored.

## Build C8run locally

### 1. Install Go and JDK 25+

Go **1.25 or newer** is required (the `go.mod` minimum). Verify any existing installation with `go version`.
A JDK **25 or newer** is required to package C8Run locally because the packager creates the bundled Java 25 JRE with `jdeps` and `jlink`.
The packaged runtime still falls back to a user-provided JDK 21+ when the bundled `jre/` directory is absent.

### 2. Configure LDAP credentials in `.env`

Open `c8run/.env` and add the following two lines using your Camunda LDAP credentials:

```dotenv
JAVA_ARTIFACTS_USER=<firstname.lastname>
JAVA_ARTIFACTS_PASSWORD=<your current Okta password>
```

### 3. Build the C8run binary

From the `c8run/` directory, run the appropriate command for your platform:

**Windows:**

```bash
go build -o c8run.exe ./cmd/c8run
```

**Linux / macOS:**

```bash
go build -o c8run ./cmd/c8run/
```

### 4. Package the distribution

```bash
./package.sh
```

The package step creates a platform-specific `jre/` directory and includes it in the final archive. End users do not need to install Java to start C8Run from the packaged distribution.

### 5. Start Camunda 8 Run

```bash
./start.sh
```

## Build C8Run from a feature branch (via CI)

The [`c8run-build.yaml`](https://github.com/camunda/camunda/actions/workflows/c8run-build.yaml) workflow supports `workflow_dispatch`, so you can trigger a full build and package from any branch without setting up a local environment.

Steps:

1. Go to the [C8Run: build/test workflow](https://github.com/camunda/camunda/actions/workflows/c8run-build.yaml).
2. Click **Run workflow**, select your feature branch, and start the run.
3. Wait for the run to complete.
4. Download the `camunda8-run-build-<os>` artifacts from the run summary.

The workflow produces platform-specific artifacts for Linux, macOS (ARM and Intel), and Windows; the downloaded artifacts contain the corresponding `camunda8-run*` files ready to share with colleagues or customers.

> **macOS only — Gatekeeper warning:** CI build artifacts are not code-signed (only release artifacts go through Apple notarization). If macOS blocks the binary with _"cannot be opened because the developer cannot be verified"_, remove the quarantine flag before running:
>
> ```bash
> xattr -dr com.apple.quarantine <path-to-unpacked-artifact>
> ```
>
> This is safe for internal CI artifacts. End-user release builds downloaded from the Camunda website or GitHub releases are already notarized and will not trigger this warning.

### Connectors launcher

C8Run automatically starts the connectors runtime through Spring Boot's `PropertiesLauncher` for connector bundles versioned 8.9.0 or newer (including snapshots). Older bundles continue to run via the legacy `JarLauncher`, so you can switch versions in `.env` without extra configuration.

If you want to run your own connectors runtime, start C8Run with `./c8run start --disable-connectors` to skip launching the bundled connectors jar.

To run the bundled connectors runtime on a port other than `8086`, use `./c8run start --connectors-port <port>`.

### Headless startup

To start C8Run without opening a browser window (for example, in a headless dev environment or an autostart script), use `./c8run start --no-browser`. A headless start also leaves the quickstart marker untouched, so the next regular (non-headless) start still shows the quickstart URL instead of jumping straight to Operate.
