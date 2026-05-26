# AI Assistant Config Sync

Scripts to merge repository-recommended configuration into your local workspace settings.
Supports both VS Code Copilot and Claude Code. Your existing config is always preserved — the
scripts only **add or update** keys defined in the templates, and always ask for confirmation
before applying changes.

## Quick start

```bash
make ai-sync-all
```

That's it. Run this once after cloning and again whenever the templates are updated.

## VS Code Copilot

### MCP Configuration Sync

```bash
make vscode-mcp-sync
```

### Settings Configuration Sync

```bash
make vscode-settings-sync
```

### Sync All VS Code

```bash
make vscode-sync-all
```

## Claude Code

### MCP Configuration Sync

```bash
make claude-mcp-sync
```

Derives from `.github/mcp.json.template` into your local `.mcp.json` (gitignored). Includes
Context7 MCP server (the GitHub MCP is excluded — it requires Copilot auth; use `gh` CLI instead).

### Settings Sync

```bash
make claude-settings-sync
```

Derives from `.github/settings.json.template` into `.claude/settings.json` (gitignored). Uses
union-merge for permission arrays so your personal entries coexist with team policy.

### Sync All Claude

```bash
make claude-sync-all
```

## What gets configured

### VS Code MCP servers (`.vscode/mcp.json`)

Template: [`.github/mcp.json.template`](../../.github/mcp.json.template)

|   Server   | Type |             Description             |
|------------|------|-------------------------------------|
| `context7` | HTTP | Up-to-date library docs for Copilot |
| `github`   | HTTP | GitHub Copilot MCP integration      |

### VS Code terminal auto-approve rules (`.vscode/settings.json`)

Template: [`.github/settings.json.template`](../../.github/settings.json.template)

Configures `chat.tools.terminal.autoApprove` so Copilot agent mode can run safe, read-only
commands without prompting you every time, while still requiring explicit approval for anything
that could modify the system (e.g. `rm`, `curl` to external hosts, `chmod`).

Pre-approved categories:
- **Shell utils**: `ls`, `cat`, `grep`, `find`, `jq`, …
- **Git read-only**: `git status`, `git log`, `git diff`, `git show`, `git branch`
- **Maven wrapper**: `./mvnw compile/test/verify/spotless:apply/license:format/…`
- **Make**: `make help`, `make vscode-*`, `make claude-*`, `make ai-sync-all`
- **Docker inspection**: `docker ps`, `docker compose ps/logs`
- **Local API**: `curl` against `localhost` only (e.g. Camunda REST at `:8080`)
- **Linting**: `actionlint`, `buf lint/breaking`

### Claude MCP servers (`.mcp.json`)

Derived from: [`.github/mcp.json.template`](../../.github/mcp.json.template)

Same MCP servers as VS Code minus the GitHub server (requires Copilot auth). For GitHub
interaction, Claude uses the `gh` CLI which is auto-approved for read-only operations.

### Claude permissions (`.claude/settings.json`)

Derived from: [`.github/settings.json.template`](../../.github/settings.json.template)

Auto-approve rules (`true`) become `permissions.allow`, deny rules (`false`) become
`permissions.deny`, in Claude's `Bash(command:*)` format.

## Individual targets

|             Command             |                    What it does                     |
|---------------------------------|-----------------------------------------------------|
| `make ai-sync-all`              | Sync everything (VS Code + Claude) — recommended    |
| `make vscode-sync-all`          | Sync both VS Code MCP and settings                  |
| `make vscode-mcp-sync`          | Sync VS Code MCP servers only                       |
| `make vscode-settings-sync`     | Sync VS Code terminal settings only                 |
| `make claude-sync-all`          | Sync both Claude MCP and settings                   |
| `make claude-mcp-sync`          | Sync Claude MCP servers only                        |
| `make claude-settings-sync`     | Sync Claude permissions only                        |

Or run the scripts directly from the repo root:

```bash
./scripts/vscode-config-sync/sync-mcp.sh
./scripts/vscode-config-sync/sync-settings.sh
./scripts/vscode-config-sync/sync-claude-mcp.sh
./scripts/vscode-config-sync/sync-claude-settings.sh
```

## Canonical Sources

| Script | Source (committed) | Target (gitignored) |
|--------|-------------------|---------------------|
| `sync-mcp.sh` | `.github/mcp.json.template` | `.vscode/mcp.json` |
| `sync-settings.sh` | `.github/settings.json.template` | `.vscode/settings.json` |
| `sync-claude-mcp.sh` | `.github/mcp.json.template` | `.mcp.json` |
| `sync-claude-settings.sh` | `.github/settings.json.template` | `.claude/settings.json` |

## How the merge works

1. If the target file doesn't exist yet, it is created from the canonical source.
2. Your existing keys are read first; the template keys are merged on top.
   - For MCP: your custom servers are preserved; repo servers are added/updated.
   - For VS Code settings: repo keys take precedence (so security rules stay consistent).
   - For Claude settings: permission arrays are union-merged (your entries + repo entries).
3. A diff is shown before anything is written.
4. You confirm (`y`) or abort (`N`).
5. A backup is created (e.g. `.mcp.json.bak`).

## Requirements

| Tool | Required for |                    Install                    |
|------|--------------|-----------------------------------------------|
| `jq` | All scripts  | `sudo apt-get install jq` / `brew install jq` |

## After syncing

- **VS Code**: Restart (or **Developer: Reload Window**) to pick up new MCP servers.
- **Claude Code**: Changes take effect on next `claude` invocation.

## Troubleshooting

**Script fails with "jq not found"**
Install `jq` (see Requirements above).

**I want to override a repo setting locally**
The scripts won't re-run on a key you've already customised unless the template value changes. If
you need to reset completely, delete the target file and re-run the sync.

**The templates changed — how do I update?**
Re-run `make ai-sync-all`. The diff will show exactly what's new.

**Want to revert?** Restore from `.bak` file (e.g., `mv .mcp.json.bak .mcp.json`).
