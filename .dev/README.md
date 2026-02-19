# `.dev/` — Camunda Development Tools

Local development tooling for the Camunda monorepo. Not shipped with the product.

## `c8env` — Environment Generator

Interactive CLI that generates docker-compose files + IntelliJ run configs for local development.
Zero dependencies — pure Bash with arrow-key menus.

### Quick Start

```bash
# Interactive mode (arrow-key menus)
.dev/c8env

# Flag mode — auto-generates name from components
.dev/c8env --db elasticsearch
.dev/c8env --connectors agentic --resource agentic-daily-briefing
.dev/c8env --auth basic --db postgres --connectors runtime
.dev/c8env --db elasticsearch --standalone
.dev/c8env --resource path/to/my.bpmn,path/to/other.bpmn
```

This generates:
- `docker-compose.yml` with selected infrastructure services
- IntelliJ run configs (one-click Run or Debug)
- `bootstrap.sh` to auto-deploy resources when `--resource` is used
- Auto-generated environment name (e.g. `c8-insecure-es`, `c8-basic-pg-ai`)

### Auto-Generated Names

Names follow the pattern `c8-<auth>-<datastore>[-connector|-ai]`:

|      Configuration      |          Name           |
|-------------------------|-------------------------|
| ES + insecure           | `c8-insecure-es`        |
| PG + basic + connectors | `c8-basic-pg-connector` |
| ES + agentic            | `c8-insecure-es-ai`     |
| OS + basic              | `c8-basic-os`           |

### Options

|     Option     |                                      Values                                      |       Default        |
|----------------|----------------------------------------------------------------------------------|----------------------|
| `--name`       | any short name                                                                   | auto from components |
| `--auth`       | `insecure`, `basic`, `oidc`                                                      | `insecure`           |
| `--db`         | `elasticsearch`, `opensearch`, `postgres`, `mysql`, `mariadb`, `oracle`, `mssql` | `elasticsearch`      |
| `--connectors` | `none`, `runtime`, `agentic`                                                     | `none`               |
| `--model`      | Ollama model name                                                                | `qwen3:0.6b`         |
| `--resource`   | Comma-separated BPMN/resource paths or built-in names                            | —                    |
| `--standalone` | Run Camunda in Docker                                                            | off (IntelliJ)       |
| `--no-install` | Skip copying configs to `.idea/`                                                 | off (auto-install)   |

### Commands

```bash
.dev/c8env                            # Generate environment (interactive)
.dev/c8env list                       # List generated environments
.dev/c8env clean <name>               # Remove environment + IntelliJ configs
.dev/c8env stop [name|--all]          # Stop environment Docker containers
.dev/c8env deploy <resource>          # Deploy a BPMN resource to Camunda
.dev/c8env deploy --list              # List available BPMN resources
.dev/c8env start <process-id>         # Start a process instance
.dev/c8env help                       # Show usage
```

### Auth Modes

- **insecure** — No authentication, fastest for development. Uses `dev,insecure` profiles.
- **basic** — Basic auth with `demo/demo` user. Enables authorization checks.
- **oidc** — OpenID Connect via Keycloak (port 18080). Pre-configured realm with `demo/demo` user.
  Keycloak admin: `admin/admin` at `http://<name>.localhost:18080/`.
  Authorization checks are **disabled** (`authorizations.enabled=false`) so the demo user can
  access all webapps without mapping rules. To test authorization, enable it manually and
  configure [mapping rules](https://docs.camunda.io/docs/self-managed/identity/mapping-rules/).
  Note: the realm is imported on first start only. If you change the realm config, run
  `docker compose -f .dev/envs/<name>/docker-compose.yml down -v` to recreate.

### Camunda Modeler Integration

`c8env` automatically configures a connection in Camunda Desktop Modeler matching the environment's
auth mode. The connection is added/updated on `generate` and removed on `clean`.

Settings file locations:
- **macOS**: `~/Library/Application Support/camunda-modeler/settings.json`
- **Linux**: `~/.config/camunda-modeler/settings.json`
- **Windows**: `%APPDATA%\camunda-modeler\settings.json`

Skip with `--no-install`.

### Connectors

- **none** — Just Camunda + datastore
- **runtime** — Adds Connectors runtime (port 8086)
- **agentic** — Adds Connectors + Ollama LLM. Enables MCP. For testing AI Agent processes.

### Resource Bootstrapping

Use `--resource` to bundle BPMN files with your environment. They get copied into the env and a
`bootstrap.sh` script is generated to deploy them automatically.

```bash
# Built-in resource by name
.dev/c8env --connectors agentic --resource agentic-daily-briefing

# Custom file paths (comma-separated)
.dev/c8env --resource path/to/process.bpmn,path/to/other.bpmn

# After Camunda starts, deploy all bundled resources
.dev/envs/c8-insecure-es-ai/bootstrap.sh
```

### Standalone Mode

By default, Camunda runs from IntelliJ (debuggable). With `--standalone`, Camunda runs as a
Docker container — no IDE needed, just `docker compose up`.

### Switching Environments

All environments share the same host ports, so only one can run at a time. When launching from
IntelliJ, the Spring Boot run config automatically stops any running environments before starting
the new one (via a Before Launch task). You can also manage this manually:

```bash
.dev/c8env stop                       # Stop the current environment
.dev/c8env stop c8-insecure-es        # Stop a specific environment
.dev/c8env stop --all                 # Stop all environments
```

### Camunda Desktop Modeler Integration

The CLI auto-configures a connection in Camunda Desktop Modeler's `settings.json` so you can
deploy BPMN diagrams directly from the Modeler. If the Modeler is not installed, a warning is
printed with a download link.

After generating an environment, restart the Modeler to pick up the new connection settings.

### Model Presets (for `--connectors agentic`)

|     Model     |  Size  |                    Notes                     |
|---------------|--------|----------------------------------------------|
| `qwen3:0.6b`  | ~270MB | Fastest, CPU-friendly, supports tool calling |
| `qwen3:1.7b`  | ~1GB   | Better quality, still fast on CPU            |
| `llama3.2:1b` | ~1.3GB | General purpose                              |

You can also specify any Ollama model name (e.g. `mistral:7b`).

## Directory Structure

```
.dev/
├── c8env                    # CLI tool
├── README.md                # This file
├── .gitignore               # Ignores envs/ (generated, personal)
├── templates/               # Template fragments for generation
│   ├── docker-compose/      # Docker service blocks (ES, PG, Ollama, Keycloak, etc.)
│   └── intellij/            # IntelliJ run config templates
├── resources/               # Sample BPMN processes and resources
│   └── bpmn/
│       └── agentic-daily-briefing.bpmn
└── envs/                    # Generated environments (gitignored)
```

## Resources

The `resources/bpmn/` directory contains sample processes you can deploy:

- **agentic-daily-briefing** — AI Agent that fetches weather, time, and Wikipedia events

```bash
# Deploy and start
.dev/c8env deploy agentic-daily-briefing
.dev/c8env start agentic-daily-briefing
```

