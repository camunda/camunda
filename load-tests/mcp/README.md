# Camunda Load Test MCP Server

A local MCP server that wraps GitHub Actions to give developers a conversational interface
for triggering, managing, and observing Camunda load tests.

## Prerequisites

- Python 3.11+
- `gh` CLI authenticated (`gh auth login`) **or** a GitHub Personal Access Token
  with `repo` and `workflow` scopes

## Installation

From the repo root:

```bash
cd load-tests/mcp
pip install -e .
```

## Claude Code Setup

Add the following to your Claude Code MCP config
(`~/.claude/mcp_servers.json` or via `claude mcp add`):

```json
{
  "mcpServers": {
    "camunda-load-tests": {
      "command": "python",
      "args": ["-m", "camunda_load_test_mcp"],
      "env": {
        "GITHUB_TOKEN": "<your-token>"
      }
    }
  }
}
```

If you have already run `gh auth login`, you can omit `GITHUB_TOKEN` — the server
will call `gh auth token` automatically.

Required GitHub token scopes: `repo`, `workflow`.

## Available Tools

| Tool | Description |
|---|---|
| `start_load_test` | Trigger a new load test from a branch |
| `update_load_test` | Redeploy an existing namespace (config change or image rebuild) |
| `get_load_test_status` | Check run status + get dashboard link |
| `list_load_tests` | List recent tests tracked locally |
| `stop_load_test` | Trigger early cleanup (deletes all TTL-expired namespaces) |

## Example Usage

> "Start a typical load test for my branch feature/my-change"

> "Is the load test for feature/my-change still running?"

> "Update the load test c8-my-change-20260416 with a new image build"

> "List all my active load tests"

## Local State

The server stores a local index of triggered tests at `~/.camunda-load-tests.json`.
This tracks run IDs and image tags across sessions. It is not shared between developers —
each person has their own local state.

## Running Tests

```bash
cd load-tests/mcp
pip install -e ".[dev]"
pytest -v
```

## Phase 2 (Planned)

Future tools will add live metric querying (throughput, latency) and run comparison
without requiring cluster access changes.
