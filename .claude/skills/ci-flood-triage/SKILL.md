---
name: ci-flood-triage
description: Triages a flood of CI incidents in camunda/camunda. Use when multiple CI incidents open in a short window and the medic needs to orient fast — find the shared pattern, identify outliers, and know what to do next. Re-runnable as new incidents arrive.
user-invocable: true
argument-hint: "[minutes] — how far back to look, default 30"
---

# CI Flood Triage

Gives the medic a fast orientation snapshot when a flood of CI incidents opens at once. Finds the
shared pattern, flags outliers, and drafts a broadcast message. Read-only by default — generating
the snapshot never writes to incident.io. If the medic explicitly asks to merge incidents
afterward, this skill can do that too, always with a confirmation step first (see Step 6).

## Prerequisites

- **incident.io MCP** — required. Verify the `mcp__incident-io__incident_show` tool is available.
  If not, tell the user to [configure the incident.io MCP server](https://docs.incident.io/ai/remote-mcp)
  and stop.

Throughout this skill, MCP tools are referenced by their short verb (`incident_list`,
`incident_show`, `incident_update`); the actual tool name is namespaced by whichever MCP server
is configured (e.g. `mcp__incident-io__incident_list`).

## Scope

Multiple incidents only. If the user passes a single incident ID, redirect them to `/ci-incident`
for deep-dive investigation of that one incident.

This skill owns the multi-incident merge action when the medic asks for it — it already has the
correlation context (which incidents share a cause) needed to do that safely. `/ci-incident` is
scoped to driving a single incident's investigation and status updates once assigned; it does not
handle cross-incident merging.

## Procedure

### Step 1 — Parse the time window

Read `$ARGUMENTS`. Supported forms:

| Argument | Meaning |
|---|---|
| *(empty)* | last 30 minutes |
| `60` | last N minutes |
| `--since 2026-07-06T15:00Z` | from that UTC timestamp to now |
| `--since 2026-07-06T15:00Z --until 2026-07-06T17:30Z` | specific historical range |

Timestamps must be RFC 3339 / ISO 8601 UTC (append `Z` if no timezone given).
When `--until` is provided, this is a **historical replay** — adjust Step 2 accordingly.

### Step 2 — Fetch CI incidents

Call `incident_list` with:
- `created_after`: derived from Step 1
- `created_before`: `--until` value if provided, otherwise omit
- `include: ["summary"]`
- `status_category: ["triage", "active"]` — **omit this filter for historical replays** (closed
  incidents won't appear otherwise)
- Page through all results until no more pages

If the MCP server exposes a `config://organisation` resource (or equivalent), you may read it to
find the CI incident type ID and filter to it. This is optional, not a required step — if the
resource isn't available, or you can't determine the type ID from it, proceed without the filter
(results may include non-CI incidents) and note this in the output.

Record: total count, the list of (reference, name, summary, reported_at) for each.

### Step 3 — Reason about the pattern

Look at all incident names and summaries together. Ask: do these incidents point to a shared root
cause, or are they independent failures?

Read `references/flood-patterns.md` for known archetypes — use them to inform your reasoning, not
to gate it. Real floods may not match either pattern cleanly.

Signals to look for:
- Common job name fragments across incident names
- Shared failure description in summaries (same error, same upstream, same timing)
- Tight timing cluster vs spread over many hours
- Whether summaries mention the same GHA run URLs

Form a working hypothesis: "probably one cause" vs "probably independent" vs "unclear, need more
data."

### Step 4 — Dig where uncertain

If the hypothesis is unclear, or summaries are thin, call `incident_show` on the incidents that
need it — not the whole flood. That's normally:
1. A handful representative of the dominant group (enough to confirm the shared cause — usually
   3–5, more only if the group is large and heterogeneous)
2. Every incident that looks like an outlier (there are usually few)

Call these in parallel. From each response, extract:
- Alert payload run URLs / run IDs — **and** the specific job name / workflow step within that run
  (e.g. `integration-tests/zeebe-opensearch-exporter` vs `integration-tests/camunda-exporter`).
  Two incidents can share a run URL while testing entirely different components — a shared run ID
  is a much weaker signal than a shared job/test name. See the run-ID caveat in
  `references/flood-patterns.md` before treating a shared run as enough to group on.
- Any existing investigation content — but scope a stated root cause to the incident that stated
  it. Do not extend one incident's "already tracked under issue #X" claim to a sibling that shares
  only a run ID or name prefix with it; confirm the sibling's own failure independently.

If a specific incident's alert payload is thin (e.g. "unsuccessful test cases: none captured") and
you're relying on it to justify grouping, that's a blocker, not a detail to skip past — a sibling's
confidence is not a substitute for this incident's own evidence. Before deciding where it belongs:
pull the actual GHA job log (`gh run view <run_id> --log-failed`) if you have shell access, or
invoke `/ci-incident <ID>` for that one incident to pull its full investigation context (Slack
thread, matched runbook). Scope this to the incidents that actually need it; don't run it broadly
across the flood.

Use all of this to confirm or refute the grouping hypothesis.

### Step 5 — Output the triage snapshot

Print to terminal (never post to Slack, never call any write MCP tool):

```
CI Flood Triage — [timestamp] — [N] incidents in last [M] min

PATTERN: [one sentence describing what you see]

GROUPS:
  Group 1 ([N] incidents): [pattern — e.g. "all reference runs 28799271420, 28798845890"]
    → INC-6435, INC-6436, INC-6437 … (list all)
    → Likely cause: [hypothesis]
    → Recommended action: [e.g. "merge into one, assign to @zeebe-medic, pull PR #XXXXX"]

  Outliers ([N] incidents): [why they don't fit]
    → INC-6440: [reason — e.g. "different failure signature, predates the flood window"]
    → Recommended action: [e.g. "investigate separately via /ci-incident INC-6440"]

DRAFT SLACK BROADCAST (copy-paste to #top-monorepo-ci):
---
:alert: Merge queue blocked — [N] incidents, single root cause identified.
[One sentence: what failed, why, which team owns it.]
Tracking in [main incident ref]. ICs assigned. Updates to follow.
---
```

If the pattern is genuinely unclear after steps 3–4, say so explicitly rather than guessing. List
what's known and what still needs checking.

### Step 6 — Wait for medic direction

After the snapshot, stop and wait. Do not act on anything — no merging, no status updates, no
Slack posts — until the medic explicitly asks.

If asked to merge incidents: list the exact IDs you'll merge and the target, then wait for
explicit confirmation before calling `incident_update` (or the equivalent merge write tool).

If asked to post the draft broadcast: remind the medic that this skill does not post to Slack
directly — they should copy-paste from the output above.

## Guardrails

- Never post to Slack.
- Never merge, update severity, or change status without explicit medic instruction — the snapshot
  in Step 5 is a recommendation, not an action.
- Always show draft wording and get confirmation before any `incident_update` call.
- Never assume an incident has been investigated — ask or check.
- Never group an incident into a shared-cause bucket on a shared run ID or name prefix alone, and
  never let one incident's stated root cause/dedup claim cover a sibling that hasn't been checked
  independently — especially when that sibling's own alert payload is thin. See "Correlation
  Pitfalls" in `references/flood-patterns.md`.
- If the snapshot is a partial picture (flood still growing), say so and suggest re-running in
  5–10 minutes.