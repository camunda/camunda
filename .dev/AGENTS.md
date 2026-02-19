# .dev/ — Agent Instructions

This directory contains `c8env`, a pure-Bash CLI tool that generates local Camunda 8 development
environments (docker-compose files + IntelliJ run configs). Read this file before modifying or
extending anything under `.dev/`.

## Architecture

```
.dev/
├── c8env                              # Main CLI (~750 lines of Bash)
├── README.md                          # User-facing documentation
├── AGENTS.md                          # This file — agent/LLM instructions
├── .gitignore                         # Ignores envs/ (generated, personal)
├── templates/                         # Template fragments assembled at generation time
│   ├── docker-compose/                # Docker service blocks
│   │   ├── header.yml                 # Networks + services header ({{ENV_NAME}})
│   │   ├── elasticsearch.yml          # ES 8.x service
│   │   ├── opensearch.yml             # OS 2.x service
│   │   ├── postgres.yml               # PostgreSQL service
│   │   ├── mysql.yml                  # MySQL service
│   │   ├── mariadb.yml                # MariaDB service
│   │   ├── oracle.yml                 # Oracle Free service
│   │   ├── mssql.yml                  # MSSQL service
│   │   ├── connectors.yml             # Connectors bundle service
│   │   ├── ollama.yml                 # Ollama + init container
│   │   ├── camunda.yml                # Standalone Camunda container
│   │   ├── keycloak.yml               # Keycloak OIDC provider
│   │   ├── keycloak/                  # Keycloak config files
│   │   │   └── camunda-dev-realm.json # Pre-configured realm (demo/demo user)
│   │   └── camunda-env/               # Env var fragments for standalone Camunda container
│   │       ├── common.yml             # Gateway security disable (always included)
│   │       ├── auth-basic.yml         # Basic auth env vars
│   │       ├── auth-oidc.yml          # OIDC auth env vars (Keycloak issuer)
│   │       ├── connector-agentic.yml  # MCP enabled flag
│   │       ├── db-elasticsearch.yml   # Exporter config for ES
│   │       ├── db-opensearch.yml      # Exporter config for OS
│   │       ├── db-postgres.yml        # RDBMS config for Postgres
│   │       ├── db-mysql.yml           # RDBMS config for MySQL/MariaDB ({{DB_TYPE}} placeholder)
│   │       ├── db-oracle.yml          # RDBMS config for Oracle
│   │       └── db-mssql.yml           # RDBMS config for MSSQL
│   └── intellij/                      # IntelliJ run config templates
│       ├── spring-boot.xml            # Main Spring Boot template (placeholders below)
│       ├── docker-infra.xml           # Docker Compose run config template
│       ├── stop-envs.xml              # Shell Script to stop all envs (Before Launch task)
│       ├── wait-for-keycloak.xml      # Shell Script to wait for Keycloak readiness (OIDC)
│       ├── env/                       # Environment variable fragments (<env> XML elements)
│       │   ├── common.xml             # Partition count, wait-for-importers
│       │   ├── auth-demo-user.xml     # Demo user init (insecure mode)
│       │   ├── connector-agentic.xml  # MCP enabled
│       │   ├── db-elasticsearch.xml   # CamundaExporter config for ES
│       │   └── db-opensearch.xml      # CamundaExporter config for OS + secondary storage
│       └── params/                    # Additional Spring Boot params (<param> XML elements)
│           ├── auth-basic.xml         # Basic auth + gateway security + demo user + authorizations
│           ├── auth-oidc.xml          # OIDC auth + Keycloak issuer + client config
│           ├── db-postgres.xml        # RDBMS connection for Postgres
│           ├── db-mysql.xml           # RDBMS connection for MySQL/MariaDB ({{DB_TYPE}} placeholder)
│           ├── db-oracle.xml          # RDBMS connection for Oracle
│           └── db-mssql.xml           # RDBMS connection for MSSQL
├── resources/                         # Sample BPMN processes
│   └── bpmn/
│       └── agentic-daily-briefing.bpmn
└── envs/                              # Generated environments (gitignored, not committed)
```

## How c8env Works

The CLI has two modes: **interactive** (arrow-key TUI menus) and **flag mode** (all options via
CLI flags). Both call the same generation functions.

### Generation Flow

