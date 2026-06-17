# Category D — Infra / Transient Failures

Genuine infra/transient failures are rare in this repo and the bar for classifying as D is
**high**. Nothing in the diff or workflow is wrong, and the annotations (Step 4 of SKILL.md) name
an infra signal — not a test, step, or timeout caused by code.

## Branch sensitivity decides the fix

Classifying as D does **not** mean "recommend rerun and stop." The right disposition depends on
the branch the run is on:

### Strict-bar branches: `main`, `stable/*`, and any `schedule` and `merge_group` run

These must always succeed. Transient failures on these branches are not acceptable — the response
is **hardening the workflow** so the same class of failure can't break the branch again. Rerun is
an immediate unblock only, not the fix.

Propose the smallest concrete hardening change. **Always cross-check the proposal against**
[docs/monorepo-docs/ci.md](../../../../docs/monorepo-docs/ci.md) — it defines the prescribed
actions and the caching policy for this repo. Common shapes:

- **Network-touching steps** (artifact upload to Nexus, package install, image pull, registry
  fetch): wrap in `nick-fields/retry@<sha>` (allowlisted in `ci.md`). Set 2–3 attempts with
  backoff. Pin to a SHA per `ci-security-compliance`. Many actions also expose a built-in `retry`
  input — prefer that over wrapping when available.
- **Slow `npm` / `yarn` install**: use the
  [`setup-yarn-cache`](https://github.com/camunda/infra-global-github-actions/tree/main/setup-yarn-cache)
  action. **Do not** use `actions/setup-node`'s `cache: 'npm'` field — that is not the repo's
  prescribed pattern. Per `ci.md`, NPM/Yarn caches are written only on `main` / `stable*` builds.
- **Slow Maven**: use the local
  [`setup-maven-cache`](../../../../.github/actions/setup-maven-cache) action. Same write rules
  apply.
- **Docker builds**: do **not** add `cache-from: type=gha` / `cache-to: type=gha` to
  `docker/build-push-action` — `ci.md` explicitly forbids it.
- **External registry / dependency that has caused outages**: configure a mirror or fallback URL
  if one exists, or pin to a known-good version. Coordinate with monorepo-devops if the change
  touches `Artifactory` / `DockerHub` integration.
- **Right-sizing / Splitting** (runner class): only justified when the timeout is the
  actual constraint *and* the slowdown is intrinsic. Don't bump timeouts. Use
  `ci-runner-utilization` for sizing decisions.

Run the `ci-validation` skill after any workflow edit. Consult `ci-workflow-authoring` for
conventions and `ci-security-compliance` for action pinning.

### Looser-bar branches: `pull_request` (feature branches)

The PR author can re-trigger and the cost of one flaky failure is bounded to their PR. Rerun is
acceptable for a one-off transient:

```
gh run rerun --failed <run-id> --repo camunda/camunda
```

Only propose hardening if:

- The signal has recurred across runs (check `gh run list --workflow <name> --branch <branch>`).
- The same root cause is also hitting `main` / `stable/*` / `schedule` / `merge_group`.

## Cancellations are NOT automatically transient

`cancelled` is almost always a real failure here since we set high timeouts:

- **GHA per-job timeout** → the job ran longer than its `timeout-minutes` and was killed.
  Annotation: `The job has exceeded the maximum execution time of <N>m<S>s`. This is Category A
  (a slow/hung test or step is the root cause), **not** D.
- **Merge-queue dequeue or cancellation** → blocks merges and costs a re-queue. Still a real
  failure on a strict-bar context.

## Genuine Category D shapes

Use this category only when the annotations / log lines clearly point at infra:

- `Error waiting for runner to pick up the job` (and the runner pool is known to have capacity)
- `failed to pull image ...: 5xx` from a registry
- HTTP 5xx from Nexus / artifact registry / package mirror during upload or download
- `connection reset` or DNS failure mid-build, with no test signal
- `The runner has received a shutdown signal` from the host platform
- A GitHub status-page incident that overlapped the run window (GitHub announces incidents late sometimes, recheck)

`OOMKilled` deserves care: if it killed the JVM running tests, that's usually Category A (memory
leak or oversized fixture in code under test). Only treat OOM as D if it killed an unrelated
process (e.g. the runner agent itself).

## Don't

- Don't close out a `main` / `stable/*` / `schedule` / `merge_group` brief with just "rerun." Propose
  hardening as the primary fix.
- Don't claim "GitHub was down" without a corroborating reference — say "looks transient,
  recommend hardening X" instead.
- Don't escalate to an incident from a single job failure. The `ci-incident` skill is for
  declared incidents.
- Don't reflexively recommend `gh run rerun` for cancellations. Most cancellations are
  timeouts — rerunning won't fix a slow test.
- Don't bump `timeout-minutes` or runner class to mask a flake — that hides real cost
  (`ci-runner-utilization` exists for sizing decisions).
