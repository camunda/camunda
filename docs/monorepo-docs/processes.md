# Processes

This page collects processes we follow in the `camunda/camunda` monorepo.

## Renovate PR Handling

We use [Renovate to automate dependency updates](./ci.md#renovate) in the `camunda/camunda` monorepo. However, not all updates can be automatically merged as-is due to breaking changes or adjustments needed to the code base.

We identify cases where manual intervention is needed by looking at open Renovate Pull Requests which are older than 7 days.

We assign an engineer as DRI to each open Renovate PR that needs manual intervention, to address it. The engineer is chosen based on expertise, e.g. backend engineers for backend dependencies.

The DRI will also be mentioned on the Renovate PR with a link to the below responsibilities, and reminded of that 3 weeks after their assignment (delayed).

We have GitHub teams of engineers grouped by expertise:

- [Monorepo Backend engineers](https://github.com/orgs/camunda/teams/monorepo-backend-engineers)
- [Monorepo Frontend engineers](https://github.com/orgs/camunda/teams/monorepo-frontend-engineers)
- [Monorepo DevOps team](https://github.com/orgs/camunda/teams/monorepo-devops-team)

If you spot errors in the grouping, reach out to the Monorepo DevOps team!

### Overview

An overview of Renovate PRs that need manual intervention and are assigned or even delayed can be found [on this GH project board](https://github.com/orgs/camunda/projects/224/views/1).

To identify potential overloads of one group there are views with breakdowns available by expertise.

### Automation

We use a [daily GitHub Actions workflow](https://github.com/camunda/camunda/blob/main/.github/workflows/renovate-daily.yml) to execute the [assignment logic script](https://github.com/camunda/camunda/blob/main/.ci/scripts/renovate-assignments.py) automatically. Actions and PR comments are done via a GitHub App.

### DRI Responsibilities

The DRI for an open Renovate PR is responsible for addressing the dependency upgrade and getting it merged in a timely manner. They are expected to:

- Get necessary information from the changelog and usages from our code base.
  - Reach out to your peers via `#team-orchestration-cluster` on Slack in case of more questions.
- Do adjustments to the code base to accommodate dependencies with breaking changes, ensuring green CI and passing tests.
  - Minor and patch updates should almost always be resolved in this way, without an extra ticket.
- Explore AI-assistance to get breaking changes fixed.
- If needed, create follow-up tickets for major upgrades or big refactoring tasks, and ensure that those tickets are scheduled within their team's planning.
- Improve [our Renovate configuration](https://github.com/camunda/camunda/blob/main/.github/renovate.json) to make sure updates are as smooth as possible, e.g. by:
  - Applying appropriate grouping of similar dependencies (all React components, similar Maven plugins) to reduce the number of PRs.
  - Allow auto-merging if possible.
- Complete the above steps within 3 weeks of the DRI assignment.
  - Reach out to your manager to adjust priorities or find a replacement if the above timeline is not possible.