1. User selects options: `auth`, `db`, `connectors`, `model`, `standalone`, `resources`
2. `generate_compose()` assembles docker-compose.yml from template fragments
3. `generate_infra_xml()` creates the Docker Compose IntelliJ run config
4. `generate_stop_xml()` creates the Shell Script run config to stop all envs before launch
5. `generate_app_xml()` creates the Spring Boot IntelliJ run config (unless standalone)
6. A `.c8env` metadata file is saved to the env directory (see below)
7. Configs are written to `.dev/envs/<name>/` and optionally installed to `.idea/runConfigurations/`
8. `install_modeler_connection()` upserts a connection in Camunda Desktop Modeler's `settings.json`;
   `detect_modeler()` checks if the Modeler is installed and prints a restart hint or download link
9. `cmd_clean()` removes the env, IntelliJ configs, and the Modeler connection

### Regeneration via `--name`

Each generated environment stores its settings in `.dev/envs/<name>/.c8env`:
```bash
_saved_auth=basic
_saved_datastore=elasticsearch
_saved_connectors=agentic
_saved_standalone=no
_saved_model=qwen3:0.6b
```

When `--name` is used in flag mode and the env already exists, saved settings are restored for
any flags not explicitly provided. This allows quick regeneration:
```bash
.dev/c8env --name c8-basic-es-ai          # restores all saved settings
.dev/c8env --name c8-basic-es-ai --db pg  # overrides only db, keeps rest
```

### Camunda Desktop Modeler Integration

The CLI auto-configures a connection in the Camunda Desktop Modeler `settings.json`:

- **macOS**: `~/Library/Application Support/camunda-modeler/settings.json`
- **Linux**: `~/.config/camunda-modeler/settings.json`
- **Windows**: `%APPDATA%\camunda-modeler\settings.json`

