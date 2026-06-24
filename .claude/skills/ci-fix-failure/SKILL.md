---
name: ci-fix-failure
description: Diagnose failing GitHub Actions runs and propose fixes. Use when given a GHA run/job URL, or asked why CI failed, why a job failed, what broke in a run, or to fix a red check on a PR.
---

# CI Job Failure Diagnosis

**Scope: diagnose + propose. Never apply, commit, or push automatically.**

## When to use

User shares a GHA URL (`actions/runs/<id>`, `actions/runs/<id>/job/<id>`, or `pull/<n>/checks?check_run_id=<id>`) and asks why it failed.

- No URL → ask. Don't guess from recent context.
- Non-`camunda/camunda` repo → tell user this skill assumes monorepo conventions; some fixes won't apply.

## Prerequisites

- `gh auth status` works against `camunda/camunda`.
- CWD is the repo root.

## Hard rules — read first

1. **NEVER increase a job/step timeout.** Timeouts are contracts. Fix the slow work, the cache, the runner size, or (one-off `pull_request` only) rerun. Per `ci.md` §flaky tests: retry **within** the timeout.
2. **NEVER propose workflow/cache/retry/sizing changes without reading and quoting [`ci.md`](../../../docs/monorepo-docs/ci.md).** Silent or contradicts → drop. Use the prescribed composite actions (`setup-maven-cache`, `setup-yarn-cache`); cache writes only from `main`/`stable*`.

## Procedure

### 1. Parse URL → `run_id` (required), `job_id` (optional), `attempt` (optional)

For check URLs, `check_run_id` **is** the job id. `/attempts/<n>` → `attempt=<n>`.

### 2. Pull run and job metadata

```bash
gh run view <run-id> --repo camunda/camunda [--attempt <n>] --json \
  status,conclusion,name,headBranch,headSha,event,workflowName,jobs,url,attempt
```

Default returns the **latest** attempt — a successful rerun masks the original failure. Always pass `--attempt <n>` if the URL had one; thread it through Step 5's `--log-failed` too.

- `in_progress` → tell user, stop.
- `success` → tell user, ask what they actually want.

### 3. Identify failing jobs and steps

From `jobs[]` where `conclusion ∈ {failure, cancelled}`, find the failing step (`steps[].conclusion`). If `job_id` given, restrict to it.

**Matrix / cascade**: find the upstream root cause first (usually `Java Checks`, `build-*`, `Unit - Back End`). Group identical step failures. Tell user the shortlist before pulling logs.

### 4. Fetch annotations (mandatory, before logs)

```bash
gh api --paginate "/repos/camunda/camunda/check-runs/<job-id>/annotations?per_page=100" \
  --jq '.[] | {level: .annotation_level, message: .message, path: .path}'
```

Watch `level: failure`:

- `"exceeded the maximum execution time of 30m0s"` → GHA per-job timeout. **Real failure, not transient.**
- `"<TestClass.method> -- Time elapsed: …s <<< ERROR!"` → the specific test that hung/errored.
- `"The operation was canceled."` → cascade noise; ignore alone.

If annotations name a test/file, often enough to skip Step 5.

### 5. Pull logs for failing step

```bash
gh run view <run-id> --repo camunda/camunda --log-failed --job <job-id>
```

Extract: the error line, ±few lines of context, the step + action that ran it.

`headSha == HEAD` (`git rev-parse HEAD`) → `Read` source files locally; skip `gh api`.

### 6. Classify

Pick **one** primary category per failing job.

- **A. Code/test** — diff broke something, or test errored/hung. Signals: compile errors, assertion failures, spotless/license, lint, **timeout naming a specific slow test**. → `references/code-test-failures.md`
- **B. Flaky test** — nondeterministic test failed. Signals: passes on rerun, timing/race, network/container in stack. Repo has a [flaky-test gate](../../../docs/monorepo-docs/flaky-test-gate.md). → `references/flaky-tests.md`
- **C. Workflow/CI config** — workflow itself is wrong. Signals: actionlint, missing secret, bad `permissions:`, invalid expression, conftest violation. → `references/workflow-config.md`. Also invoke `ci-validation` once fix is drafted.
- **D. CI Infrastructure / transient** — runner, network, image, or GitHub misbehaved. Signals: `Error waiting for runner`, image pull failure, registry 5xx, OOMKilled with no test signal. → `references/infra-transient.md`

