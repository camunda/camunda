---
name: ci-fix-failure
description: Diagnose a failing GitHub Actions run in camunda/camunda from a run or job URL - fetch jobs and logs via gh CLI, classify the failure (code/test, flake, workflow config, infra), find the root cause, and propose a concrete fix. Use whenever the user shares a GitHub Actions run URL, job URL, or asks you to look at "why CI failed", "why this job failed", "what broke in this run", or to fix a red check on a PR. Diagnose-and-propose only - get user confirmation before applying or pushing fixes.
---

# CI Job Failure Diagnosis

Given a GitHub Actions run or job URL, walk an unsuccessful (failed, cancelled) run down to the root cause and propose a fix.
Scope is **diagnose + propose** — never apply, commit, or push automatically.

## When to use

The user shares any of:

- A run URL: `https://github.com/camunda/camunda/actions/runs/<run-id>`
- A job URL: `https://github.com/camunda/camunda/actions/runs/<run-id>/job/<job-id>`
- A check URL on a PR: `https://github.com/camunda/camunda/pull/<n>/checks?check_run_id=<id>`

…and asks why it failed, or to fix it.

If no URL is given, ask for one — do not guess from recent history.

## Prerequisites

- `gh` CLI authenticated against `camunda/camunda`. Verify with `gh auth status`.
- Working directory should be the repo root so any proposed fix lands in the right tree.

## Procedure

### Step 1 — Parse the URL

Extract `run_id` (required) and `job_id` (optional). For check URLs, the `check_run_id` query
param **is** the job id.

If the URL is for a different repo than `camunda/camunda`, inform the user that for some fixes
this skill assumes the monorepo's conventions.

### Step 2 — Pull run and job metadata

```bash
gh run view <run-id> --repo camunda/camunda --json \
  status,conclusion,name,headBranch,headSha,event,workflowName,workflowDatabaseId,url,jobs,displayTitle,createdAt
```

Note: `name` and `workflowName`, `headBranch`, `event` (push/pull_request/schedule/workflow_dispatch),
`headSha`, and the list of jobs with their conclusions.

If the run is still `in_progress`, tell the user and stop — wait for it to finish.

If the run is `success`, tell the user the run passed and ask what they want to look at instead.

### Step 3 — Identify failing jobs and steps

From the `jobs` array, list every job where `conclusion` is `failure` or `cancelled`. For each,
find the failing **step** by name from `steps[].conclusion`.

If a `job_id` was given, restrict to that one job.

For runs with many failing jobs (e.g. matrix builds), **find the upstream root cause first**.
Most cascade failures share a single foundational job — usually `Java Checks`, a `build-*`
composite action, or a `Unit - Back End` job. Diagnose that one first; the rest collapse to "we
couldn't build / test because the foundation broke." Group identical step failures rather than
treating each matrix shard as distinct.

Tell the user the shortlist before pulling logs (titles + which step failed + the candidate
upstream job).

### Step 4 — Fetch annotations (mandatory)

GHA annotations are where the most useful failure signal lives — timeout messages, the test that
hung, the specific assertion. **Always pull annotations before pulling logs**:

```bash
gh api --paginate "/repos/camunda/camunda/check-runs/<job-id>/annotations?per_page=100" \
  --jq '.[] | {level: .annotation_level, message: .message, path: .path}'
```

Look for `level: failure` entries. Common shapes:

- `"The job has exceeded the maximum execution time of 30m0s"` → GHA per-job timeout. The job
  was killed by GitHub. **This is a real failure, not transient** (see Step 5).
- `"<TestClass.method> -- Time elapsed: 121.7 s <<< ERROR!"` → the specific test that errored or
  hung (often appears alongside a timeout annotation, naming the cause).
- `"The operation was canceled."` → noise from cascade; ignore on its own.

If annotations name a specific test or file, you often have enough to diagnose without pulling
full logs.

### Step 5 — Pull logs for the failing step

```bash
gh run view <run-id> --repo camunda/camunda --log-failed --job <job-id>
```

`--log-failed` only returns failing step output, which is what you want. For each failing job,
extract:

