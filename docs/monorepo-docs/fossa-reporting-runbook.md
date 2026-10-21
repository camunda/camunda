# FOSSA Reporting Runbook

How to publish FOSSA attribution and SBOM reports for a component in the monorepo release. For
license checks and triage see [License Checks](./ci.md#license-checks) and
[Unhandled FOSSA Licensing Issues](https://github.com/camunda/camunda/blob/main/docs/monorepo-docs/processes.md#unhandled-fossa-licensing-issues-reported-in-top-monorepo-ci).
This runbook covers the plumbing only.

## How it works

1. [`check-licenses.yml`](../../.github/workflows/check-licenses.yml) scans each FOSSA project
   (`single-app`, `optimize`, `c8run`) on every tag and push.
2. The `fossa-release*` jobs in
   [`camunda-platform-release.yml`](../../.github/workflows/camunda-platform-release.yml) wait for
   the scan at the release tag revision, then create a FOSSA release in two release groups
   (attribution as TXT, SBOM as CycloneDX JSON).
3. The reports are served from the public FOSSA portal, not attached to GitHub releases.

The steps live in the local composite action
[`fossa-release-project`](../../.github/actions/fossa-release-project/action.yml). Each project gets
its own job, so a failure in one does not hide the result of another.

## Add a component

**Who:** Engineering Operations, plus an Infra team member (FOSSA org Admin) for step 2.

1. **Check the scan.** The component needs its own FOSSA project and a matching entry in the
   `check-licenses.yml` matrix. Note the project locator, for example
   `custom+50756/camunda/camunda@optimize`.
2. **Create the release groups.** Ask the Infra team (`#ask-infra`) to create
   `camunda-<component>-attribution` and `camunda-<component>-sbom` under Monorepo Team and set them
   to public. The CI account `ci-camunda-camunda` lacks the rights, and the FOSSA UI search only
   shows groups visible to the searching account, so ask them to check for existing groups first.
   Note both group IDs from the group URL (`https://app.fossa.com/projects/group/<id>`).
   Ask them to store the API key under `products/camunda/ci/camunda` as
   `FOSSA_API_KEY_<COMPONENT>`, next to the single-app key.
   See [infra FOSSA docs](https://github.com/camunda/infra-core/blob/stage/docs/scanning-tools/fossa.md).
3. **Add a job.** In `camunda-platform-release.yml`, copy `fossa-release-optimize` and change the
   `project-id`, the group IDs, `fossa-api-key-vault-key` and the `if` gate. Add the job to `needs` and to the `if` of
   `notify-on-success` and `notify-if-failed`.
4. **Dry run.** Run `zeebe-release-stable-dry-run.yml` from the branch. The new job must print the
   FOSSA calls for its project locator, and the other `fossa-release*` jobs must be unchanged.
5. **Backfill.** For releases already shipped, replay the `fossa/release` calls per tag with
   `dry-run: true` first. Check beforehand that each tag revision has a completed scan.
6. **Link the docs.** Add the portal links to the
   [dependencies page](https://docs.camunda.io/docs/reference/dependencies/) in `camunda-docs`, in
   `docs/` and each supported `versioned_docs/` folder. Check that all links resolve.
7. **Backport.** Add the `backport stable/X.Y` labels for each supported stable branch, ideally
   before the next patch release.

## When a release run fails

- **`wait-for-scan` times out.** The scan for that tag revision is missing, failed or still running.
  Release artifacts are already published at that point, so only the scan and the failed job need a
  re-run:
  1. Find the `check-licenses.yml` runs for the tag SHA. A release starts two of them, one for the
     `release-X.Y.Z` branch push and one for the tag push. `wait-for-scan` looks up the revision by
     project and SHA only, so either run is enough.

     ```bash
     gh api "repos/camunda/camunda/actions/workflows/check-licenses.yml/runs?head_sha=$(git rev-list -n1 <tag>)" \
       --jq '.workflow_runs[] | "\(.id) \(.head_branch) \(.conclusion)"'
     ```
  2. If the `Analyze <project> dependencies` job is red, use "Re-run failed jobs" on that run.
     `check-licenses.yml` has no `workflow_dispatch`, so a re-run is the only way to scan an existing
     tag again. If the scan is still running, wait for it to finish.
  3. Once the scan is green, use "Re-run job" on the failed `fossa-release*` job in the release run.
     That job and the notify jobs that depend on it run again, the rest of the release keeps its
     results.
- **`fossa/release` fails partway.** The step makes four calls in order: attribution release, SBOM
  release, attribution report, SBOM report. It does no lookup or rollback, so a re-run always starts
  again from the first call. Check the job log to see how far it got:
  - **No release created** (the first call failed): fix the cause, then re-run the job.
  - **Some releases created:** the log prints each created release ID. Make only the missing calls
    locally with those IDs, as for a backfill. Do not re-run the job, it would create duplicate
    releases.
  - **Fails again:** check the HTTP status in the log. 401/403 means the API key or group rights,
    404 a wrong group ID, 5xx a FOSSA outage. A re-run only helps for 5xx.
  - **If this happens again:** make the local
    [`fossa-release-project`](../../.github/actions/fossa-release-project/action.yml) action look
    up each release by title before creating it, so a re-run skips the calls that already
    succeeded. Not done yet, as no partial failure has been seen so far.
- **Other failures.** The failed job triggers `notify-if-failed`. The release manager dispatches it
  to Engineering Operations for plumbing issues. Wrong report content goes through the license
  triage process.