Connections are stored under `connectionManagerPlugin.c8connections` array. Each entry has:
- `id` — short random string (preserved on upsert)
- `name` — matches the environment name (used as upsert key)
- `targetType` — always `selfHosted`
- `authType` — `basic` or `none` (matching the env's auth mode)
- `contactPoint` — `http://localhost:8080/`
- `basicAuthUsername` / `basicAuthPassword` — only for `basic` auth

The merge logic uses `python3` to read/write JSON, upserts by `name`, and preserves all other
settings. Skipped when `--no-install` is passed or when the settings path can't be detected.

### Template Placeholders

Templates use `{{PLACEHOLDER}}` syntax, replaced via Bash string substitution:

- `{{ENV_NAME}}` — environment name (e.g. `c8-insecure-es-ai`)
- `{{APP_NAME}}` — Spring Boot run config name (same as env name)
- `{{INFRA_NAME}}` — Docker infra config name (`<env_name> Infra`)
- `{{STOP_NAME}}` — Shell Script config name (`<env_name> Stop Envs`)
- `{{WAIT_KC_NAME}}` — Shell Script config name (`<env_name> Wait for Keycloak`)
- `{{BEFORE_LAUNCH_EXTRA}}` — optional Before Launch tasks (e.g. wait-for-keycloak for OIDC)
- `{{PROFILES}}` — Spring Boot active profiles
- `{{ENV_VARS}}` — assembled `<env>` XML elements from `intellij/env/` fragments
- `{{ADDITIONAL_PARAMS}}` — assembled `<additionalParameters>` block from `intellij/params/`
- `{{COMPOSE_PATH}}` — relative path to docker-compose.yml
- `{{SERVICES_LIST}}` — `<option>` list of Docker services
- `{{CAMUNDA_EXTRA_ENV}}` — assembled env vars for standalone Camunda container
- `{{OLLAMA_MODEL}}` — Ollama model name
- `{{DB_TYPE}}` — database type for JDBC URLs (e.g. `mysql`, `mariadb`)

### Template Fragment Naming Convention

Fragments in `intellij/env/`, `intellij/params/`, and `docker-compose/camunda-env/` use prefixed
names for clarity:

- `auth-*` — authentication-related (e.g. `auth-basic.xml`, `auth-demo-user.xml`)
- `db-*` — database/datastore-related (e.g. `db-elasticsearch.xml`, `db-postgres.xml`)
- `connector-*` — connector-related (e.g. `connector-agentic.xml`)
- `common.*` — always included, no prefix

### Spring Profiles

The `dev` profile is **always** included for all environments. Profile sets:

- **insecure**: `dev,identity,tasklist,operate,broker,consolidated-auth,insecure`
- **basic**: `dev,broker,consolidated-auth,identity,tasklist,operate`
- **oidc**: `dev,broker,consolidated-auth,identity,tasklist,operate` (same as basic; OIDC config via additionalParameters)

### Key Design Decisions

- **No healthchecks** in docker-compose: IntelliJ `RunConfigurationTask` (Before Launch) blocks
  indefinitely if compose services have healthchecks. All healthchecks are intentionally removed.
- **`zeebe.broker.gateway.security.enabled=false`** is always set for local dev (both auth modes).
  Without it, the embedded gateway expects TLS which breaks local connections.
- **insecure** auth sets demo user via env vars; **basic** auth sets demo user via
  `additionalParameters` (Spring Boot properties) because basic auth also needs `authentication.method`,
  `authorizations.enabled`, and `unprotected-api` configured as Spring properties.
- **oidc** auth uses `additionalParameters` like basic, plus a Keycloak container on port 18080.
  The realm JSON at `templates/docker-compose/keycloak/camunda-dev-realm.json` is auto-imported on first start
  and includes a `demo/demo` user and `camunda` client with `camunda-secret`.
- **IntelliJ XML filenames**: spaces become `___`, special chars become `_`.

## How to Add a New Database

1. Create `templates/docker-compose/<dbname>.yml` — Docker service definition with `{{ENV_NAME}}`
2. Create `templates/docker-compose/camunda-env/db-<dbname>.yml` — standalone Camunda env vars
3. Create `templates/intellij/params/db-<dbname>.xml` — RDBMS Spring Boot params (if RDBMS)
4. If search-based (like ES/OS), create `templates/intellij/env/db-<dbname>.xml` instead
5. Update `c8env`:
   - Add to `ds_short()` function (short name mapping)
   - Add to the interactive menu options array
   - Add to the `case` in `generate_app_xml()` (env vars or rdbms_file)
   - Add to the `case` or file lookup in `generate_compose()` (if standalone needs special handling)
   - Add to `--db` validation in flag parsing
6. Update `.dev/README.md` options table

## How to Add a New Auth Mode

1. Create `templates/intellij/params/auth-<mode>.xml` — Spring Boot additional params
2. Create `templates/docker-compose/camunda-env/auth-<mode>.yml` — standalone container env vars
3. Optionally create `templates/intellij/env/auth-<mode>.xml` — env var fragment
4. Update `c8env`:
   - Add profile set in `generate_app_xml()`
   - Add auth handling in `generate_compose()` standalone block
   - Add to interactive menu and flag validation
5. Update `.dev/README.md`

## How to Add a New Connector Mode

1. Create `templates/intellij/env/connector-<mode>.xml` — env var fragment
2. Create `templates/docker-compose/camunda-env/connector-<mode>.yml` — standalone env vars
3. May need a new docker-compose service template in `templates/docker-compose/`
4. Update `c8env`:
   - Add to connector handling in `generate_app_xml()` and `generate_compose()`
   - Add to interactive menu and flag validation
5. Update `.dev/README.md`

## How to Add a New BPMN Resource

1. Place the `.bpmn` file in `resources/bpmn/`
2. Users can reference it by name (without `.bpmn` extension) via `--resource <name>`
3. The `deploy` command auto-discovers files in `resources/bpmn/`

## BPMN Process Notes (agentic-daily-briefing)

- Task type: `io.camunda.agenticai:aiagent-job-worker:1`
- AI provider: `openaiCompatible` (camelCase) — works with Ollama's OpenAI-compatible endpoint
- `fromAi(toolCall.paramName)` must appear exactly **once per parameter per tool** — duplicates
  cause "Duplicate parameter name" deployment errors
- `toolCallResults` is the output variable: array of `{id, name, content}` where `name` matches
  the BPMN task ID
- The Format Result script task uses FEEL to build `dailyBriefing` from `toolCallResults`
- BPMN XML schema: `extensionElements` must come **before** `incoming`/`outgoing` elements

## Testing Changes

```bash
# Generate and inspect without installing
.dev/c8env --auth basic --db postgres --connectors agentic --name test --no-install

# Check generated files
cat .dev/envs/test/docker-compose.yml
cat .dev/envs/test/intellij/test.xml

# Clean up
.dev/c8env clean test
```