- The actual error line(s) — exception, assertion message, exit code, missing file
- A few lines of surrounding context
- The step name and the action/script that ran it

If the log is unhelpful (e.g. cancelled job, process killed with no stack), the annotations
from Step 4 are your primary signal. For deeper context, grab the full step log via `--log`
(without `--failed`) and look earlier.

When the run's `headSha` matches your local working tree's `HEAD` (check with
`git rev-parse HEAD`), just `Read` source files directly — much faster than `gh api`.

### Step 6 — Classify the failure

Pick **one** primary category. If multiple jobs fail with different root causes, classify each in parallel subagents.

**A. Code / test failure** — the change under test is broken, or a test errored/hung.
Signals: Java compile errors, JUnit/AssertJ assertion failures, spotless/license violations,
TypeScript/ESLint errors, Vitest/Playwright failures, **a GHA per-job timeout that names a
specific slow/hung test in annotations**.
Next: see `references/code-test-failures.md`.

**B. Flaky test** — a nondeterministic test failed.
Signals: failure unrelated to the diff, passes on rerun, timing/awaitility/race-y assertion,
network or container startup in the stack. The repo has a flaky-test gate
([docs/monorepo-docs/flaky-test-gate.md](../../../docs/monorepo-docs/flaky-test-gate.md)).
Next: see `references/flaky-tests.md`.

**C. Workflow / CI config error** — the workflow itself is wrong.
Signals: actionlint-style errors, missing secret, bad `permissions:`, invalid expression, action
not found, conftest/policy violation, runner label typo.
Next: see `references/workflow-config.md`. Also invoke the `ci-validation` skill once a fix is
drafted.

**D. CI Infrastructure / transient** — runner, network, image, or GitHub itself misbehaved.
Signals: `Error waiting for runner`, image pull failure, 5xx from a registry, OOMKilled with no
test signal, GitHub status-page incident.
Next: see `references/infra-transient.md`.

**Important — handling `cancelled` jobs:**

A `cancelled` conclusion is **not** automatically Category D. In this repo, cancellation is
usually a GHA per-job timeout, and the timeout was triggered by a slow or hung test or step.
That's a real failure (Category A) and rerunning won't fix it.

In particular, **for `push` and `merge_group` runs every non-success is a real failure** — there is no such
thing as a "transient" merge-queue or base branch failure here. Every dequeue costs the engineer a re-queue and
blocks downstream merges.

When a job is `cancelled`, the Step 4 annotations decide the category:

- Annotation names a timeout AND names a specific test/step → **Category A** (the test/step is
  the root cause).
- Annotation names a timeout but the offending step is non-deterministic per its history →
  **Category B**.
- Annotation names a runner shutdown, image pull failure, or similar infra signal with no
  test/step in the picture → **Category D**.
- No useful annotations at all → fall back to logs and treat with skepticism; default toward
  A/B over D for `push` and `merge_group` runs.

If you are uncertain between A and B, prefer A — never call something "flaky" without evidence.

### Step 7 — Find the root cause

Following the right reference file, dig in:

- For **A**: read the failing source/test file at `headSha`. If it's a test in the diff, read the
  diff. If it's an unchanged test, blame the most recent change to it (`git log -p -- <path>`).
  For compile/format errors, the error message names the line.
- For **B**: search for an existing flake issue
  (`gh issue list --repo camunda/camunda --label kind/flake --search "<test name>"`). Check the
  flaky-test gate doc.
- For **C**: read the workflow file under `.github/workflows/`. Cross-check with the
  `ci-workflow-authoring` skill conventions.
- For **D**: nothing to fix in code — confirm transient, recommend rerun.

Keep digging until you can name the **smallest change** that would make the job pass — a specific
file, function, line, or workflow key.

### Step 8 — Consult `ci.md` before proposing any improvement

Before proposing **any** workflow change, caching change, retry, or sizing change — even a
one-liner — read the relevant section of
[docs/monorepo-docs/ci.md](../../../docs/monorepo-docs/ci.md). It is the source of truth for
CI best-practices in this repo, including:

