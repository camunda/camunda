# Renovate PR Handling

## Process

We use [Renovate to automate dependency updates](./ci.md#renovate) in the `camunda/camunda` monorepo.
However, not all updates can be automatically merged as-is due to breaking changes or adjustments
needed to the code base.

We identify cases where manual intervention is needed, e.g. by looking at open Renovate Pull
Requests which are older than 3 days.

We assign an engineer as DRI to each open Renovate PR that needs manual intervention, to address
it. The engineer is chosen based on expertise, e.g. backend engineers for backend dependencies.

We have GitHub teams of engineers grouped by expertise:

- [Monorepo Backend engineers](https://github.com/orgs/camunda/teams/monorepo-backend-engineers)
- [Monorepo Frontend engineers](https://github.com/orgs/camunda/teams/monorepo-frontend-engineers)
- [Monorepo DevOps team](https://github.com/orgs/camunda/teams/monorepo-devops-team)

If you spot errors in the grouping, reach out to the Monorepo DevOps team!

### Automation

We will leverage automation via GitHub Actions to execute the assignment logic in the future.
Currently, the assignments are done by a
[manually executed script](https://gist.github.com/cmur2/95e320e2e4c3f6208f0f361d882ba572) to
allow for quicker iteration.

## DRI Responsibilities

The DRI for an open Renovate PR is responsible for addressing the dependency upgrade and getting it
merged in a timely manner. They are expected to:

- Get necessary information from the changelog and usages from our code base.
  - Reach out to your peers via `#team-orchestration-cluster` on Slack in case of more questions.
- Do adjustments to the code base to accommodate dependencies with breaking changes, ensuring green
  CI and passing tests.
  - Minor and patch updates should almost always be resolved in this way, without an extra ticket.
- Explore AI-assistance to get breaking changes fixed.
- If needed, create follow-up tickets for major upgrades or big refactoring tasks, and ensure that
  those tickets are scheduled within their team's planning.
- Improve [our Renovate configuration](https://github.com/camunda/camunda/blob/main/.github/renovate.json)
  to make sure updates are as smooth as possible, e.g. by:
  - Applying appropriate grouping of similar dependencies (all React components, similar Maven
    plugins) to reduce the number of PRs.
  - Allow auto-merging if possible.
- Complete the above steps within 3 weeks of the DRI assignment.
  - Reach out to your manager to adjust priorities or find a replacement if the above timeline is
    not possible.

