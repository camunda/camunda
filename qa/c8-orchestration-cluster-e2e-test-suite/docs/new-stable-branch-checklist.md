# What to do after a new stable branch is cut (c8-orchestration-cluster-e2e-test-suite CI)

When a new `stable/<X.Y>` branch is cut, this test suite's CI does not pick it up on its own.
Several files need a matching, mostly manual update before the branch gets nightly, on-demand,
and release test coverage. Apply every item below right after the cut, rather than waiting for
a broken or missing nightly run to surface each gap one at a time. That's exactly how it played
out after the `stable/8.10` cut: an initial setup commit, then about 15 follow-up fix commits
over the next two days to fully wire it in.

Replace `<X.Y>` below with the new branch's version (e.g. `8.11`), and `<PREV>` with the
previous stable version (e.g. `8.10`).

## Required manual edits

1. **Create the three nightly workflow files**, copying the previous version's files and
   swapping the branch/version:
   - `.github/workflows/c8-orchestration-cluster-nightly-<X.Y>-e2e.yml`
   - `.github/workflows/c8-orchestration-cluster-nightly-<X.Y>-api-es.yml`
   - `.github/workflows/c8-orchestration-cluster-nightly-<X.Y>-api-rdbms.yml`

   Each is a thin wrapper calling `uses: ./.github/workflows/c8-orchestration-cluster-reusable-*.yml`
   with `branch: stable/<X.Y>`. Stagger the `cron` schedule from the neighboring versions so
   nightlies don't all start at once.

2. **Register the new nightlies in the metrics report**
   (`.github/workflows/c8-orchestration-cluster-nightly-metrics-report.yml`). This workflow
   does not discover nightly workflows. It iterates three hardcoded
   `"<version>|<workflow-file>"` lists (API/ES, API/RDBMS, E2E). Add one line to each list, in
   ascending-version order, between `<PREV>` and `main`. Skip this and the new nightlies run
   and post to Slack individually, but stay absent from the daily report.

3. **Route the new branch in the on-demand workflow**
   (`.github/workflows/c8-orchestration-cluster-e2e-tests-on-demand.yml`):
   - Base-branch detection: add `stable/<X.Y>` to both the `git fetch origin ...` line and the
     `for candidate in ...` loop that resolves a base branch from an arbitrary input branch.
   - RDBMS gate (`c8-orchestration-cluster-api-tests-rdbms` job's `if:`): this is still an
     allow-list (`contains(fromJSON('["stable/8.9","stable/8.10","main"]'), ...)`), not yet
     converted to an exclusion-list like the release workflow's equivalent (see below). Add the
     new version here, or, better, convert it to
     `!contains(fromJSON('["stable/8.6","stable/8.7","stable/8.8"]'), needs.validate-branch.outputs.base)`
     so it stops needing this edit on every future cut.
   - ES API-tests `camunda_mode` matrix (the job just above the RDBMS one): as of the 8.10 cut
     this still only lists `stable/8.9 || main`. It does not include `stable/8.10`, so 8.10's
     on-demand ES API tests only ever run in `profiles` mode, never `all-in-one`. This is a
     pre-existing gap, not something this checklist introduced. Check this line at every cut
     and decide whether the new branch should be added, or whether the condition should be
     generalized instead of extended again.
   - `tasklist_mode` matrix: enumerates `stable/8.6`/`stable/8.7` (v1-only) and
     `stable/8.8`/`stable/8.9` (v1+v2), defaulting everything else to v2-only. Self-updating as
     long as no future branch reintroduces Tasklist v1. No edit needed unless that changes.

4. **Route the new branch in the release workflow**
   (`.github/workflows/c8-orchestration-cluster-e2e-tests-release.yml`):
   - Version-to-base mapping (the `if/elif` chain that sets `base="stable/<X.Y>"` from
     `$major_minor`): add a new `elif` arm for `<X.Y>`. Without it, validating an `<X.Y>`
     release resolves `base="main"`, which drives test-directory routing and matrix gating.
     Sometimes that lands on the right behavior by coincidence, but always for the wrong reason.
   - RDBMS gate (`build-rdbms-dist` and the RDBMS API-tests job): already self-updating,
     expressed as
     `!contains(fromJSON('["stable/8.6","stable/8.7","stable/8.8"]'), needs.validate-release.outputs.base)`,
     an exclusion of the branches that predate RDBMS. No edit needed unless a future branch
     predates RDBMS, which it won't.
   - `tasklist_mode` matrix: same v1/v2 enumeration and same self-updating caveat as the
     on-demand workflow above.

5. **Cover the new branch in the responses-regeneration matrix**
   (`.github/workflows/c8-orchestration-cluster-responses-regenerate.yml`): add a
   `matrix.include` entry:
   ```yaml
   - ref: stable/<X.Y>
     safe: stable-<X.Y>
     paths_expected: 'true'
   ```
   `paths_expected` is declared per-row, not inferred from the branch name, specifically so
   this is the one place a new branch needs registering. Set it to `'true'` unless the new
   branch predates the typed `buildUrl` generator, which it won't going forward.

6. **Route the new branch in the branch-scoped API on-demand workflow**
   (`.github/workflows/c8-orchestration-cluster-e2e-api-test-branch-on-demand.yml`): it carries
   its own, separate copy of the base-branch detection block (`git fetch origin ...` /
   `for candidate in ...`), and it hasn't been updated since `stable/8.8`. Both `stable/8.9` and
   `stable/8.10` are still missing from it today. Add `stable/<X.Y>` here too, and back-fill the
   missing versions at the same time instead of leaving them for a future cut.

## After merging

- Confirm each new nightly workflow actually appears in the next day's metrics report (step 2
  above) rather than only checking that the workflow file parses.
- Watch the first few on-demand and release runs against the new branch for "Detected base
  branch: main" in the logs. That log line means one of the base-branch-detection spots in
  step 3, 4, or 6 was missed.
