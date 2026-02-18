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

| Configuration | Name |
|---------------|------|
| ES + insecure | `c8-insecure-es` |
| PG + basic + connectors | `c8-basic-pg-connector` |
| ES + agentic | `c8-insecure-es-ai` |
| OS + basic | `c8-basic-os` |

### Options

| Option | Values | Default |
|--------|--------|---------|
| `--name` | any short name | auto from components |
| `--auth` | `insecure`, `basic` | `insecure` |
| `--db` | `elasticsearch`, `opensearch`, `postgres`, `mysql`, `mariadb`, `oracle`, `mssql` | `elasticsearch` |
| `--connectors` | `none`, `runtime`, `agentic` | `none` |
| `--model` | Ollama model name | `qwen3:0.6b` |
| `--resource` | Comma-separated BPMN/resource paths or built-in names | — |
| `--standalone` | Run Camunda in Docker | off (IntelliJ) |
| `--no-install` | Skip copying configs to `.idea/` | off (auto-install) |

### Commands

```bash
.dev/c8env                            # Generate environment (interactive)
.dev/c8env list                       # List generated environments
.dev/c8env clean <name>               # Remove environment + IntelliJ configs
.dev/c8env stop [name|--all]          # Stop environment Docker containers
.dev/c8env deploy <resource>          # Deploy a BPMN resource to Camunda
.dev/c8env deploy --list              # List available BPMN resources
.dev/c8env start <process-id>         # Start a process instance
.dev/c8env dashboard [name]           # Open environment dashboard in browser
.dev/c8env help                       # Show usage
```

### Auth Modes

- **insecure** — No authentication, fastest for development. Uses `dev,insecure` profiles.
- **basic** — Basic auth with `demo/demo` user. Enables authorization checks.

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

### Dashboard & BPMN Editor

Every generated environment includes a browser-based dashboard at **http://\<name\>.localhost:8090/**
served by an nginx container (e.g. `http://c8-insecure-es.localhost:8090/`). The `*.localhost`
subdomain resolves to `127.0.0.1` automatically — no DNS or `/etc/hosts` setup needed.

Features:
- Collapsible sidebar with live health status for Camunda, datastore, connectors, and Ollama
- Full Camunda 8 / Zeebe properties panel (task types, I/O mappings, headers)
- Open files from disk or browse mounted `.dev/resources/bpmn/` directory
- Save to disk (download or File System Access API in Chrome)
- Deploy directly to Camunda REST API (`POST /v2/deployments`)
- Start process instances (`POST /v2/process-instances`)
- Configurable endpoint URL and basic auth credentials (persisted in localStorage)
- Keyboard shortcuts: Ctrl+S (save), Ctrl+O (open)

The dashboard opens automatically when launching the Spring Boot run config from IntelliJ.

### Model Presets (for `--connectors agentic`)

| Model | Size | Notes |
|-------|------|-------|
| `qwen3:0.6b` | ~270MB | Fastest, CPU-friendly, supports tool calling |
| `qwen3:1.7b` | ~1GB | Better quality, still fast on CPU |
| `llama3.2:1b` | ~1.3GB | General purpose |

You can also specify any Ollama model name (e.g. `mistral:7b`).

## Directory Structure

```
.dev/
├── c8env                    # CLI tool
├── README.md                # This file
├── .gitignore               # Ignores envs/ (generated, personal)
├── templates/               # Template fragments for generation
│   ├── docker-compose/      # Docker service blocks (ES, PG, Ollama, dashboard, etc.)
│   ├── intellij/            # IntelliJ run config templates
│   └── landing/             # Dashboard + BPMN editor template (index.html)
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
