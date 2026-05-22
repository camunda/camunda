# C8 Orchestration Cluster E2E Tests — Release workflow

Reference for [`c8-orchestration-cluster-e2e-tests-release.yml`](./c8-orchestration-cluster-e2e-tests-release.yml).

## Purpose

Run the C8 Orchestration Cluster API tests and E2E tests (Operate, Tasklist, Identity) against a **specific released or in-flight version** of the monorepo. Triggered manually via `workflow_dispatch`.

## Inputs

| Input | Required | Type | Notes |
| --- | --- | --- | --- |
| `monorepo_release` | yes | string | e.g. `8.8.0`, `8.8.0-alpha8`, `8.8.0-alpha8-rc1`. Matched against the tag and the conventional release branch name. |

## What gets pinned to the version

| Thing | Source |
| --- | --- |
| Docker image | `camunda/camunda:<monorepo_release>` (substituted into `docker-compose.yml` at runtime). |
| Test suite source code (Playwright tests, page objects, BPMN resources) | The resolved `ref` — see below. |
| Camunda built from source for the RDBMS jobs | Same resolved `ref`. |

## Source ref resolution (`validate-release` job)

Two outputs are produced:

- `base` — `stable/x.y` or `main`. Drives **routing only**: which test directories to run, NON_STANDALONE for 8.6/8.7, the V1/V2 matrix, RDBMS-job gating, artifact naming. Not used for any `actions/checkout`.
- `ref` — **fully-qualified** git ref (`refs/heads/...` or `refs/tags/...`) used by every source-code checkout in the workflow.

`ref` resolution order:

1. **`refs/heads/release-<monorepo_release>`** — preferred when present.
   - This is the branch the upstream release pipeline forks off `main` / stable before tagging — see [`camunda-platform-release.yml`](./camunda-platform-release.yml), line 76: `RELEASE_BRANCH: ${{ inputs.releaseBranch != '' && inputs.releaseBranch || format('release-{0}', inputs.releaseVersion) }}`.
   - Branch HEAD can have post-tag commits the team wants tested (e.g. test stabilization for *this specific release*).
2. **`refs/tags/<monorepo_release>`** — fallback when the release branch has been cleaned up.
   - Tags match the version string exactly: `8.8.0`, `8.8.0-alpha8-rc1`.
   - Immutable — once cut, always points at the same commit.
3. **fail-fast** — `validate-release` errors out naming both candidates if neither exists. The matrix never fans out.

Refs are fully qualified so `actions/checkout` can never resolve a tag to a same-named branch (or vice versa) by accident.

## Worked examples

| `monorepo_release` | State of refs on origin | Resolved `ref` | Why |
| --- | --- | --- | --- |
| `8.8.5` | branch `release-8.8.5` + tag `8.8.5` exist | `refs/heads/release-8.8.5` | branch wins; identical to tag at release time, picks up post-tag fixes if any |
| `8.10.0-alpha1` | branch deleted post-release, tag `8.10.0-alpha1` exists | `refs/tags/8.10.0-alpha1` | fallback to immutable tag |
| `8.10.0-alpha1-rc1` | branch is `release-8.10.0-alpha1` (no `-rc1` suffix), tag `8.10.0-alpha1-rc1` exists | `refs/tags/8.10.0-alpha1-rc1` | input is RC-specific → use the RC tag (immutable revision that produced the rc1 image). Branch is *not* used because it may have advanced to rc2/rc3. |
| `8.10.0-alpha1-rc1` | tag `8.10.0-alpha1-rc1` is **missing** | — (fail) | tag missing means the release wasn't actually cut — docker image probably doesn't exist either. Fail fast. |
| `8.99.999` | neither branch nor tag | — (fail) | typo or wrong version |

## What you cannot do today (and what to do instead)

| Goal | What works | What does *not* work |
| --- | --- | --- |
| Test the released `8.8.5` artifacts | `monorepo_release: 8.8.5` | — |
| Test an in-flight release before the tag is cut | `monorepo_release: <intended-version>` while `release-<intended-version>` branch exists | The tag won't exist yet — but that's fine, the branch wins. |
| Test a specific RC (e.g. rc1) of an alpha | `monorepo_release: 8.10.0-alpha1-rc1` | Don't pass `8.10.0-alpha1` and hope to get rc1 — that resolves to the alpha1 branch HEAD, which may already be rc2. |
| Test the latest state of an alpha's release branch regardless of RC | `monorepo_release: 8.10.0-alpha1` (no `-rcN` suffix) | — |

## Routing decisions still tied to `base` (the stable branch)

These knobs are still keyed off the stable branch name and need updating when a new minor is released (e.g. `stable/8.10` going forward). See the workflow file for the conditionals — search for `'stable/8.'` and `'main'`.

| Knob | Today |
| --- | --- |
| `NON_STANDALONE` (separate `zeebe`/`operate`/`tasklist` images) | `stable/8.6` and `stable/8.7` only |
| Tasklist V1 / V2 matrix | 8.6/8.7 → V1 only; `main` → V2 only; 8.8/8.9 → both |
| RDBMS jobs | `stable/8.9` and `main` only |
| Test directory layout | `main`, `stable/8.8`, `stable/8.9` each have a dedicated path |

A future refactor can derive these from the checked-out source tree (e.g. "does `tests/tasklist/v1` exist?") rather than the branch name. Not in scope for the source-ref fix.

## Smoke-testing the workflow itself

The workflow has no built-in toggle to suppress the release-channel Slack notification. To smoke-test, either:

- Run a release version whose result you're happy to broadcast, or
- Temporarily edit the `notify-result` job's `if:` on a local fork before triggering.

(An earlier draft of this workflow had a `notify_slack` boolean input for this purpose, reverted in [`74465048251`](https://github.com/camunda/camunda/commit/74465048251) since it was only useful during PR development.)

## Related

- [`camunda-platform-release.yml`](./camunda-platform-release.yml) — the upstream release pipeline that creates `release-<version>` and the version tag.
- Source-of-truth fix that introduced the branch-first `ref` resolution: [#53772](https://github.com/camunda/camunda/pull/53772).
