---
name: quality-board-check
description: Scan the quality board for urgency:immediate bugs, research ownership and merge correlation, and report via Slack. Use when asked to check the quality board, find immediate-urgency bugs, or run the quality board routine.
---

# Quality Board — Urgency: Immediate Check

Scan the Camunda quality board for open bugs with **urgency: immediate**, summarize each
one, research ownership and correlation to recent merges, then report results via Slack.

---

## Urgency scoring — how it works

The `Urgency` field on the quality board is a computed org-level GitHub Projects v2 field.
It is **not a label**; it cannot be searched directly. It is derived automatically from
the issue's `severity/*` and `likelihood/*` labels by the workflow at
`.github/workflows/assign-urgency-to-issue.yml`.

Scoring matrix (only this combination produces **immediate**):

| Severity label    | Likelihood label | → Urgency    |
|-------------------|------------------|--------------|
| `severity/critical` | _any_          | **immediate** |
| `severity/high`   | `likelihood/high` | **immediate** |
| `severity/high`   | `likelihood/medium` or lower | next |
| `severity/mid` or lower | _any_    | planned / someday |

**Consequence for search efficiency:** you only need two GitHub issue searches to find
all immediate-urgency bugs in `camunda/camunda`:

```
label:kind/bug label:severity/critical is:open
label:kind/bug label:severity/high label:likelihood/high is:open
```

Both return a `field_values` array in the response confirming the computed `Urgency`
value. Cross-check it to catch any manual overrides or lag in automation.

For cross-repo bugs (web-modeler, connectors, helm, etc.) on project #187, run the same
two label searches scoped to those repos individually, or use
`project:camunda/187` as an additional filter.

---

## Execution steps

### 1 — Fetch immediate-urgency bugs (parallel)

Run both searches at the same time:

```
mcp__github__search_issues:
  query: "repo:camunda/camunda is:issue is:open label:kind/bug label:severity/critical"
  perPage: 50  sort: created  order: desc

mcp__github__search_issues:
  query: "repo:camunda/camunda is:issue is:open label:kind/bug label:severity/high label:likelihood/high"
  perPage: 50  sort: created  order: desc
```

Keep only items where `field_values[].field == "Urgency" && value == "immediate"`.
Deduplicate by issue number across both result sets.

### 2 — Per-bug deep-dive (parallel across all found bugs)

For each immediate-urgency bug, fetch concurrently:

- **Issue comments** — `mcp__github__issue_read` method `get_comments` — to find
  current status, linked PRs, discussions, and any stated workaround.
- **CODEOWNERS** — look up the component label against `/CODEOWNERS` to identify the
  owning team. Component-to-path hints:

  | component label          | path pattern to look up in CODEOWNERS |
  |--------------------------|---------------------------------------|
  | component/zeebe          | `zeebe/`                              |
  | component/data-layer     | `zeebe/exporter*`, `zeebe/stream*`    |
  | component/operate        | `operate/`                            |
  | component/tasklist       | `tasklist/`                           |
  | component/identity       | `identity/`                           |
  | component/management-identity | `identity/` (old Identity UI)    |
  | component/optimize       | `optimize/`                           |
  | component/c8run          | `c8run/`                              |

- **Open fix PR** — search for `repo:camunda/camunda is:open closes:#<N>` or look for
  a PR with the issue number in the title/branch name.

### 3 — Correlate to recent merges

For each bug, determine whether a recent merge plausibly caused or surfaced it.

```
mcp__github__list_commits:
  owner: camunda  repo: camunda  sha: main  perPage: 30
```

Compare the issue's `created_at` date against the commit list. Flag any commit merged
**within 30 days before the issue was opened** that touches the same component paths
as the bug. Also run:

```
mcp__github__search_pull_requests:
  query: "repo:camunda/camunda is:merged <relevant keywords from bug title>"
```

Look specifically for:
- PRs merged in the 4 weeks before the issue opened
- PRs touching the same file paths as the bug's component
- Backport labels on those PRs that may indicate a regression window

If no merge is found, state "no correlated merge found; likely a long-standing missing
guard / feature gap".

---

## Output format

### Main Slack message (brief — fits in one screen)

Post to the appropriate Slack channel (e.g. `#quality-board-alerts` or as directed).
Keep it to ≤ 8 lines. Use this template:

```
*Quality Board — Urgency: Immediate* | <today's date>
<board URL>

Found <N> open bug(s) with urgency: immediate:

• #<number> — <title> | owner: @<github-handle> | <open|fix-in-progress>
• #<number> — <title> | owner: @<github-handle> | <open|fix-in-progress>

<If N=0>: ✅ No immediate-urgency bugs open.

Full details in thread ↓
```

### Thread reply per bug (one reply each)

Post one thread reply for each bug with the following sections — keep each section
to 3–5 bullet points maximum:

```
*#<number>: <title>*
<GitHub URL>

*What's happening*
<2–3 sentences: impact, affected versions, any workaround>

*Root cause*
<1–3 bullets: technical explanation, where the failure is triggered>

*Ownership*
• Assignee: @<login> (<team from CODEOWNERS>)
• CODEOWNERS team: <team name from /CODEOWNERS>
• Last active: <date of most recent comment>

*Fix status*
• <open / no fix started / fix PR #NNNN (draft|open|merged)>
• Backport planned: <yes (stable/X.Y) / no / unknown>

*Correlation to recent merges*
• <PR #NNNN merged YYYY-MM-DD — <why it's related> — OR "No correlated merge found">
```

---

## Notification

After posting to Slack, send a `PushNotification` to the session owner with:

```
<routine_summary>
Quality Board: <N> urgency:immediate bug(s). <one-line summary of each>.
Fix PR open: <yes/no>. Details posted to Slack.
</routine_summary>
```

If N=0, send no notification (silence = healthy).

---

## What NOT to do

- Do not fetch all `kind/bug` issues (hundreds) and filter client-side — use the
  targeted label searches above.
- Do not re-derive the urgency scoring from scratch — trust the label → urgency matrix
  documented above.
- Do not read the entire CODEOWNERS file into context — grep for the relevant path only.
- Do not post to Slack if there are zero immediate-urgency bugs — silence is correct.
