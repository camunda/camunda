# Load Test MCP Server — Design Spec

**Date:** 2026-04-16
**Author:** Christopher Kujawa (Reliability Testing @ Camunda)
**Status:** Draft

---

## Problem

Running and managing Camunda load tests requires knowledge of GitHub Actions workflow inputs,
Helm values, namespace conventions, and image tag management. This friction means feature team
developers either skip load testing, ask the Reliability Testing team to run tests on their
behalf, or struggle through the GHA UI without knowing the right parameters.

## Goal

A local MCP server that gives any developer (and AI assistants like Claude Code) a simple,
conversational interface to trigger, manage, and observe load tests — without needing to know
the underlying GHA workflow inputs, Helm flags, or cluster conventions.

**In scope (Phase 1):**
- Trigger new load tests
- Update existing load tests (config change or image rebuild)
- Check status of a running test
- List active/recent tests
- Stop/clean up a test

**Out of scope (Phase 2 — planned):**
- Query live metrics from Prometheus (throughput, latency numbers)
- Compare metrics across two runs
- Fetch profiling/flamegraph artifacts
- Live in-place Kubernetes config patching (without full redeploy)

---

## Architecture

The server is a **local stdio MCP process** — each developer runs it on their own machine,
connected to Claude Code (or any MCP-compatible AI client). It requires only a GitHub token
(from the user's existing `gh auth login` session). No Teleport, no kubectl, no Helm needed.

```
Developer's Claude Code session
        │
        │  MCP stdio
        ▼
  load-test-mcp-server
        │
        ├── GitHub API ──► camunda-load-test.yml (workflow_dispatch)
        ├── GitHub API ──► camunda-load-test-clean-up.yml (workflow_dispatch)
        ├── GitHub API ──► poll run status / list workflow runs
        └── local state  ~/.camunda-load-tests.json  (namespace → run ID → image tag)
```

**Language:** Python (MCP SDK: `mcp`)
**Location in repo:** `load-tests/mcp/`
**GitHub API client:** `gh` CLI subprocess or `PyGithub` with token from environment

### Local State File

`~/.camunda-load-tests.json` stores per-namespace metadata set at trigger time:

```json
{
  "c8-my-feature-20260416": {
    "run_id": 12345678,
    "branch": "feature/my-thing",
    "scenario": "typical",
    "image_tag": "8.9.0-my-thing-abc123",
    "started_at": "2026-04-16T10:00:00Z",
    "ttl_days": 1
  }
}
```

This allows `update_load_test` to retrieve the current image tag (for `reuse-tag`) and
`list_load_tests` to show context without re-querying the GHA API for every entry.

---

## Tools

### `start_load_test`

Triggers a fresh deployment of `camunda-load-test.yml` via workflow dispatch. Always builds
a new image from the specified branch unless `reuse_image_tag` is provided.

**Inputs:**

| Parameter | Type | Default | Description |
|---|---|---|---|
| `branch` | string | required | Git ref to test (branch, tag, or commit SHA) |
| `scenario` | enum | `typical` | `typical`, `realistic`, `latency`, `max`, `archiver` |
| `ttl_days` | int | `1` | Days before namespace is auto-deleted |
| `name` | string | derived from branch | Namespace suffix (prefixed `c8-` by the cluster) |
| `secondary_storage` | enum | `elasticsearch` | `elasticsearch`, `opensearch`, `postgresql`, `none` |
| `enable_optimize` | bool | `false` | Deploy Optimize alongside the platform |
| `platform_helm_values` | string | `""` | Arbitrary `--set` flags passed to Helm (escape hatch) |

**Returns:** namespace name, GHA run URL, Grafana dashboard URL, estimated TTL expiry.

**Side effect:** Writes entry to `~/.camunda-load-tests.json`.

---

### `update_load_test`

Redeploys an existing namespace. Used for two distinct cases:
- **Config change, same image:** Pass `reuse_image=True` — GHA uses `reuse-tag` to skip the
  Docker build, applies new Helm values only. Fast.
- **Bug fix, rebuild:** Pass `reuse_image=False` — GHA rebuilds the image from the branch,
  then redeploys.

Note: Both paths do a full Helm upgrade (`helm upgrade --install`), which restarts Zeebe.
This is acceptable for load testing contexts where continuity is not required.

**Inputs:**

| Parameter | Type | Default | Description |
|---|---|---|---|
| `namespace` | string | required | Existing namespace to update |
| `reuse_image` | bool | `true` | Reuse the current image tag (skip rebuild) |
| `scenario` | enum | unchanged | Override scenario for the redeployment |
| `platform_helm_values` | string | unchanged | Override Helm values |
| `enable_optimize` | bool | unchanged | Override Optimize flag |

**Returns:** GHA run URL, namespace, dashboard URL.

---

### `get_load_test_status`

Polls the GHA run associated with a namespace.

**Inputs:** `namespace` (string)

**Returns:** run state (`queued` / `in_progress` / `success` / `failure`), GHA run URL,
Grafana dashboard URL, started-at timestamp, estimated TTL expiry date.

---

### `list_load_tests`

Lists recent load tests from the local state file, enriched with live GHA run status.

**Inputs:** `limit` (int, default `10`)

**Returns:** Table of namespace, branch, scenario, status, started-at, TTL expiry.

---

### `stop_load_test`

Triggers `camunda-load-test-clean-up.yml` for a specific namespace to delete it early.

**Inputs:** `namespace` (string)

**Returns:** Confirmation message and GHA cleanup run URL.

---

## Auth & Setup

Developers add the server to their Claude Code MCP config:

```json
{
  "mcpServers": {
    "camunda-load-tests": {
      "command": "python",
      "args": ["-m", "camunda_load_test_mcp"],
      "env": {
        "GITHUB_TOKEN": "<token>"
      }
    }
  }
}
```

The `GITHUB_TOKEN` can also be omitted if `gh auth login` has been run — the server falls
back to `gh auth token` to retrieve it.

Required GitHub token scopes: `repo`, `workflow`.

---

## Error Handling

- **Auth failure:** Surface the GitHub API 401/403 directly with a message pointing to
  `gh auth login` or token scope requirements.
- **Workflow dispatch failure:** Return the raw GitHub error (workflow not found, branch
  not found, etc.).
- **Run never starts:** `get_load_test_status` returns `queued` with the run URL so the user
  can investigate in the GHA UI.
- **Namespace not in local state:** `update_load_test` and `stop_load_test` fall back to
  querying recent GHA runs by namespace name if the local state file has no entry.

---

## Testing

- **Unit tests:** Mock GitHub API responses, verify tool outputs and local state mutations.
- **Smoke test:** `list_load_tests` against the real repo (read-only GitHub API call) —
  verifies auth and API connectivity without side effects.

---

## Phase 2 Extensions (Planned)

When revisiting analysis of existing load tests:

| Tool | Purpose |
|---|---|
| `get_metrics(namespace)` | Query Prometheus for throughput + latency snapshot |
| `compare_runs(ns_a, ns_b)` | Diff key metrics between two namespaces |
| `get_profiling_results(namespace)` | Fetch flamegraph artifacts from GHA run |

These additions do not require changes to the Phase 1 tools or architecture.