- **GHA caching strategy** (§ "GitHub Actions Cache"): which dependency types cache (Maven, NPM,
  Go, Docker), which branches may write to the cache, prescribed actions to use.
- **Flake handling** (§ flaky tests): how retries-within-timeout and the CI health metrics flow
  works.
- **Allowed third-party actions** and pinning rules.

If `ci.md` is silent on the specific case, fall back to `ci-workflow-authoring` for general
conventions. Never invent a hardening pattern that contradicts `ci.md`.

### Step 9 — Apply branch-sensitivity to the fix

The same root cause has a different "right fix" depending on which branch the run is on. Use this
table to shape the proposed fix:

| `event` / `headBranch`             | Reliability bar | Right disposition for **transients** (Cat D) |
|-----------------------------------|-----------------|----------------------------------------------|
| `push` to `main` or `stable/*`    | **Strict — must always succeed** | Hardening is the primary fix (automated step retry, caching, mirror, sizing). Rerun is an immediate unblock only, not the fix. |
| `merge_group`                     | **Strict** — every non-success blocks the queue and costs a re-queue | Same as above: hardening primary, rerun secondary. |
| `pull_request` (feature branch)   | Looser — author can re-trigger | Rerun is acceptable for a one-off transient. Only propose hardening if the signal recurs. |
| `schedule` / `workflow_dispatch`  | Workflow-specific — read the workflow's owner annotation | Default to hardening if the workflow is owned (`# owner:` in the YAML); otherwise rerun. |

For Category A/B/C (real test, flake, workflow-config), branch doesn't change the diagnosis —
only the framing of the brief. For Category D, branch flips the disposition.

When the branch is `main`, `stable/*`, or the event is `push`, `schedule` or `merge_group`, never close out the brief
with just "rerun." Propose the smallest concrete hardening change in `.github/workflows/` (or the
relevant composite action under `.github/actions/`) as the **primary** fix.

### Step 10 — Brief the user

Reply in this exact shape:

```
**Run**: <workflow name> · <branch> · <event> · <run url>
**Failed jobs**: <count> (<grouped summary if matrix>)
**Category**: A | B | C | D
**Reliability bar**: strict (main / stable/* / merge_group) | looser (pull_request)

**Root cause**
<1–3 sentences naming the specific file/line/condition>

**Proposed fix**
<concrete edit, command, or action — file path + what to change>
<for Category D on strict-bar branches: name the hardening (retry action, cache, mirror) as the
 primary fix; rerun is only the immediate unblock>

**Verification**
<the exact local command to confirm the fix — usually a module-scoped `./mvnw` or a workflow
re-run command>
```

Do not cite the skill's own reference files in the brief (e.g. "per `infra-transient.md`…") —
just give the answer. The reference files are working notes for you, not the user.

Then **stop**. Do not edit files until the user confirms. If the user confirms, apply the fix,
re-run the verification command, and report results.

## Guardrails

- **Diagnose-then-stop.** Apply fixes only after the user's explicit go-ahead.
- **No silent reruns.** If you think it's a flake, say so and recommend `gh run rerun --failed
  <run-id> --repo camunda/camunda` — don't trigger it yourself.
- **Don't blame the diff blindly.** Verify the failing assertion/code is reachable from the
  changeset before claiming "your PR broke this".
- **Honor repo conventions.** Format/build via `./mvnw license:format spotless:apply -T1C` then
  the module-scoped command from `AGENTS.md` — never recommend full-repo builds.
- **Backports.** For bug fixes that need to land on `stable/*`, ask the user about backports per
  `AGENTS.md` PR conventions — don't auto-tag.
- **Flake handling.** Never disable, skip, or `@Disabled` a test. The repo policy is: file an
  issue with `kind/flake` (via the `create-issue` skill), assign to the engineer, leave the test
  enabled. If a flake gate auto-disabled it, that's the gate's job — not yours.

## Compose with other skills

- `ci-validation` — run after any workflow file edit before committing.
- `ci-workflow-authoring` — consult when proposing non-trivial workflow changes.
- `create-issue` — use to file a flaky-test issue.
- `ci-incident` — escalate to this if the failure is part of a declared incident, not a single job.
