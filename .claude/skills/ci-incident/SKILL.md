---
name: ci-incident
description: Drive response for a CI incident in camunda/camunda by combining incident.io state, Slack channel history, and the repo CI runbooks and incident process docs. Use when asked to resolve a CI incident, respond to an incident, or drive an incident by ID. Full incidents only, not alerts.
---

# CI Incident Response

Guides a responder through a CI incident in `camunda/camunda`. Pulls live state from incident.io,
context from the incident's Slack channel, the matching runbook from
[docs/monorepo-docs/ci-runbooks.md](../../../docs/monorepo-docs/ci-runbooks.md), and the process
from [docs/monorepo-docs/processes.md](../../../docs/monorepo-docs/processes.md).

## Prerequisites

- **incident.io MCP** — required. Verify the `mcp__incident-io__incident_show` tool is available.
  If not, tell the user to [configure the incident.io MCP server](https://docs.incident.io/ai/remote-mcp) and stop.
- **Slack MCP** — preferred but optional. If `mcp__*__slack_read_channel` is not available, warn
  the user that Slack context will be skipped and continue.

Throughout this skill, MCP tools are referenced by their short verb (`incident_show`,
`slack_read_channel`); the actual identifier depends on which MCP server is configured.

## Scope

Full incidents only. If the user passes an alert ID (or anything that isn't an incident),
stop and ask for the incident ID.

## Procedure

### Step 1 — Parse the incident ID

The incident ID comes from `$ARGUMENTS`. If empty, ask the user for it before proceeding. Do not
guess from recent context.

### Step 2 — Pull incident state

Call `incident_show` with the ID and `include: ["investigation", "postmortem", "roles", "custom_fields"]`.

Extract and remember:

- Title, severity, status, opened-at
- Role assignments (lead, comms, etc.)
- Active alert name(s)
- Incident's Slack channel reference (channel ID or name)
- Any existing investigation / postmortem content

### Step 3 — Pull Slack context

If the Slack MCP is available, read recent messages from the incident's Slack channel
(`slack_read_channel`, ~50 most recent). Summarize for yourself:

- What has already been tried
- Who is actively engaged
- Timestamp of the last status update
- Any blockers or open questions raised

If the Slack MCP is not available, note this in the brief and continue.

### Step 4 — Match a runbook

Read [docs/monorepo-docs/ci-runbooks.md](../../../docs/monorepo-docs/ci-runbooks.md).

Match the incident title and active alert name(s) against the `###` headings under both
**Alert Runbooks** and **Incident Runbooks** sections. Fuzzy match is fine (e.g. alert
"Merge Queue High Failure Rate" → runbook heading of the same name).

- **One match**: load its Troubleshooting and Solutions subsections.
- **Multiple matches**: list them and ask the user which applies.
- **No match**: list the available runbook headings and ask which applies, or confirm this is a
  novel incident with no runbook.

### Step 5 — Locate the current process step

Read the **CI Incident Management** section of
[docs/monorepo-docs/processes.md](../../../docs/monorepo-docs/processes.md).

Based on incident status + Slack history, determine which step the incident is in:

1. Identification
2. Response
3. Follow-Up

This tells you what action is expected next (e.g. assign roles, post update, create follow-ups).

### Step 6 — Brief the responder

In a single concise message, give the responder:

- **State**: title, severity, status, lead, opened-at
- **Slack TL;DR**: what's been tried, who's active, last update time
- **Runbook**: matched heading + the Troubleshooting steps
- **Process step**: which of the three steps the incident is in, and what's expected
- **Next action**: one concrete recommendation

Then wait for the responder to direct the next move.

### Step 7 — Drive the response

Work through the runbook with the responder. Prioritize mitigation (stopping the bleeding) over root causing and resolution. Use relevant CI skills from this repository, e.g. for troubleshooting. Specific rules:

- **Status updates**: always use `incident_update` from the incident.io MCP. Do not post directly
  to the incident's Slack channel — incident.io syndicates updates to Slack.
- **Slack MCP usage**: read-only by default. Only post via Slack MCP if the responder explicitly
  asks to message a channel *other than* the incident channel (e.g. `#top-monorepo-ci`).
- **Confirm wording**: never send an `incident_update` without showing the draft to the responder
  and getting explicit confirmation.
- **Don't change state silently**: never change severity, status, or role assignments without the
  responder explicitly instructing it.

### Step 8 — Wrap-up

When the incident moves to resolved or closed:

- Re-read the **Follow-Up** subsection of `processes.md`.
- Prompt the responder to create follow-ups via `follow_up_create` for each action item raised
  during response (postmortem tasks, runbook updates, fixes deferred during the incident).
- Offer to draft each follow-up's title and description from the Slack history and runbook gaps.

## Guardrails

- Do not invent runbook content. If no runbook matches, say so.
- Do not assume the responder has tried something just because it's in the runbook — ask.
- If the runbook references commands or links that no longer exist in the repo, flag it as a
  follow-up rather than improvising.
