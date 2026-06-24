# Processes

This page collects processes we follow in the `camunda/camunda` monorepo.

## Green Checks on `main` and `stable/*` Branches

### Purpose

This process ensures that `main` and `stable/*` base branches stay green continuously. Every build failure is unexpected and will be handled as a L1 incident.

We use this process because:

- These branches are our release and integration baseline, so red checks indicate reduced stability and threaten release-readiness.
- For base branches, we expect no failing checks after CI stabilization work; every red check matters.
- Some important scheduled workflows are not part of Unified CI, so Unified CI thresholds alone are not sufficient.

### Approach

We want a fast feedback loop for any failed check on base branches that leads to short-term fixes and mid-term improvement work to prevent recurrence and increase reliability.

High-level steps:

- Detect GHA job failures across `on: push` and `on: schedule` workflows on `main` and `stable/*`.
- Alert immediately via Grafana on any unsuccessful job.
- Create and route incidents to the right owners/medics based on `.codeowners`.
- Drive mitigation (SLA: within 1 day) and resolution via the established incident process.
- Keep base branches green over time instead of reacting ad hoc.

### Implementation

1. **Coverage enforcement:** [CI policy checks](https://github.com/camunda/camunda/blob/main/.github/conftest-unified-ci-rules.rego) ensure that all relevant GHA workflows/jobs submit CI Analytics data.
2. **Detection:** CI Analytics data is [queried regularly by Grafana](https://github.com/camunda/infra-core/blob/stage/camunda-int/kustomize/prod/monitoring/dashboard.int.camunda.com/config/alerts-monorepo-ci.yml) for unsuccessful jobs on `main` and `stable/*` for push/schedule triggers.
3. **Alerting:** Grafana raises `base-branch-unsuccessful-job` [alerts](https://github.com/camunda/infra-core/blob/stage/camunda-int/kustomize/prod/monitoring/dashboard.int.camunda.com/config/alerts-monorepo-ci.yml) for any detected red check. Alerts are enriched with context about failed GHA job links, unsuccessful test cases, and ownership information.
4. **Incident propagation:** Alerts are propagated into incident.io, which groups them by base branch and GHA workflow job name and raises `C8 Monorepo CI` type incidents, severity L1.
5. **Ownership routing:** Incidents are assigned to the responsible medic/owner based on ownership information from [.codeowners file](https://github.com/camunda/camunda/blob/main/.codeowners). If the attribution is incorrect, the assignee is responsible for updating the ownership information.
6. **Response and resolution:** Medics can use `/ci-incident` guidance skill to follow [runbooks](./ci-runbooks.md#base-branch-unsuccessful-job) and the [CI incident management process](#ci-incident-management) to mitigate the problem and fix root causes. Those measures include stopping the bleeding, preventing the problem from happening again and increasing reliability constantly.
7. **Verification:** Job trends and alert behavior are monitored by medic via Grafana until checks are consistently green again.

### Operating Notes

- Complements Unified CI SLOs monitoring by being stricter for base branches: alerts on every failure.
- Focuses on reliability of release-critical (base) branches: covers push and scheduled triggers.
- Remind ICs to prioritize quick mitigation ([with 1 day SLA](#sla)) via incident.io nudges.
- Monitoring via daily Slack overview of open CI incidents with "mitigation overdue" warning.
- If recurring test instability found, evaluate quarantine or other mitigations with clear ownership and follow-up.


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

## Monorepo CI Medic

**Purpose:** Be the central point-of-contact for *problems and alerts* with the CI & automation around the C8 monorepo and drive resolution of *incidents* based on their severity.

**Contact:** Mention **@monorepo-ci-medic** in [#top-monorepo-ci](https://camunda.slack.com/archives/C071KP5BTHB) on Slack.

**Response times:** as quickly as possible

**Handovers:** weekly sync meeting with [handover notes](https://docs.google.com/document/d/1kUUfrsJj-AW77rcx5z1I6kmp1QdCj_hb-Mh8A8YQ_Xg/edit?pli=1&tab=t.xwwilhiltkkd)

### Responsibilities & Process

#### React to alerts in [#monorepo-ci-alerts](https://camunda.slack.com/archives/C07NZ85EF1Q)

- Investigate and address alerts in [#monorepo-ci-alerts](https://camunda.slack.com/archives/C07NZ85EF1Q) channel.
  - If it is an [incident](#incident), follow the [CI incident management process](#ci-incident-management).
- Follow the [linked CI runbooks](./ci-runbooks.md).
- Keep [handover notes](https://docs.google.com/document/d/1kUUfrsJj-AW77rcx5z1I6kmp1QdCj_hb-Mh8A8YQ_Xg/edit?tab=t.7wflm6r3a2ix) updated.

#### Triage problems reported in [#top-monorepo-ci](https://camunda.slack.com/archives/C071KP5BTHB)

- React with :eyes: for Slack threads *that ping you* that you are checking/aware of and :white_check_mark: for resolved threads.
- Verify/reproduce the reported problem to make sure it is not a fluke or misunderstanding:
  - If it is an [incident](#incident), follow the [CI incident management process](#ci-incident-management).
  - Otherwise find [existing related issue](https://github.com/camunda/camunda/issues) or create a new issue to document the problem.
  - Defer strategical decisions to Christian Nicolai after absence.

#### Unhandled FOSSA Licensing Issues reported in [#top-monorepo-ci](https://camunda.slack.com/archives/C071KP5BTHB)

- React with :eyes: for Slack threads *that ping you* that you are checking/aware of and :white_check_mark: for resolved threads.
- Make sure to read the internal documentation: [FOSS in Camunda 8: FOSSA](https://confluence.camunda.com/spaces/HAN/pages/277024795/FOSS+in+Camunda+8+FOSSA) (and the [Handle License Issues](https://confluence.camunda.com/spaces/HAN/pages/277024795/FOSS+in+Camunda+8+FOSSA#FOSSinCamunda8:FOSSA-Handlelicenseissues) part specifically)
- Follow the [Playbook](https://confluence.camunda.com/spaces/HAN/pages/277024795/FOSS+in+Camunda+8+FOSSA#FOSSinCamunda8:FOSSA-PlaybooktoKeeponHandWhenReviewingLicenseIssues) for the first actions to take, including:
  - Curate issues and eliminate false positives.
  - Multi-licensed components
  - Other low-complexity cases with known outcomes
- Escalate to Legal when license terms or obligations require clarification
  - Open a Jira ticket directly from the corresponding FOSSA issue
  - e.g. [issue](https://app.fossa.com/projects/custom%2B50756%2Fcamunda%2Fcamunda%40single-app/refs/branch/main/a23e32efeadc45545ae17ce70ed3908e3b9c5f7a/issues/licensing/10591187?revisionScanId=91861742) & [Jira ticket](https://jira.camunda.com/browse/OSS-24)
- Delegate to the responsible team medics when Engineering actions are needed: use either of the 4 medics:
  - **@core-features-medic** (Operate, Optimize, Tasklist)
  - **@zeebe-medic** (Zeebe Engine)
  - **@data-layer-medic** (Elasticsearch, Opensearch, RDBMS layer)
  - **@identity-medic** (Identity) to delegate.
  - *If you're not sure about which medic to choose, pick the one that makes most sense to you. They will reroute you to the right one.*
- When delegating:
  - clarify distribution scope, remove or replace a dependency, etc. (example: [OJDBC dependency](https://camunda.slack.com/archives/C071KP5BTHB/p1762249747771159?thread_ts=1762161090.318749&cid=C071KP5BTHB))

#### Drive resolution of incidents

- Follow the [CI incident management process](#ci-incident-management).
- Debug problems on GitHub Actions level yourself, involve the stakeholder teams (via their medic) or subject-matter experts for advice on technical details in certain sub-areas:
  - CI Knowledge Base: https://camunda.github.io/camunda/ci
  - Core Features: **@core-features-medic** on Slack (e.g. issues with Operate, Optimize, Tasklist)
  - Core Foundations:
    - Data Layer: **@data-layer-medic** on Slack (e.g. issues with OpenSearch/Elasticsearch tests)
    - Identity: **@identity-medic** on Slack (e.g. issues with Identity and Identity Management)
    - Zeebe: **@zeebe-medic** on Slack
  - Self-Managed:
    - Distro **@distro-medic** on Slack (e.g. Helm chart integration tests)
  - Infra: **@infra-medic** on Slack (e.g. self-hosted runner problems, Vault issues)
- Try to identify a (limited) workaround to unblock users.

## CI Incident Management

This section is for the [camunda/camunda](https://github.com/camunda/camunda/actions) monorepo CI. See all available [incident types](https://confluence.camunda.com/display/HAN/IM+-+Definitions#IMDefinitions-DifferentIncidentTypes).

### Definitions

#### Incident

By *incidents* we refer to problems affecting the C8 Monorepo CI that need to be managed in a structured way.

If *any* of the following is true, an event is an incident:

- Is a C8 release blocked or delayed?
- Are Camundi blocked from creating and merging PRs (unrelated to their code)?
- Is it a large-scale problem affecting many engineers/branches/CI job executions?
- Is the issue unresolved even after an hour of concentrated analysis?

#### Severity

We re-use the generic severity levels from [IM - Service Levels and Criteria](https://confluence.camunda.com/display/HAN/IM+-+Service+Levels+and+Criterias) with the following meanings:

- L1: Problem blocks the [Unified CI](https://camunda.github.io/camunda/ci/#unified-ci) workflow on `main` or `stable/*` or in the merge queue to `main` or `stable/*` branches or blocks release workflows
  - E.g. no PR mergeable (e.g. [1](https://app.incident.io/camunda/incidents/1756), [2](https://app.incident.io/camunda/incidents/1845), [3](https://camunda.slack.com/archives/C071KP5BTHB/p1723559734451389)), 3+ consecutive executions of same job failing incl retries, external service unreachable
- L2: Problem degrades the [Unified CI](https://camunda.github.io/camunda/ci/#unified-ci) workflow on `main` or `stable/*` or in the merge queue to `main` or `stable/*` branches
  - E.g. 3+ consecutive executions of same job taking 50%+ longer, sporadic timeouts, sporadic build failures, external service degraded (e.g. [1](https://app.incident.io/camunda/incidents/1890))
- L3: Used for tracking a prominent flaky test or slow [Unified CI](https://camunda.github.io/camunda/ci/#unified-ci) job until resolution
  - E.g. same test has been flaky in dozens of builds over 1 or multiple days, Unified CI job is slower than runtime SLO and needs speedup

#### Roles

From [IM - Roles and Responsibilities](https://confluence.camunda.com/display/HAN/IM+-+Roles+and+Responsibilities#IMRolesandResponsibilities-TheIncidentResponseTeam):

- Incident Commander (IC)
- Communications Lead (CL)
- Operations Lead (OL)

### Steps

Effective incident management is key to limiting the disruption caused by an incident and restoring normal business operations as quickly as possible.

#### 1. Identification

An incident can be identified by any engineer in the C8 or Infrastructure domain via:

- Alerts in [#monorepo-ci-alerts](https://camunda.slack.com/archives/C07NZ85EF1Q)
- Monorepo CI Medic reacting to requests in [#top-monorepo-ci](https://camunda.slack.com/archives/C071KP5BTHB)
- Issues in [camunda/camunda](https://github.com/camunda/camunda/issues/)

Once an incident is identified, the [Monorepo CI Medic](#monorepo-ci-medic) becomes the incident commander (IC). The IC holds all [roles](#roles) until they delegate to someone else.

##### Incident Command

1. Create an incident as described in [IM - Lifecycle](https://confluence.camunda.com/display/HAN/IM+-+Lifecycle):
   - Quickly estimate the [severity](#severity) of the incident. Be pessimistic; we can always downgrade
2. Record everything in the incident channel and pin a message to persist the contents on the timeline.
3. Take a quick triage of the incident, and remember that you don't need to solve this yourself!
   - Incidents for which no known fix or mitigation can be applied and which fall into the [responsibility of another team](https://confluence.camunda.com/display/HAN/IM+-+Definitions) should be handed over to them as soon as possible. In this case, they become IC, and Monorepo CI Medic stays on standby to support and apply necessary changes to the C8 Monorepo CI.
   - If you feel overwhelmed, it's a good idea to get others involved quickly especially on L1. Particularly consider reaching out to the push engineer - in case of a human-triggered incident, this person often has the most state and should be involved as quickly as possible.

#### 2. Response

Incidents are resolved according to the [Engineering Incident Management Process](https://confluence.camunda.com/display/HAN/Incident+Management) by the [Incident Response Team](https://confluence.camunda.com/display/HAN/IM+-+Roles+and+Responsibilities#IMRolesandResponsibilities-TheIncidentResponseTeam). Helpful resources:

- [Runbooks](./ci-runbooks.md) for CI-related problems
- `/ci-incident` and `/ci-fix-failure` skills for LLM assistance
- generically applicable [Incident Checklist](https://confluence.camunda.com/display/SRE/Incident+Checklist) of the Infra team

##### SLA

The Incident Commander needs to ensure that mitigations are in place with timelines depending on the severity of the incident:

- L1: Within 1 day after opening
- L2: Within 2 days after opening

The Communications Lead should send out periodic updates depending on the severity of the incident:

- L1: Once per hour until mitigated, afterwards once per day until resolved
- L2: Once per day until resolved

#### 3. Follow-Up

##### Incident Command

1. Mark incident as Resolved (see [IM - Lifecycle](https://confluence.camunda.com/display/HAN/IM+-+Lifecycle)) and notify stakeholders.
2. The Monorepo CI Medic is responsible for ensuring the post-incident activities happen:

- Follow the [IM - Post Mortem](https://confluence.camunda.com/display/HAN/IM%3A+Post+Mortem) procedures, especially [Create and schedule a Post Mortem](https://confluence.camunda.com/display/HAN/Create+and+schedule+a+Post+Mortem) if needed
- The post-mortem meeting (**mandatory for L1 severity, otherwise optional**) should occur no more than 5 working days after the incident is resolved. It is recommended to try and asynchronously do a post-mortem if possible. Use [the example meeting invite](https://docs.google.com/document/d/1oVfVST6XPncPPl711CmeDkrU9p0-nNqJHQLeDMBjlpw) to schedule early; you can always use it for root-cause hunting if the incident is not ready for review.
- Assign due dates and assignees to any actionable tasks from the post-mortem meeting.
- Mark incident as Closed (see [IM - Lifecycle](https://confluence.camunda.com/display/HAN/IM+-+Lifecycle)).
