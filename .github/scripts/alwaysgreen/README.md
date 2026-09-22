# AlwaysGreen failure triage

CI-only tooling that classifies failed workflow runs, decides whether a failure is
worth handing to the fix agent, and dispatches it. `discover.py` fetches data from
the GitHub API and downloaded artifacts; `classify.py` and `plan.py` are pure
functions over that data, which is what keeps them unit-testable without a cluster
or a token.

The classification rules were derived from every failed run of
`docker-build-helm-integration.yml` in a 300-run window (29 failures). See the
module docstring in [`classify.py`](classify.py) for the non-obvious findings from
that analysis.

## `preview-env-smoke-test.yml`: one run, four branches

Most pipelines run one branch per run, so the run's own ref is the correct base ref
for every failing job in it. `preview-env-smoke-test.yml` breaks that assumption: it
deploys four refs (one per supported minor) in a single run, via a matrix job named
`Run ${{ matrix.deployment.version }} Smoke Tests`. Each leg's failure belongs to
the branch it actually deployed, not to the run's own ref — so `classify.py` maps
the rendered version in the job name to a base ref (`base_ref_for_job`,
`PREVIEW_ENV_BASE_REFS`) instead of trusting the caller-supplied default. This
matters because the base ref is part of every fingerprint and picks the fix agent's
target branch; sharing one ref across the four legs would target the wrong branch
and collapse four independent failures onto one dispatch key.

A minor is deliberately matched by a rendered-value regex (`^Run \d+\.\d+ Smoke
Tests$`), not a literal prefix: skipped legs keep the unrendered `${{ }}` form in
their job name, and only a rendered value proves the leg actually ran.

Two things need to be true of `PREVIEW_ENV_BASE_REFS` for this to keep working:

- The workflow's `ref-8-x` dispatch overrides are *not* used as the base ref. They
  point at unmerged PR branches, and the fix agent only accepts branches in
  [`plan.SUPPORTED_BASE_REFS`](plan.py).
- 8.11 has no stable branch yet and is tracked on `main` — but plain `"main"` is
  also the real ref for the main-branch pipeline's own dispatch key. Aliasing the
  8.11 leg to `"main"` would collide the two: both would hash to the same
  `<base_ref>:<surface>` dispatch key, and `merge_by_key` would fold them into one
  candidate, silently dropping whichever leg lost the merge. `PREVIEW_ENV_MAIN_REF`
  (`"main:preview-8.11"`) avoids the collision by being absent from
  `SUPPORTED_BASE_REFS`: a leg mapped to it is cleanly suppressed as
  `base-ref-not-supported-by-fix-agent` instead of being mis-dispatched.

A minor added to the workflow's matrix before `PREVIEW_ENV_BASE_REFS` is updated to
match must not fall back to the caller's `default` ref either — on the schedule
that runs this workflow, `default` is `"main"`, which is exactly the collision
above. `base_ref_for_job` falls back to `PREVIEW_ENV_MAIN_REF` instead, so an
unmapped minor is suppressed rather than mis-dispatched.

**The 8.11 leg is report-only by design, not by oversight.** Being absent from
`SUPPORTED_BASE_REFS` means `plan_dispatches()` suppresses every 8.11 candidate as
`base-ref-not-supported-by-fix-agent` and never dispatches an agent for it. That is
the intended contract for now, for two independent reasons:

1. There is no ref the agent could correctly target. 8.11 has no stable branch, and
   `"main"` is taken by the main pipeline's own dispatch key (above).
2. There is no 8.11 suite to fix. `alwaysgreen-fix.yml` derives `version=8.10` for
   `base_ref=main`, because the e2e repo tops out at `tests/SM-8.10` and the chart at
   `camunda-platform-8.10`. Dispatching an 8.11 failure would point the agent at
   8.10's test directory.

So 8.11 failures are still classified, fingerprinted and reported — they just stop
short of a dispatch. When an 8.11 chart and `tests/SM-8.11` suite exist, the fix is
to map 8.11 to a real branch and bump the version derivation in
`alwaysgreen-fix.yml`; until then, suppression is the correct outcome and
[`test_plan.py`](test_plan.py) asserts it
(`test_preview_env_8_11_leg_does_not_collide_with_the_real_main_candidate`).

### Evidence artifacts must be version-scoped too

The same one-run-four-legs shape affects which artifacts
[`alwaysgreen-fix.yml`](../../workflows/alwaysgreen-fix.yml) downloads as evidence.
`preview-env-smoke-test.yml` uploads `playwright-results-json`/`playwright-report`
per version, so an unscoped glob would hand the fix agent every version's evidence
instead of just the leg it was dispatched to fix. The `dl_versioned` helper
downloads the version-scoped artifact name first and only falls back to the
unscoped glob when the scoped download finds nothing — which is also what makes it
safe to use for other pipelines, which run one version per run and have no
version-scoped name at all.

### Triage dispatch from `preview-env-smoke-test.yml`

The `alwaysgreen-triage` job at the bottom of that workflow only fires for the
`test` job's failures. A `Deploy 8.x Preview Environment` failure is an ArgoCD or
chart-deploy problem reported under the inner reusable deploy workflow's own job
names, which map to no surface here — `notify-failure` already routes those to the
eng-ops medic directly.

It also only fires on `schedule` runs, not `workflow_dispatch`: a manual run may
point the `ref-8-x` inputs at unmerged branches, and neither the version-to-branch
mapping above nor the fix agent itself can follow those. `dry_run` stays `true`
until the classification here has been confirmed against real failing scheduled
runs; after that, dropping the input lets the Vault flag decide.
