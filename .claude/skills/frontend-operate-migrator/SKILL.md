---
name: frontend-operate-migrator
description: Use when porting an Operate page or component from the legacy client at operate/client/ to the Operate pod in the orchestration cluster webapp, including end-to-end execution from a migration ticket number. Covers the page inventory, MobX decomposition, behavior fidelity, and the ticket-to-draft-PR loop.
disable-model-invocation: true
---

# Operate Migration

The entry point for porting a page from `operate/client/` to
`webapp/client/apps/orchestration-cluster-webapp/src/operate/`.

**Conventions for both codebases — routing, data fetching, state, styling, testing, forms — live in
[operate-frontend](../operate-frontend/SKILL.md). Read it first.** This skill covers only what is
specific to the act of migrating: the page inventory, store decomposition, fidelity proof, and the
ticket-driven loop. It is deleted when the migration completes.

## Pages to migrate

Page list, status, and per-PR breakdown live in GitHub:
[camunda/experience-pdp#32](https://github.com/camunda/experience-pdp/issues/32) → page → coherent
implementation issue. Maximum depth is **2 below the epic** (epic = 0, page = 1, implementation = 2).
Cross-cutting implementation issues may sit directly under the epic. Do not add intermediate feature
trackers or children beneath an implementation issue.
[#51305](https://github.com/camunda/camunda/issues/51305) tracks broader cross-component
dependencies. Source path, target route, and fidelity scope live in the tickets — query live rather
than caching status here.

What already exists is whatever is in
`webapp/client/apps/orchestration-cluster-webapp/src/operate/pages/` and
`src/routes/_carbon/_auth/operate/` — read those directories before adding or splitting routes, and
do not recreate a shell from a historical ticket.

## MobX store decomposition

Operate has ~20 legacy stores. Most are transient UI state and do not need porting. Map each one:

| Store | What it holds | Target |
|-------|--------------|--------|
| `authentication.ts` | Session | Already in `#/shared/auth/` — reuse |
| `currentTheme.ts` | Theme preference | Already in `#/shared/theme/` — reuse |
| `notifications.tsx` | Toast queue | `#/shared/notifications/notifications.store` — reuse |
| `variableFilter.ts` | Filter inputs on Processes | URL search params via `validateSearch` |
| `incidentsPanelFiltersStore.ts` | Filter inputs on Incidents tab | URL search params |
| `instancesSelection.ts` | Selected rows | `useState` in the page component |
| `panelStates.ts` | Panel open/collapsed | `useState` |
| `dateRangePopover.ts` | Calendar open/close | `useState` |
| `executionCountToggle.ts` | Toggle state | `useState` |
| `batchModification.ts` | Batch operation in progress | `useState` |
| `diagramOverlays.ts` | Diagram overlay data | `useState` in the BPMN component |
| `processInstanceMigration.ts` | Migration wizard state | `useState` + URL params for the step |
| `modifications.ts` | Pending variable modifications | `useState` + local reducer — or keep MobX if genuinely complex |
| `networkReconnectionHandler.ts` | Connectivity polling | Standalone hook with `useEffect` |

## Ticket-driven execution contract

An explicit `/frontend-operate-migrator <ticket-number>` request means: execute the PR-sized
migration end to end and return only when its draft PR has converged. This applies to an execution
request, not to analysis or planning.

Unless the user narrows them, that request authorizes local edits, branch creation, commits, pushes,
draft PR creation, reviewer requests, and handling review threads. It does **not** authorize marking
the PR ready, merging it, or changing unrelated code. Later instructions replace the relevant
permission; never carry permissions over from another invocation.

Before editing:

1. Read the ticket and traverse its live parent/subissue hierarchy through every level, exhausting
   pagination. If it is a tracking parent, select a clearly identified, unblocked implementation
   issue directly beneath the page. Check every native `blockedBy` prerequisite and any explicit
   product or deployment approval gate. Follow the surviving issue's complete scope, not an obsolete
   split plan — superseded specifications are archived through links, not child relationships.
2. Inspect linked PRs and branches so work is not duplicated.
3. Build an acceptance matrix from the ticket **and the legacy implementation**. Cover observable
   branches and effects, API calls, URL state, loading/empty/error/forbidden states, permissions,
   tenancy, and accessibility. Record intentional omissions such as tracking.
4. Plan one coherent behavior change per PR, up to **1500 total additions + deletions** including
   tests and locales. Keep tightly coupled UI, data loading, mutations, recovery and coverage
   together. The limit is a ceiling, not a target: don't pad small changes or bundle unrelated work.
   Split only when the complete change exceeds the cap or has an independently useful boundary, and
   record sibling implementation issues directly under the page.

Dependency edges describe real required contracts or acceptance gates, not chronology. Don't block on
already-delivered prerequisites, superseded trackers, ancestors, or redundant transitive
prerequisites. Keep independent work parallel, and give shared state/selection/host contracts one
explicit owner.

**State lives in GitHub — never cache issue or PR state in a file.** Query it:
`gh issue view 32 --repo camunda/experience-pdp --json title,body,state`,
`gh api --paginate repos/camunda/experience-pdp/issues/32/sub_issues`,
`gh issue view <n> --repo <owner/repo> --json title,body,state`,
`gh api --paginate repos/<owner/repo>/issues/<n>/sub_issues`,
`gh pr list --repo camunda/camunda --search "<page>"`. Follow each child's own repository URL rather
than assuming the parent's. GitHub's child-completion percentage counts closed issues including
not-planned ones; it is not evidence that implementation shipped.

## The migration loop

Run [operate-engineering-loop](../operate-engineering-loop/SKILL.md), adding the migration-only
fidelity check and review below after its edit tier and before its component tier.

### Independent behavior-fidelity review (migration-only)

After the edit tier is green, spawn a fresh read-only frontend reviewer. Give it the ticket,
acceptance matrix, exact legacy source, migrated source, and diff. Its only task is an
evidence-backed `legacy → migrated` list of added, removed or changed observable behavior, plus any
shared logic copied instead of reused. Tracking-only differences are ignored.

The implementing agent adjudicates every finding: fix a valid discrepancy with the smallest change
then rerun the affected tier; reject an invalid one only with concrete code or test evidence; stop
with a precise blocker when product intent is genuinely ambiguous. Never invent behavior to make the
review pass. Repeat until no unexplained observable difference remains.

## Fidelity checks (the 1:1 oracle)

Run **after the edit tier, scoped to the just-ported component** — not across the whole `operate/`
directory, or you will flag not-yet-ported features.

**Deterministic (script, always trusted).** Run from the **repo root** — the script and its default
locales path are repo-root relative, not `webapp/client`:

```bash
node .claude/skills/frontend-operate-migrator/scripts/fidelity.mjs --ported <ported-component-dir>
```

It checks locale coverage: every `t('operate.*')` key exists in en/de/fr/es. Non-zero exit is a gate
failure — fix before continuing.

**Judgment (LLM flagger — flag, never approve).** The two checks a script cannot make:

1. **No inlined shared logic.** For each shared hook/util/type the legacy component imports, find the
   target shared equivalent and reuse it. If none exists, port it once to the appropriate shared
   owner rather than making per-consumer copies.
2. **1:1 behavior.** Walk the legacy component's branches and effects and list any observable
   behavior the port adds, drops or alters.

A script saying "key X missing from de.json" is trusted; an LLM saying "looks faithful" is not. The
flagger produces evidence; the implementing agent fixes clear discrepancies and escalates only
genuine product ambiguity.

## PR conventions

- Assign the team reviewer on every PR.
- Note in the description any feature still being built in legacy Operate that must be mirrored.
- Commit message: `feat: migrate Operate <PageName> page to unified app`.
- A partial PR must not close a parent or an issue whose remaining panels are deferred.
