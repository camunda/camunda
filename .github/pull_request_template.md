## Description

<!-- Describe the goal and purpose of this PR. -->

## Checklist

<!--- Please delete options that are not relevant. Boxes should be checked by reviewer. -->
- [ ] Enable backports when necessary (fex. [for bug fixes](https://github.com/camunda/camunda/blob/main/CONTRIBUTING.md#backporting-changes), [for CI changes](https://camunda.github.io/camunda/ci/#when-to-backport-ci-changes), or [for documentation changes](https://camunda.github.io/camunda/ci/#documentation-specific-backporting-monorepo-docs-folders)).
- [ ] If this PR modifies the [C8 Orchestration Cluster E2E Test Suite](qa/c8-orchestration-cluster-e2e-test-suite), its required automatic PR check (**C8 E2E Test Suite / Tests**) is green. It blocks only on the specs this PR adds or modifies; the non-blocking **C8 E2E Test Suite / Full Result** check reds on any other suite failure, so I have also reviewed those and confirmed they are pre-existing, not caused by this PR. For a **fork PR**, where the automatic check does not run, the [on-demand workflow](https://github.com/camunda/camunda/actions/workflows/c8-orchestration-cluster-e2e-tests-on-demand.yml) has been run instead and its results documented.

## Related issues

<!--
Link the tracked ISSUE this PR belongs to (link the issue, NOT a PR):
- This PR fully resolves the issue:  closes #1234  (also: fixes #1234, resolves #1234 — auto-closes it on merge)
- One of several PRs for the issue (an epic, or work split across releases):  relates to #1234  or a bare  #1234
This satisfies the gate but does NOT close the issue, so it stays open until the last PR lands.
No tracked issue (hotfix, dep bump, CI/refactor), or it is tracked in another repo? Tick the opt-out below instead.
-->

closes #

- [ ] This PR does not need a linked issue (no tracked issue, or it is tracked in another repository)

