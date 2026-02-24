# VS Code Config Sync

Scripts to merge repository-recommended VS Code configuration into your local workspace settings.
Your existing config is always preserved — the scripts only **add or update** keys defined in the
templates, and always ask for confirmation before applying changes.

## Quick start

```bash
make vscode-sync-all
```

That's it. Run this once after cloning and again whenever the templates are updated.

## What gets configured

### MCP servers (`.vscode/mcp.json`)

Template: [`.github/mcp.json.template`](../../.github/mcp.json.template)

|   Server   | Type |             Description             |
|------------|------|-------------------------------------|
| `context7` | HTTP | Up-to-date library docs for Copilot |
| `github`   | HTTP | GitHub Copilot MCP integration      |

### Terminal auto-approve rules (`.vscode/settings.json`)

Template: [`.github/settings.json.template`](../../.github/settings.json.template)

Configures `chat.tools.terminal.autoApprove` so Copilot agent mode can run safe, read-only
commands without prompting you every time, while still requiring explicit approval for anything
that could modify the system (e.g. `rm`, `curl` to external hosts, `chmod`).

Pre-approved categories:
- **Shell utils**: `ls`, `cat`, `grep`, `find`, `jq`, …
- **Git read-only**: `git status`, `git log`, `git diff`, `git show`, `git branch`
- **Maven wrapper**: `./mvnw compile/test/verify/spotless:apply/license:format/…`
- **Make**: `make help`, `make vscode-*`
- **Docker inspection**: `docker ps`, `docker compose ps/logs`
- **Local API**: `curl` against `localhost` only (e.g. Camunda REST at `:8080`)
- **Linting**: `actionlint`, `buf lint/breaking`

## Individual targets

|           Command           |                   What it does                   |
|-----------------------------|--------------------------------------------------|
| `make vscode-sync-all`      | Sync both MCP servers and settings (recommended) |
| `make vscode-mcp-sync`      | Sync MCP servers only                            |
| `make vscode-settings-sync` | Sync terminal settings only                      |

Or run the scripts directly from the repo root:

```bash
./scripts/vscode-config-sync/sync-mcp.sh
./scripts/vscode-config-sync/sync-settings.sh
```

## How the merge works

1. If `.vscode/mcp.json` (or `settings.json`) doesn't exist yet, it is created from scratch.
2. Your existing keys are read first; the template keys are merged on top.
   - For MCP: your custom servers are preserved; repo servers are added/updated.
   - For settings: repo keys take precedence (so security rules stay consistent).
3. A diff is shown before anything is written.
4. You confirm (`y`) or abort (`N`).
5. A backup is created at `.vscode/mcp.json.bak` / `.vscode/settings.json.bak`.

## Requirements

| Tool | Required for |                    Install                    |
|------|--------------|-----------------------------------------------|
| `jq` | Both scripts | `sudo apt-get install jq` / `brew install jq` |

## After syncing

Restart VS Code (or run **Developer: Reload Window** from the Command Palette) to pick up the new
MCP servers. To verify, open the Command Palette and run **MCP: List Servers**.

## Troubleshooting

**Script fails with "jq not found"**
Install `jq` (see Requirements above).

**I want to override a repo setting locally**
The scripts won't re-run on a key you've already customised unless the template value changes. If
you need to reset completely, delete `.vscode/mcp.json` (or `settings.json`) and re-run the sync.

**The templates changed — how do I update?**
Re-run `make vscode-sync-all`. The diff will show exactly what's new.
