# FOSSA Reporting Runbook

How to publish FOSSA attribution and SBOM reports for a component in the monorepo release. For
license checks and triage see [License Checks](./ci.md#license-checks) and
[Unhandled FOSSA Licensing Issues](./processes.md#unhandled-fossa-licensing-issues-reported-in-top-monorepo-ci).
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
   See [infra FOSSA docs](https://github.com/camunda/infra-core/blob/stage/docs/scanning-tools/fossa.md).
3. **Add a job.** In `camunda-platform-release.yml`, copy `fossa-release-optimize` and change the
   `project-id`, the group IDs and the `if` gate. Add the job to `needs` and to the `if` of
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

- **`wait-for-scan` times out.** The scan for that tag revision is missing or still running. Check
  the `check-licenses.yml` run for the tag SHA. The FOSSA CLI can hang, so re-run the scan if needed,
  then re-run the failed job. Release artifacts are already published at that point.
- **Other failures.** The failed job triggers `notify-if-failed`. The release manager dispatches it
  to Engineering Operations for plumbing issues. Wrong report content goes through the license
  triage process.
