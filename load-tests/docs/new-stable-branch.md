# New Stable Branch Checklist

This document lists the steps to take after you cut a new stable branch (for example
`stable/8.10` from `main`).

Without these steps, the new branch keeps testing the `main` configuration. The cross-branch
schedulers on `main` do not know about the new branch.

See [issue #60970](https://github.com/camunda/camunda/issues/60970) for the background. See the
[reference PRs](#reference-prs) below for a full example (8.10).

## On the new stable branch

Do these steps right after you cut the branch.

### Point workflows at the branch itself

In [`camunda-load-test.yml`](../../.github/workflows/camunda-load-test.yml) and
[`camunda-release-load-test.yaml`](../../.github/workflows/camunda-release-load-test.yaml):

- Change the `ref` default from `main` to `stable/XXX`
- Remove the `target-version` input  (`main` tests several setup versions through a matrix. A stable branch tests only one version.)
- In the `newLoadTest.sh --target-version` call, hardcode the value to `stable-XXX`

**Example:** [PR #60997](https://github.com/camunda/camunda/pull/60997):
[`camunda-load-test.yml`](https://github.com/camunda/camunda/blob/caef873789e668e37495d822791fa16fe25c3c42/.github/workflows/camunda-load-test.yml#L29)
and
[`camunda-release-load-test.yaml`](https://github.com/camunda/camunda/blob/caef873789e668e37495d822791fa16fe25c3c42/.github/workflows/camunda-release-load-test.yaml#L113)
ref defaults, and the
[`newLoadTest.sh --target-version` call](https://github.com/camunda/camunda/blob/caef873789e668e37495d822791fa16fe25c3c42/.github/workflows/camunda-load-test.yml#L575).

### Fetch the setup files from main at runtime

The folder
[`load-tests/setup/stable-XXX`](https://github.com/camunda/camunda/tree/main/load-tests/setup)
lives only on `main` (see [On `main`](#on-main) below). In
[`camunda-load-test.yml`](../../.github/workflows/camunda-load-test.yml):

- Add a `sparse-checkout` step to the job that renders the setup.
- Set `ref: main` on that step.
- Check out [`load-tests/`](https://github.com/camunda/camunda/tree/main/load-tests),
  [`.github`](https://github.com/camunda/camunda/tree/main/.github), and
  [`.tool-versions`](https://github.com/camunda/camunda/blob/main/.tool-versions).

**Example:** [PR #60997](https://github.com/camunda/camunda/pull/60997):
[`camunda-load-test.yml`](https://github.com/camunda/camunda/blob/caef873789e668e37495d822791fa16fe25c3c42/.github/workflows/camunda-load-test.yml#L538-L545).

### Delete the branch's own setup folder

- The branch no longer needs `load-tests/setup/`: delete this folder.
- Update [`load-tests/README.md`](../README.md) with:

  ```
  > [!CAUTION]
  > The `setup` folder in the branch `stable/XXX` does not exist.
  > To deploy a load test for the Camunda Platform XXX, use the `stable-XXX` folder from the `main` branch instead.
  ```

**Example:** [PR #61295](https://github.com/camunda/camunda/pull/61295):
[`load-tests/setup/`](https://github.com/camunda/camunda/tree/343b6cd748a93247989864da294360e0eb2e7823/load-tests/setup)
as it looked right before removal, and the
[`load-tests/README.md` note](https://github.com/camunda/camunda/blob/eecf96d57a026913d44986d8b5a49d8e3751e067/load-tests/README.md#L14-L16).

### Delete workflows that only apply to main

These workflows coordinate tests across all branches. A copy on the stable branch serves no
purpose.

- Delete
  [`camunda-load-test-smoke-dispatch.yml`](../../.github/workflows/camunda-load-test-smoke-dispatch.yml).
  This workflow dispatches smoke tests for every setup version changed in a PR. The branch has
  only one version, so it does not need this.
- Delete
  [`camunda-scheduled-release-load-tests.yml`](../../.github/workflows/camunda-scheduled-release-load-tests.yml).
  This is the cross-branch release-test scheduler.
- Simplify [`camunda-load-test-smoke.yml`](../../.github/workflows/camunda-load-test-smoke.yml).
  Trigger it directly on `pull_request` and `workflow_dispatch`. Remove the `target-version`
  input.

**Example:** [PR #60997](https://github.com/camunda/camunda/pull/60997):
[`camunda-load-test-smoke-dispatch.yml`](https://github.com/camunda/camunda/blob/15d5eb12ed80512d6f368bf7dbe0bce4e770c336/.github/workflows/camunda-load-test-smoke-dispatch.yml)
and
[`camunda-scheduled-release-load-tests.yml`](https://github.com/camunda/camunda/blob/15d5eb12ed80512d6f368bf7dbe0bce4e770c336/.github/workflows/camunda-scheduled-release-load-tests.yml)
as they looked right before removal, and the
[`camunda-load-test-smoke.yml` simplification](https://github.com/camunda/camunda/blob/caef873789e668e37495d822791fa16fe25c3c42/.github/workflows/camunda-load-test-smoke.yml#L10-L27).

### Drop the unused CI jobs

In [`ci.yml`](../../.github/workflows/ci.yml):

- Remove `load-test-golden-tests` and `load-test-helm-lockfile`. These jobs test the deleted
  `load-tests/setup/` folder.
- Remove their entries from any `needs:` lists.

**Example:** [PR #61295](https://github.com/camunda/camunda/pull/61295):
[`ci.yml`](https://github.com/camunda/camunda/blob/343b6cd748a93247989864da294360e0eb2e7823/.github/workflows/ci.yml#L242-L322)
as it looked right before removal.

## On `main`

### Add the setup folder

- Add a `load-tests/setup/stable-XXX` folder.
- Copy
  [`load-tests/setup/main/`](https://github.com/camunda/camunda/tree/main/load-tests/setup/main)
  as a starting point.

**Example:** [PR #60980](https://github.com/camunda/camunda/pull/60980):
[`load-tests/setup/stable-810`](https://github.com/camunda/camunda/tree/580faf8a8e8627f7034146b531a6d0c2a8125529/load-tests/setup/stable-810).

### Add the branch to the weekly load tests

In [`camunda-weekly-load-tests.yml`](../../.github/workflows/camunda-weekly-load-tests.yml):

- Add a job that calls `camunda-load-test.yml@stable/XXX` through a cross-branch `uses:`. Match
  the existing `weekly-XXX` jobs.
- Add the new job to the `notify` job's `needs:` list, so a failure still triggers the Slack
  notification.

This step needs no later backport. Each stable branch's own copy of `camunda-load-test.yml`
already fetches its own `stable-XXX` setup folder from `main`.

**Example:** [PR #61511](https://github.com/camunda/camunda/pull/61511):
[the new jobs](https://github.com/camunda/camunda/blob/43292fc30aec697f880b1f23be1491e7f90a1d2e/.github/workflows/camunda-weekly-load-tests.yml#L273-L311)
and the
[`notify` job's `needs:` list](https://github.com/camunda/camunda/blob/43292fc30aec697f880b1f23be1491e7f90a1d2e/.github/workflows/camunda-weekly-load-tests.yml#L316-L325).

### Add the branch to the scheduled release load tests

In
[`camunda-scheduled-release-load-tests.yml`](../../.github/workflows/camunda-scheduled-release-load-tests.yml):

- Add a `release-load-test-XXX` job. Pin it to the branch's current patch tag.
- Add the matching `verify-and-cleanup-XXX` job.
- Add `verify-and-cleanup-XXX` to the `notify-on-success` and `notify-on-failure` jobs' `needs:`
  lists and to their `if:` success/failure conditions.
- Add the branch to the Slack "Tested versions" message in both notifications.

**Example:** [PR #61002](https://github.com/camunda/camunda/pull/61002):
[`release-load-test-8-10`](https://github.com/camunda/camunda/blob/ef9285267a5556b6f16bd8bb74e263dffb8defc6/.github/workflows/camunda-scheduled-release-load-tests.yml#L62-L68),
[`verify-and-cleanup-8-10`](https://github.com/camunda/camunda/blob/ef9285267a5556b6f16bd8bb74e263dffb8defc6/.github/workflows/camunda-scheduled-release-load-tests.yml#L110-L117),
[`notify-on-success`](https://github.com/camunda/camunda/blob/ef9285267a5556b6f16bd8bb74e263dffb8defc6/.github/workflows/camunda-scheduled-release-load-tests.yml#L131-L143),
and
[`notify-on-failure`](https://github.com/camunda/camunda/blob/ef9285267a5556b6f16bd8bb74e263dffb8defc6/.github/workflows/camunda-scheduled-release-load-tests.yml#L202-L214).

### Update the release tag

`main` no longer tracks the branched-off version. Bump the `release-load-test-main` tag to the
next alpha line (for example `8.11.0-alphaN`).

## When a stable branch is deprecated

Reverse [Add the branch to the weekly load tests](#add-the-branch-to-the-weekly-load-tests) and
[Add the branch to the scheduled release load tests](#add-the-branch-to-the-scheduled-release-load-tests)
from [On `main`](#on-main):

- Remove that branch's jobs from
  [`camunda-weekly-load-tests.yml`](../../.github/workflows/camunda-weekly-load-tests.yml) and
  [`camunda-scheduled-release-load-tests.yml`](../../.github/workflows/camunda-scheduled-release-load-tests.yml).
- Remove its `verify-and-cleanup` job and its Slack notification line.

## Reference PRs

- [PR #60997](https://github.com/camunda/camunda/pull/60997): points `stable/8.10`'s own
  workflows at itself. Adds the sparse checkout of `main`'s setup folder.
- [PR #61295](https://github.com/camunda/camunda/pull/61295): removes the unused
  `load-tests/setup/` folder and its CI jobs from `stable/8.10`. This cleanup is optional.
- [PR #60980](https://github.com/camunda/camunda/pull/60980): adds the
  `load-tests/setup/stable-810` folder on `main`.
- [PR #61511](https://github.com/camunda/camunda/pull/61511): adds `stable/8.7`, `8.8`, and
  `8.9` to `main`'s weekly load tests. `stable/8.10` follows the same pattern.
- [PR #61002](https://github.com/camunda/camunda/pull/61002): adds `stable/8.10`'s
  `release-load-test-8-10` and `verify-and-cleanup-8-10` jobs to `main`'s scheduled release load
  tests.