**`cancelled` is NOT automatically D.** In this repo it's usually a per-job timeout triggered by a slow/hung test (Category A). For `push` / `merge_group`, every non-success is a real failure — no "transient" merge-queue or base-branch failures exist.

Decide by Step 4 annotations:

- timeout + names a test/step → A
- timeout + step is non-deterministic by history → B
- runner/image/infra signal, no test in picture → D
- nothing useful → default A/B over D on `push`/`merge_group`. Prefer A over B without evidence.

### 7. Find root cause

Per category:

- **A**: read failing file at `headSha`. If in diff, read the diff; else `git log -p -- <path>`. Compile/format → message names the line.
- **B**: search existing flake issue (`gh issue list --repo camunda/camunda --label kind/flake --search "<test>"`); check the flaky-test gate.
- **C**: read workflow under `.github/workflows/`. Cross-check `ci-workflow-authoring`.
- **D**: nothing to fix in code; confirm transient.

Drill until you can name the **smallest change** that makes the job pass (file, function, line, or workflow key).

### 8. Gate: read `ci.md` before any workflow/cache/retry/sizing change

This is a **gate**, not a suggestion (see Hard Rule 2). Before proposing any change to `.github/workflows/`, `.github/actions/`, caching, retries, runner sizing, or timeouts:

1. Open [docs/monorepo-docs/ci.md](../../../docs/monorepo-docs/ci.md) and read the relevant section (Caching strategy §"GitHub Actions Cache"; flake/timeout handling §flaky tests; allowed third-party actions; runner labels).
2. In the brief's **Proposed fix**, quote the specific `ci.md` rule that authorizes the change ("per `ci.md` §… `setup-yarn-cache` is the prescribed action for NPM caches").
3. If `ci.md` is silent on the case, fall back to the `ci-workflow-authoring` skill conventions and say so. If `ci.md` contradicts your proposal, drop it.

Common contradictions to watch for:
- "increase timeout" — forbidden (Hard Rule 1).
- "add `cache-from: type=gha` for Docker" — `ci.md` §caching forbids this.
- Hand-rolled cache steps when a composite action (`setup-maven-cache`, `setup-yarn-cache`) already exists.

### 9. Apply branch sensitivity (Cat D only)

| `event` / `headBranch`            | Bar          | Cat D disposition                                                            |
|-----------------------------------|--------------|------------------------------------------------------------------------------|
| `push` to `main`/`stable/*`       | **Strict**   | Hardening primary (retry, caching, mirror, sizing); rerun = unblock, not fix |
| `merge_group`                     | **Strict**   | Same                                                                         |
| `pull_request`                    | Looser       | Rerun fine for one-off; harden only if recurring                             |
| `schedule` / `workflow_dispatch`  | Per-workflow | Harden if `# owner:` is set; otherwise rerun                                 |

A/B/C: branch only changes the brief framing.

On strict-bar branches, **never close with "rerun" alone** — name the smallest hardening change in `.github/workflows/` or `.github/actions/` as primary fix.

### 10. Brief

```
**Run**: <workflow> · <branch> · <event> · <url>
**Failed jobs**: <n> (<grouped if matrix>)
**Category**: A | B | C | D
**Reliability bar**: strict (main/stable/*/merge_group) | looser (pull_request)

**Root cause**
<1–3 sentences, specific file/line/condition>

**Proposed fix**
<concrete edit + path; for D on strict bar, the hardening is the fix, rerun is the unblock>

**Verification**
<exact local command — usually module-scoped `./mvnw`, or a workflow re-run>
```

Don't cite reference files in the brief. **Stop.** Apply only after user confirms; re-run verification; report.

## Guardrails

- **Stop after brief.** No edits without user go-ahead.
- **No silent reruns.** Recommend `gh run rerun --failed <run-id> --repo camunda/camunda`; don't trigger.
- **Don't blame the diff blindly** — verify the failing code is reachable from the changeset.
- **Format/build via repo conventions** (`./mvnw license:format spotless:apply -T1C` + module-scoped command from `AGENTS.md`). Never full-repo builds.
- **Backports** — for bug fixes that may need `stable/*`, ask the user per `AGENTS.md`.
- **Never disable/skip/@Disabled a flaky test.** File `kind/flake` issue via `create-issue`, assign engineer, leave test enabled.

## Compose with

- `ci-validation` — after any workflow edit, pre-commit.
- `ci-workflow-authoring` — for non-trivial workflow changes.
- `create-issue` — to file a flake issue.
- `ci-incident` — escalate if part of a declared incident, not a single job.
