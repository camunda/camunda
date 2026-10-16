## Description

<!-- Describe the goal and purpose of this PR. -->

## Checklist

<!--- Please delete options that are not relevant. Boxes should be checked by reviewer. -->
- [ ] Enable backports when necessary (fex. [for bug fixes](https://github.com/camunda/camunda/blob/main/CONTRIBUTING.md#backporting-changes) or [for CI changes](https://github.com/camunda/camunda/wiki/CI-&-Automation#when-to-backport-ci-changes)).
- [ ] If this PR modifies the [C8 Orchestration Cluster E2E Test Suite](qa/c8-orchestration-cluster-e2e-test-suite), the automatic PR check (which blocks on the specs this PR adds or modifies) is green. Because it does not gate on untouched tests and does not run on fork PRs, when this change can break tests it does not edit (shared page object, fixture or config) or the PR is from a fork, the [on-demand workflow](https://github.com/camunda/camunda/actions/workflows/c8-orchestration-cluster-e2e-tests-on-demand.yml) has also been run and any failures documented in the description, confirmed not to be regressions introduced by this PR.

## Related issues

<!--
Link the tracked ISSUE this PR belongs to (link the issue, NOT a PR):
- This PR fully resolves the issue:  closes #1234  (also: fixes #1234, resolves #1234 — auto-closes it on merge)
- One of several PRs for the issue (an epic, or work split across releases):  relates to #1234  or a bare  #1234
This satisfies the gate but does NOT close the issue, so it stays open until the last PR lands.
No tracked issue (hotfix, dep bump, CI/refactor)? Tick the opt-out below instead.
-->

closes #

- [ ] This PR does not need a linked issue

