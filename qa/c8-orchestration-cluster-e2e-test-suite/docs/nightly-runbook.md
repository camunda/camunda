# Nightly runbook (Orchestration Cluster E2E)

This runbook is for the teams that watch the Orchestration Cluster E2E nightlies. Today Test Automation Enablement watches all of them. A handover of each test area to the team that owns it is proposed in [#65304](https://github.com/camunda/camunda/issues/65304), with the technical preparation in [#65305](https://github.com/camunda/camunda/issues/65305).

## What runs

Each nightly starts the Orchestration Cluster of one version on the GitHub runner and runs this suite against it. The UI nightlies run the Playwright specs under `tests/`, except `tests/api/`. The API nightlies run `tests/api/` against Elasticsearch or an RDBMS.

| Version | UI (`-e2e`) | API, Elasticsearch (`-api-es`) | API, RDBMS (`-api-rdbms`) |
|---------|-------------|--------------------------------|---------------------------|
| 8.7     | 00:15 UTC   | 00:00 UTC                      |                           |
| 8.8     | 00:45 UTC   | 00:30 UTC                      |                           |
| 8.9     | 01:15 UTC   | 01:00 UTC                      | 01:00 UTC                 |
| 8.10    | 02:15 UTC   | 02:00 UTC                      | 02:00 UTC                 |
| `main`  | 01:45 UTC   | 01:30 UTC                      | 01:30 UTC                 |

The workflows are `c8-orchestration-cluster-nightly-<version>-<type>.yml`. Each one checks out the branch it tests, so a nightly of `stable/8.9` runs the tests, page objects and CI scripts of `stable/8.9`.

## Who owns which test

`.codeowners` names the owner of each test area. CI Analytics attributes a nightly failure to the code owner of the failing specs, if all of them have the same owner. Failing specs owned by the workflow's `TEST_OWNER`, `@camunda/test-automation-team`, do not count against one area owner: a `common-flows/` spec usually fails because of a change in an area. Otherwise CI Analytics attributes the failure to `TEST_OWNER`, and the job log lists the candidate owners.

The proposed area owners are not live yet. They wait for the area teams to approve [#65326](https://github.com/camunda/camunda/pull/65326):

|                                   Specs under `tests/`                                    |                  Owner                  |
|-------------------------------------------------------------------------------------------|-----------------------------------------|
| `operate/`                                                                                | `@camunda/operate-admin-pod`            |
| `identity/` (Admin UI)                                                                    | `@camunda/operate-admin-pod`            |
| `tasklist/`                                                                               | `@camunda/employee-engagement-tasklist` |
| `api/`                                                                                    | `@camunda/core-features`                |
| `api/v2/` authorization, group, role, tenant, user, mapping rule and authentication specs | `@camunda/identity`                     |
| `common-flows/`, page objects, fixtures and the suite framework                           | `@camunda/test-automation-team`         |

## Where results appear

- **Per run.** Each nightly posts its Playwright results to `#c8-orchestration-cluster-e2e-test-results`.
- **Triage digest.** `C8 Orchestration Cluster Nightly Triage` runs Monday to Friday at 03:30 UTC and posts one digest to the same channel. Fix-agent results arrive as replies in its thread.
- **Suite health.** A second run of the triage workflow, Monday to Friday at 07:45 UTC, posts to the same channel when a nightly stays red or stops running. See [Suite health](#suite-health).
- **Metrics report.** `C8 Orchestration Cluster Nightly Metrics Report` posts the latest outcome of every nightly, by version, to the same channel at 03:30 UTC.
- **Nightly metrics.** `camunda/qa-metrics-exporter` posts a daily digest at 11:00 UTC to `#team-test-automation-metrics`. Its `OC` lines are these nightlies: failures per version against the 7-day average, tests that failed 3 nights or more in a row, new breakages and flakiness.

## Reading the triage digest

Triage reads the newest run of each nightly, collects the tests that failed from its JSON report, and groups them by version.

- **One fix agent per version.** Triage dispatches at most one agent per version for its failing tests, up to 5 a run. A nightly that failed before it produced test results gets its own dispatch, up to 5 more. A version that already had an agent on the same UTC day is not dispatched again.
- **Open fix pull requests.** Triage leaves out the failing specs whose file an open fix-agent pull request on the same branch already covers. It still dispatches an agent for the other failing specs of that version, so one branch can have more than one open fix-agent pull request.
- **Weekends.** Triage runs Monday to Friday, so a Saturday or Sunday failure appears only if it still fails on Monday.

## The fix agent

`C8 Orchestration Cluster Nightly Fix Agent` reads the failing tests and their screenshots, applies a minimal fix, and opens a draft pull request against the failing branch, `main` or `stable/<version>`. It starts the on-demand workflow for that branch and adds the run link to the pull request, so you can see the fix pass before you merge. The `Nightly Fix Agent` section of [`AGENTS.md`](../AGENTS.md) holds its instructions.

- **Labels.** Fix-agent pull requests have the label `failing-test-fix`.
- **Stale pull requests.** `Close Stale Fix PRs` runs Monday to Friday at 03:00 UTC and closes the fix-agent pull requests that were opened more than 24 hours earlier and are not merged. Triage runs after it, so a pull request normally stays open until the second cleanup after it was opened: about two days, or until Monday for a pull request opened on Friday. A review comment does not keep a pull request open. To keep it, add the label `do-not-close`.
- **Other branches.** Fix the affected branch first, then forward-port up to `main`. If `main` fails too, fix `main` first and backport.

## Fix, skip with a linked bug, or escalate

1. **The product changed on purpose.** This was the most common cause in the red nights classified for the handover. Update the test, or merge the fix agent's pull request after you review it.
2. **The test is flaky.** It passed on retry, or it fails on some nights only. Fix the cause in the test: a missing wait, a race, a shared fixture. Do not skip it.
3. **The product regressed.** Follow **Product-Bug Escalation** in [`AGENTS.md`](../AGENTS.md): rule out flakiness, pin the change that broke it, and confirm that the test is still correct. Then file or reuse the bug in the owning repository, and skip the test with the bug linked in the annotation. Skipping is allowed only for a confirmed product bug with a filed issue.
4. **The environment failed.** The cluster did not start, a container crashed, or the runner was lost. No test is at fault. Re-run the nightly. If it fails the same way again, raise it in `#c8-orchestration-cluster-e2e-test-results`.
5. **It belongs to another area.** The failure is in your area's spec, but the cause is in another team's product. Hand it over in the digest thread, and say what you found.

Every Monday, `camunda/qa-metrics-exporter` opens pull requests that remove the skip from tests whose linked bug is closed. `C8 Orchestration Cluster Unskip Verify Agent` then runs the un-skipped specs and pushes a fix to the same pull request if they fail. Review them like a fix-agent pull request.

## Suite health

Triage reads the newest run of each nightly only. A suite-health check reads the scheduled history of each nightly, Monday to Friday at 07:45 UTC, and posts in `#c8-orchestration-cluster-e2e-test-results` when:

- a nightly has been red on 3 scheduled runs in a row. Weekend runs count. Cancelled and skipped runs are ignored.
- a nightly has had no scheduled run for 26 hours, for example because the workflow was disabled.

It runs later than triage because GitHub can start a nightly hours after its scheduled time: at 07:45 UTC, a nightly that did not run that night is more than 26 hours past its previous run. It posts each condition once, again every 7 days while it lasts, and once more when it clears. New and repeated alerts mention the Slack group in the repository variable `OC_SUITE_HEALTH_SLACK_MENTION`, if it is set. A red streak means that the nightly has not been fixed for several days: look at it today. For a nightly with no run, check in the Actions tab that its workflow is enabled.

The check reads the nightlies listed in `NIGHTLY_WORKFLOWS` of the triage workflow, the same list that triage scans. It saves what it announced in `health.json` on the `nightly-triage-state` branch.

## Incidents

The nightlies are excluded from CI incidents today, because Enablement reviews them every day. [#65305](https://github.com/camunda/camunda/issues/65305) plans to lift that exclusion once the area teams have taken over. A red nightly then raises a CI incident through the rule that covers other CI jobs, for the owner that CI Analytics attributes it to (see [Who owns which test](#who-owns-which-test)). incident.io groups the repeat alerts of a nightly that stays red, and a medic can silence a long fix with `ci-silence-incident`. A nightly that stops running raises no incident: the suite-health check above reports it.

## Re-running

- **A nightly.** Use **Re-run failed jobs** on the run, or run `C8 Orchestration Cluster E2E Tests On Demand` for the branch.
- **The triage.** Run `C8 Orchestration Cluster Nightly Triage` by hand. Its `dispatch` input set to false gives the digest only. `send_slack_notification` and `slack_channel` decide where it posts. The suite-health check saves what it announced only for a run of `main` that posts to Slack, so a test run cannot mark an alert as sent.

## Supervised replay

A team that sees no real failure in its area during the transition handles a recorded one instead, with Enablement watching. Pick a red night in your area from the classification in [#65305](https://github.com/camunda/camunda/issues/65305). Then:

1. Open the nightly run and the triage digest of that night.
2. Find your area's failing specs and decide the cause: product change, flaky test, product regression, environment, or another area.
3. Compare your decision with the fix that was merged, or the bug that was filed.
4. Record the result in [#65304](https://github.com/camunda/camunda/issues/65304).

