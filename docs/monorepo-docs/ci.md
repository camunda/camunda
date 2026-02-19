# CI & Automation

This page has information and answers questions around how the [C8 monorepo CI](https://github.com/camunda/camunda/actions) and related tools like [Renovate](#renovate) work. It should serve as a knowledge base including FAQ for Camunda and external contributors.

## Git Branches

* `main`: permanent branch for feature development of next C8 minor version (GitHub default branch)
* `stable/*`: long-lived branches for maintenance of past C8 minor versions (deleted on support end)
* `release*`: short-lived branches for release activities (helps to achieve code freeze) created from `main` or `stable/*`
* _any other branch_: (short-lived) branches for feature development to be merged using Pull Requests, via [merge queues](#github-merge-queue)

### Available SNAPSHOT Artifacts

Maven artifacts are available on [Artifactory](https://artifacts.camunda.com/) and Docker images are available on [DockerHub](https://hub.docker.com/u/camunda/):

* Pushed commits to `main` branch produce:
  * Maven artifacts with version `8.9.0-SNAPSHOT` for all C8 components
  * Docker images with tag `SNAPSHOT` for [Operate](https://hub.docker.com/r/camunda/operate/tags?name=SNAPSHOT), [Tasklist](https://hub.docker.com/r/camunda/tasklist/tags?name=SNAPSHOT), [Zeebe](https://hub.docker.com/r/camunda/zeebe/tags?name=SNAPSHOT)
  * Docker images with tag `8-SNAPSHOT` for [Optimize](https://hub.docker.com/r/camunda/optimize/tags?name=8-SNAPSHOT)
* Pushed commits to `stable/8.8` branch produce:
  * Maven artifacts with version `8.8.0-SNAPSHOT` for Operate, Tasklist, Zeebe
  * Docker images with tag `8.8-SNAPSHOT` for [Optimize](https://hub.docker.com/r/camunda/optimize/tags?name=8.8-SNAPSHOT), [Operate](https://hub.docker.com/r/camunda/operate/tags?name=8.8-SNAPSHOT), [Tasklist](https://hub.docker.com/r/camunda/tasklist/tags?name=8.8-SNAPSHOT), [Zeebe](https://hub.docker.com/r/camunda/zeebe/tags?name=8.8-SNAPSHOT)
* Pushed commits to `stable/8.7` branch produce:
  * Maven artifacts with version `8.7.0-SNAPSHOT` for Operate, Tasklist, Zeebe
  * Docker images with tag `8.7-SNAPSHOT` for [Operate](https://hub.docker.com/r/camunda/operate/tags?name=8.7-SNAPSHOT), [Tasklist](https://hub.docker.com/r/camunda/tasklist/tags?name=8.7-SNAPSHOT), [Zeebe](https://hub.docker.com/r/camunda/zeebe/tags?name=8.7-SNAPSHOT)
* Pushed commits to `stable/optimize-8.7` branch produce:
  * Maven artifacts with version `8.7.0-SNAPSHOT` for **Optimize**
  * Docker images with tag `8.7-SNAPSHOT` for **[Optimize](https://hub.docker.com/r/camunda/optimize/tags?name=8.7-SNAPSHOT)**

## Issue Tracking

All problems, bugs and feature requests regarding the CI of the [C8 monorepo CI](https://github.com/camunda/camunda/actions) are tracked using [GitHub Issues](https://github.com/camunda/camunda/issues).

For visibility and prioritization there is the [Monorepo CI project board](https://github.com/orgs/camunda/projects/115) that tracks high-level issues.

New reports of issues need to be checked against the [GitHub Issues](https://github.com/camunda/camunda/issues) to avoid duplication: new occurrences of existing issues need to reported in comments, otherwise raise a new issue labelled `area/build` or reach out via Slack to the Monorepo CI DRI.

Related resources:

* [Monorepo CI project board (internal)](https://github.com/orgs/camunda/projects/115)
* [Issues labelled area/build](https://github.com/camunda/camunda/issues?q=is%3Aissue+label%3Aarea%2Fbuild)

### Prioritization

Prioritization of issues is done by the Monorepo CI DRI according to severity which follows from these criteria:

1. Impacted functionality:
   * **Highest severity** for issues related to workflows that are:
     * marked as [GitHub required status checks](https://docs.github.com/en/repositories/configuring-branches-and-merges-in-your-repository/managing-protected-branches/about-protected-branches#require-status-checks-before-merging)
     * part of the [GitHub merge queue](#github-merge-queue) to `main`
     * run on `main` branch
     * part of release process
   * Lower severity for any other workflows
2. Amount of users impacted:
   * Generally severity scales with the amount of affected people that interact with the monorepo (Camundi/external contributors)
   * Can be assessed with [CI health](#ci-health-metrics) or on anecdotal level
3. Available workarounds:
   * Severity is lower if a workaround is available, especially if that workaround is easy to use/low effort

Dealing with reported issues that are identified as urgent/**high severity**:

* Communicate the degraded functionality/impact and that there is an ongoing investigation to affected people.
* Debug problems on GitHub Actions level yourself, involve the stakeholder teams (via their medic) or subject matter experts for advice on technical details.
* Try to identify a (limited) workaround to unblock users.
* Communicate any workarounds and resolution of the problem.

### FAQ

**Q: What do I do when I see the CI failing with a seemingly unrelated error?**

> A: Search the open [GitHub Issues](https://github.com/camunda/camunda/issues) with the failure message to see if the problem is known: If you find an issue for the same problem, leave a comment with the new occurrance. Otherwise raise a new issue labelled `area/build` to start tracking that CI failure or reach out via Slack to the Monorepo CI DRI.

**Q: How to deal with flaky tests that block CI?**

> A: Disable the flaky test(s) and comment on existing ticket or create a new one that the flaky test needs to be re-enabled _after_ fixing it. No single test can be more important that the stability of the remaining CI system impacting dozens of developers.

## GitHub Merge Queue

[GitHub Merge Queue](https://github.blog/2023-07-12-github-merge-queue-is-generally-available/) helps automate the Pull Request (PR) merging process by creating a temporary branch for each batch of PRs, running checks against the latest target branch, and merging changes only if the checks pass, ensuring a more streamlined and error-free workflow.

Merge queues exist per branch (one for `main`, one for `stable/8.7`, etc.) in the [C8 monorepo CI](https://github.com/camunda/camunda/actions) and are configured independently via [rulesets](https://docs.github.com/en/repositories/configuring-branches-and-merges-in-your-repository/managing-rulesets/about-rulesets). So different branches can have different [required status checks](https://docs.github.com/en/repositories/configuring-branches-and-merges-in-your-repository/managing-protected-branches/about-protected-branches#require-status-checks-before-merging) to control which CI workflows must be green to allow merging.

Related resources:

* [GitHub documentation on merge queues](https://docs.github.com/en/repositories/configuring-branches-and-merges-in-your-repository/configuring-pull-request-merges/managing-a-merge-queue)
* [Merge queue of `main` branch (PRs and time estimates)](https://github.com/camunda/camunda/queue/main)
* [Ruleset for `main` branch](https://github.com/camunda/camunda/rules?ref=refs%2Fheads%2Fmain)

### FAQ

**Q: Why do we use merge queues instead of manually merging PRs?**

> A: In repositories like the C8 monorepo with a high number of contributing engineers and high development velocity dozens of Pull Requests can get created and merged each day. Avoiding downtimes like waiting for a window to merge PRs [boosts productivity](https://docs.github.com/en/repositories/configuring-branches-and-merges-in-your-repository/configuring-pull-request-merges/managing-a-merge-queue) and allows us to scale.

**Q: Why do we have [required status checks](https://docs.github.com/en/repositories/configuring-branches-and-merges-in-your-repository/managing-protected-branches/about-protected-branches#require-status-checks-before-merging) for PRs and merge queues?**

> A: Automated software tests increase our confidence into delivering a working software product. Required status checks are a way to technically ensure that engineers get early feedback about potential problems. This way we only merge Pull Requests to `main` branch that will not fail those automated tests impacting quality or other engineers. This also helps with automerging dependency updates using [Renovate](#renovate).

**Q: What are the current [required status checks](https://docs.github.com/en/repositories/configuring-branches-and-merges-in-your-repository/managing-protected-branches/about-protected-branches#require-status-checks-before-merging) for PRs and merge queue to `main`?**

> A: You can find the up2date list [here](https://github.com/camunda/camunda/rules?ref=refs%2Fheads%2Fmain):
> * `check-results` check from [ci.yml](https://github.com/camunda/camunda/blob/df3a8296085cb7bbc1065d86e43f2b77bd02b017/.github/workflows/ci.yml#L158) ([Unified CI](#unified-ci))

**Q: Do those [required status checks](https://github.com/camunda/camunda/rules?ref=refs%2Fheads%2Fmain) of `main` guarantee that all commits are green?**

> A: Yes, for the scope of the [Unified CI](#unified-ci) except for an admin bypass of the merge queue in case of incidents.

**Q: My PR had only green checks when I queued it, why was it removed from the merge queue?**

> A: The merge queue creates a temporary branch from the latest target branch (e.g. `main`) with your PR merged and then runs CI again. Your changes could be incompatible with the target branch or CI failed e.g. due to flakiness. Look up the check results for details on the CI failure.

## Unified CI

"Unified CI" is the name of an approach to establish _one_ central CI pipeline that runs checks for _code changes_ of the whole monorepo instead of multiple unrelated, side-by-side pipelines for each component in the monorepo.

### Goals

This central pipeline will use change detection to run checks only when needed thus improving runtime and lowering cost. After migrating, the central CI pipeline will be the only GitHub [required status check](https://docs.github.com/en/repositories/configuring-branches-and-merges-in-your-repository/managing-protected-branches/about-protected-branches#require-status-checks-before-merging) for PRs and merge queue to `main` thus improving UX and preventing edge cases with multiple checks and path filters.

This central pipeline will run on all Pull Requests, the [merge queue](#github-merge-queue) to `main` and on `push` for `main` (and in the future other stable branches). Out of scope are scheduled and release workflows.

This topic is work-in-progress as part of [#17721](https://github.com/camunda/camunda/issues/17721) to migrate remaining workflows once they meet certain criteria (short runtimes under 10 minutes and low flakiness) to the central CI.

A full run of the central CI pipeline should take ideally around 15 minutes with individual jobs only taking 10 minutes of runtime at most.

GitHub Actions pipeline code should should be de-duplicated for the same task and moved out from [ci.yml](https://github.com/camunda/camunda/blob/main/.github/workflows/ci.yml) into other reusable workflows named `ci-<subtask>.yml` or composite actions to keep the `ci.yml` short and lean.

**Non-goal:** Workflows that don't trigger on code changes will not be part of the Unified CI, like scheduled workflows.

### Workflow Inclusion Criteria

Workflows that seek inclusion to the Unified CI (and thus GitHub required status checks) need to fulfill the following criteria and best practices:

* runtime of at most 10 minutes
  * set `timeout-minutes: 10` on the job level
  * parallelize lengthy tasks and/or use bigger runners for compute intensive tasks
* instrumented to emit [CI health metrics](#ci-health-metrics)
* high stability (low flakiness)
  * no CI failures for 10 consecutive builds, see [here how to check it](#how-to-verify-that-a-ci-check-is-robust-and-stable-not-flaky)
  * handle flaky tests gracefully, options:
    1. retry them 3-5 times while staying in the timeout and report them via [detailed test statistics API](https://github.com/camunda/camunda/pull/26715) to [CI health](#ci-health-metrics)
    2. disable them and create a ticket to fix them long-term
* use [Vault for secret management](#ci-secret-management)
* follow the [GitHub Actions Cache strategy](#caching-strategy) for the monorepo
* follow all [CI Security best practices](#ci-security) for the monorepo
* follow the [best practices to handle flaky tests](#flaky-tests)

If the required short runtime _cannot_ be achieved, consider moving long-running tests into nightly jobs or standalone workflows that are no [required status checks](https://docs.github.com/en/repositories/configuring-branches-and-merges-in-your-repository/managing-protected-branches/about-protected-branches#require-status-checks-before-merging) and don't run in the merge queue (to preserve merge velocity).

### Implementation

This section explains how to achieve including a CI check into the Unified CI as a required status check so that it is executed only when relevant files changed in a PR.

To include a workflow [fitting the criteria](#workflow-inclusion-criteria) into the Unified CI **all** of the following steps have to be taken for each job of that workflow:

1. **Change Detection:** Define path filter for all file changes that should trigger the new job in this [composite action](https://github.com/camunda/camunda/blob/main/.github/actions/paths-filter/action.yml). This information is relevant for the next step, make sure to:
   * Add a new `output` to the [composite action](https://github.com/camunda/camunda/blob/main/.github/actions/paths-filter/action.yml) representing the condition when the new job should be triggered.
   * The output have the same name as the new job it triggers.
   * The output condition should reuse existing filters and combine them as needed.
   * If no matching existing filter, add a new one in a step.
   * Adjust the [`detect-changes` job](https://github.com/camunda/camunda/blob/b46d8ba974152d7b2cf1f1f5dac9585657fda7cb/.github/workflows/ci.yml#L25) to re-expose the new `output` under the same name.
2. **CI Check:** Relies on the previous step to run the new job only if relevant files changed. Add the new job defintion to the [ci.yml](https://github.com/camunda/camunda/blob/main/.github/workflows/ci.yml) file, by:
   * Following this pattern:

     ```yaml
     descriptive-job-name:
       # reuse information from change detection on whether to run this job
       if: needs.detect-changes.outputs.descriptive-job-name == 'true'
       needs: [detect-changes]
       runs-on: ubuntu-latest  # or other
       timeout-minutes: 10  # or less
       permissions: {}  # unless GITHUB_TOKEN is needed
       steps:
         - uses: actions/checkout@v4
         #
         # ...ACTUAL CI CHECK STEPS HERE...
         #
         - name: Observe build status
           if: always()
           continue-on-error: true
           uses: ./.github/actions/observe-build-status
           with:
             build_status: ${{ job.status }}
             secret_vault_address: ${{ secrets.VAULT_ADDR }}
             secret_vault_roleId: ${{ secrets.VAULT_ROLE_ID }}
             secret_vault_secretId: ${{ secrets.VAULT_SECRET_ID }}
     ```
   * It is important to depend on the `detect-changes` job and use the newly defined output as a condition.
   * If the new job has many steps, you need to refactor them into a reusable workflow or composite action to keep `ci.yml` lean.
   * Adding [observability for CI health](#ci-health-metrics) is required.
3. **Results Check:** Include the new job as `needs` dependency in [`check-results`](https://github.com/camunda/camunda/blob/b46d8ba974152d7b2cf1f1f5dac9585657fda7cb/.github/workflows/ci.yml#L166) job (required status check). This is needed so that the Unified CI is marked as failure if one of its jobs fails.

Related resources:

* entrypoint and main file for pipeline code of unified CI: [ci.yml](https://github.com/camunda/camunda/blob/main/.github/workflows/ci.yml)
* see for example how [#19423 (actionlint)](https://github.com/camunda/camunda/pull/19423) and [#19436 (Java unit tests)](https://github.com/camunda/camunda/pull/19436) got added to unified CI

## CI Test Files

### Ownership

Each CI test file has an owning team. The owning team can be found either through the `CODEOWNERS` file or on the metadata in the file itself. The `CODEOWNERS` file is organized and broken down by team, any additions to the file should follow that convention. The metadata on a GHA workflow file is used by a scraping tool so that it is easy to gather information about the current state of CI. You can look at the metadata for a quick overview of the owning team, where the tests live, how the test is called, and a description of what the file is actually testing

Metadata follows this structure and is placed at the beginning of a GHA workflow file

```
# <Description of what the GHA is running and what is being tested>
# test location: <The filepath of the tests being run>
# owner: <The name of the owning team>
```

### Legacy CI

"Legacy CI" is a name for CI tests that has not been migrated to the Unified CI. Legacy tests do not meet the [inclusion criteria for Unified CI](#workflow-inclusion-criteria), such as running under 10 minutes.

Tests that are marked as Legacy are to be migrated to Unified CI by the owning team in the future. Once migrated, the test should live inside the `ci.yml` file, or be part of a workflow file that is called by it. The label of "Legacy" should be removed as well

### Consolidated Unit Tests

The Consolidated Unit Test job in the Unified CI runs unit tests by team and component. (For example, Operate tests owned by the Data Layer team). These tests are run by JUnit5 Suites. Each suite selects which tests to run by package. This enables the CI job to run a sub-set of all tests in a module, so that the tests being run are relevant to the owning team. Any new package for tests should be added to the relevant suite.

Suite names must follow a naming convention of `{componentName}{team}TestSuite`. The composite of the component and and the team is used by the CI job to select which component and team to run the tests for. For example, `OperateCoreFeaturesTestSuite` is used to run Core Features tests on Operate

### Naming Conventions

Names for CI tests are composed by Github Actions, which is a combination of CI job names. The composed name is shown on PRs and when viewing an individual test run in the Github UI. The composed name should follow the below naming convention to ensure consistency, clarity across the CI system, make it easy to identify the owning team, and which component is being tested.

For Names of tests in the Unified CI, the name should be structured as follows:

```
CI / <componentName> / [<testType>] <testName> / <ownerName> / ...
```

`testType` can be things like: `UT` for Unit Tests, `IT` for Integration Tests, `Smoke` for smoke tests, etc.

For example, Core Features Unit Tests for Tasklist would be appear as

```
CI / Tasklist / [UT] Core Features / Run Unit Tests
```

Importer Integration Tests for Operate would appear as

```
CI / Operate / [IT] Importer Tests / Data Layer / run-test
```

For Names for Legacy tests should be prefixed with `[Legacy] <componentName>` so that Legacy tests are organized and appear together when run on a PR. The rest of the name should be descriptive of what the test is doing.

## Renovate

[Renovate](https://docs.renovatebot.com/) is a bot and GitHub app that automates dependency updates in software projects by scanning the source code for outdated libraries and applications, then creating Pull Requests to upgrade them to the latest versions, which helps keeping the project secure and up-to-date.

Renovate supports many package ecosystems of which we use e.g. Maven, NPM, Docker and Helm. It can scan multiple branches (e.g. `main`, `stable/8.5`) inside of one repository and raise PRs independently for those.

Renovate is configured via a [JSON configuration file on the `main` branch](https://github.com/camunda/camunda/blob/main/.github/renovate.json). In general we allow Renovate to run and **create PRs at any time** to avoid lagging behind with updates.

We also want Renovate to **automatically merge** dependency updates when CI is green and automated tests are passing. Assuming a nearly complete test coverage the efficiency gains outweigh the risks. This is achieved by Renovate requesting to put every Pull Requests into the [GitHub Merge Queue](#github-merge-queue) - GitHub will then ensure that [required status checks](https://docs.github.com/en/repositories/configuring-branches-and-merges-in-your-repository/managing-protected-branches/about-protected-branches#require-status-checks-before-merging) pass before merging the PR.

We additionally use the [renovate-approve bot](https://github.com/apps/renovate-approve) to circumvent the PR reviewer requirements.

Pull Request labels that have a special meaning for Renovate:

* `dependencies`: added by our Renovate configuration to designate dependency PRs
* `automerge`: added by our Renovate configuration to designate dependency PRs that should get automatically merged
* `area/security`: added by our Renovate configuration to designate dependency PRs that fix a security vulnerability
* `stop-updating`: can be added by humans to tell Renovate to not rebase an open PR anymore (if the change is breaking anyways)

Related resources:

* [Renovate documentation](https://docs.renovatebot.com/)
* [Dependency Dashboard (GH issue)](https://github.com/camunda/camunda/issues/12605)
* [Renovate Dashboard](https://developer.mend.io/github/camunda/camunda)
* [Renovate configuration file on `main` branch](https://github.com/camunda/camunda/blob/main/.github/renovate.json)

### FAQ

**Q: Why do we use Renovate instead of manually looking for dependency updates?**

> A: We automate repetitive and error-prone tasks as much as possible to save valuable Engineering time for solving problems requiring more creativity, e.g. complex major version upgrades of dependencies.

**Q: Why do we use Renovate instead of Dependabot etc.?**

> A: Renovate is more flexible, supports more package ecosystems, has a detailed configuration and already used successfully in other places in Camunda so we can reuse existing experience.

**Q: Why does Renovate attempt to merge a PR with failing status checks?**

> A: Renovate will always try to automerge dependency update PRs since it does not know about CI failures. It is GitHub's task to enforce [required status checks](https://docs.github.com/en/repositories/configuring-branches-and-merges-in-your-repository/managing-protected-branches/about-protected-branches#require-status-checks-before-merging) and reject the merge attempt - as long as no PR with failing status check gets actually merged, everything is working as intended.

**Q: Why does Renovate not detect dependency XYZ?**

> A: Renovate parses and analyzes most well known dependency management files (e.g. `pom.xml`) automatically. Not detecting a dependency can be due to an unrecognized file format, a typo in the name, a bug in Renovate or the dependency being missing from the package ecosystem. This will usually be reported in the Renovate logs.

**Q: How to access the Renovate logs?**

> A: Click on the most recent run in the [Renovate Dashboard](https://developer.mend.io/github/camunda/camunda) and make sure to show debug information.

**Q: Why are updates for dependency XYZ ignored in the [Renovate configuration file](https://github.com/camunda/camunda/blob/main/.github/renovate.json)?**

> A: The reasons for manually ignoring certain updates should be described in the comments. Using `git annotate` to figure out who put the ignore can also be a way to get more details.

## CI Health Metrics

There are hundreds of CI jobs running each day in the [C8 monorepo CI](https://github.com/camunda/camunda/actions) due to high development activity. This scale makes it challenging to assess whether there are any structural problems related to the "CI health" (e.g. reliability issues) that would impact developer productivity.

To achieve that for CI jobs we can collect metrics like build times, build failures and information about the hardware/runner via the [CI Analytics framework](https://confluence.camunda.com/display/HAN/CI+Analytics). See [how to instrument GHA workflows for metrics collection](#metrics-collection). We use the collected data for [visualizations](#visualization) to get an overview of the CI health.

This topic is work-in-progress as part of [#18210](https://github.com/camunda/camunda/issues/18210) to achieve better coverage, collect more different metrics for additional insights and establish a process for dealing with the results.

### Metrics Collection

Any job in any GitHub Actions workflow can be instrumented to collect information about the build status by adding one step at the end, like the following snippet shows:

```yaml
jobs:
  my-solo-job-name:
    steps:
      # initial checkout is required!
      - uses: actions/checkout@v4
      # keep all other steps here, then insert final step:
      - name: Observe build status
        if: always()
        continue-on-error: true
        uses: ./.github/actions/observe-build-status
        with:
          build_status: ${{ job.status }}
          secret_vault_address: ${{ secrets.VAULT_ADDR }}
          secret_vault_roleId: ${{ secrets.VAULT_ROLE_ID }}
          secret_vault_secretId: ${{ secrets.VAULT_SECRET_ID }}
```

Special handling has to be done [for `matrix` jobs](https://docs.github.com/en/actions/using-jobs/using-a-matrix-for-your-jobs) since the job name is not unique among the different matrix builds, see below:

```yaml
jobs:
  my-matrix-job-name:
    strategy:
      matrix:
        identifier: [configurationA, configurationB]
    steps:
      # initial checkout is required!
      - uses: actions/checkout@v4
      # keep all other steps here, then insert final step:
      - name: Observe build status
        if: always()
        continue-on-error: true
        uses: ./.github/actions/observe-build-status
        with:
          job_name: "${{ env.GITHUB_JOB }}/${{ matrix.identifier }}"
          build_status: ${{ job.status }}
          secret_vault_address: ${{ secrets.VAULT_ADDR }}
          secret_vault_roleId: ${{ secrets.VAULT_ROLE_ID }}
          secret_vault_secretId: ${{ secrets.VAULT_SECRET_ID }}
```

Related resources:

* [other examples using `observe-build-status`](https://github.com/search?q=repo%3Acamunda%2Fcamunda+github%2Factions%2Fobserve-build-status&type=code)
* `observe-build-status` uses [`submit-build-status` action](https://github.com/camunda/infra-global-github-actions/tree/main/submit-build-status) (documentation) under the hood

### Visualization

We visualize the collected data using [an internal Grafana dashboard](https://dashboard.int.camunda.com/d/bdmo5l8puugaoc/ci-health-camunda-camunda-monorepo) to analyze for high build failure rates in general and breakdowns per CI job.

Related resources:

* [CI Health Grafana dashboard (internal)](https://dashboard.int.camunda.com/d/bdmo5l8puugaoc/ci-health-camunda-camunda-monorepo)

## CI Secret Management

All GitHub Action workflows of the [C8 monorepo CI](https://github.com/camunda/camunda/actions) must use [Vault](https://confluence.camunda.com/display/HAN/CI+Secrets+Self-Service#CISecretsSelfService-GitHubActions) to retrieve secrets e.g. with the [Hashicorp Vault action](https://github.com/hashicorp/vault-action) as a best practice. Other approaches like [GitHub Action Secrets](https://docs.github.com/en/actions/security-guides/using-secrets-in-github-actions) will be [sunset](https://github.com/camunda/camunda/issues/18211) (outside of bootstrapping connection to Vault).

Historically, [different paths](https://github.com/search?q=repo%3Acamunda%2Fcamunda+%22secret%2Fdata%2F%22&type=code) have been used in Vault to store secrets depending on the managing team, e.g. `products/zeebe/ci` or `products/operate/ci`. This scheme can lead to redundancies in a monorepo and should be aligned for more synergy.

Secrets for the [C8 monorepo CI](https://github.com/camunda/camunda/actions) should be stored in Vault under the path `products/camunda/ci/*`. Manually managed secrets should go into `products/camunda/ci/github-actions`.

Related resources:

* [Vault for GitHub Actions (internal)](https://confluence.camunda.com/display/HAN/CI+Secrets+Self-Service#CISecretsSelfService-GitHubActions)

## CI Self-Hosted Runners

GitHub offers customers to use their own machines to execute GitHub Action workflows via [self-hosted runners](https://docs.github.com/en/actions/hosting-your-own-runners/managing-self-hosted-runners/about-self-hosted-runners). We use this feature in cases when more resources are needed than what GitHub can provide or at a cheaper price. See the [internal documentation](https://confluence.camunda.com/display/HAN/Github+Actions+Self-Hosted+Runners) for what is available.

### Usage Guidelines

How to choose which runner to use for a GHA workflow:

1. Use [GitHub-hosted runners](https://docs.github.com/en/actions/using-github-hosted-runners/using-github-hosted-runners) by default (free for public repositories)
2. Use self-hosted runners (with `-default` name suffix) when a workflow needs:
   1. more resources (memory, CPU) than available on GitHub-hosted runners
   2. ARM CPU architecture

The `-default` self-hosted runners have no durability guarantees which makes them very cheap and the default choice, if GitHub-hosted runners are not sufficient. **Exceptions:** in case of reliability problems one can use the `-longrunning` suffix after approval by Monorepo CI DRI.

## GitHub Actions Cache

Workflows run by GitHub Actions can avoid repeated downloads of tools and dependencies by using [GitHub Actions Cache](https://docs.github.com/en/actions/reference/workflows-and-actions/dependency-caching). This can shorten or avoid download times, make workflow executions faster, more robust and cheaper.

There is a [web UI available and a CLI which can be used to view, analyze and delete corrupted cache entries](https://github.blog/changelog/2023-04-10-manage-caches-in-your-actions-workflows-from-web-interface/).

Important facts about the GHA cache:

* **size**: 10 GiB
  * this is small for a monorepo usecase with Java, NodeJS, and many open PRs
* **access restrictions**: [workflow runs can restore caches created in either the current branch or the default branch](https://docs.github.com/en/actions/reference/workflows-and-actions/dependency-caching#restrictions-for-accessing-a-cache)
  * caches created for `main` are very useful
  * caches created on other branches/Pull Requests are of very limited use, can only be used on subsequent builds of same PR
* **cleaning policy**: GitHub will immediately delete old cache entries when we exceed the 10 GiB total size
  * counter-intuitive: caches from `main` (more useful than those from PRs) are deleted first if they are the oldest

Metrics on cache usage are available in [CI Health Grafana dashboard (internal)](https://dashboard.int.camunda.com/d/bdmo5l8puugaoc/ci-health-camunda-camunda-monorepo).

### Caching Strategy

To make the most efficient use of the limited GHA cache resources available in the monorepo and ensure consistency across many GHA workflows, we follow these guidelines:

1. Docker/BuildKit layers: **don't** write to the GHA cache
2. Java/Maven dependencies: **do** write to the GHA cache **only** from `main` and `stable*` branch builds
3. NPM/Yarn dependencies: **do** write to the GHA cache **only** from `main` and `stable*` branch builds
4. Golang dependencies: **do** write to the GHA cache **only** from `main` and `stable*` branch builds
5. CodeQL automation by GitHub: writes to the GHA cache from Pull Requests (speeds up analysis)

Implementation:

1. Do **not** use `cache-from: type=gha` and `cache-to: type=gha` parameters of [docker/build-push-action](https://docs.docker.com/build/ci/github-actions/cache/).
2. Use [setup-maven-cache](https://github.com/camunda/camunda/tree/main/.github/actions/setup-maven-cache) action.
3. Use [setup-yarn-cache](https://github.com/camunda/infra-global-github-actions/tree/main/setup-yarn-cache) action, see [usage example in #21607](https://github.com/camunda/camunda/pull/21607).
4. No implementation since Golang usage is low.

### Disable cache restoration for a Pull Request

You can temporarily turn off cache restore functionality in a PR by using the `/ci-disable-cache` command as described under [ChatOps](#chatops). This could be useful to test GHA workflows without the caching mechanism. To restore standard functionality, you need to issue the `/ci-enable-cache` command or drop the empty commit.

**Note**: Disabling cache restore mechanism is only possible on PRs.

## CI Security

### Permissions of `GITHUB_TOKEN`

Every GHA workflow job is given a `GITHUB_TOKEN` environment variable with a valid GitHub API token by default. This token can have wide permissions which are unnecessary but open up _attack surface_, reducing security.

**Best Practice:** All GHA workflow jobs must request only actually required [permissions on the `GITHUB_TOKEN`](https://docs.github.com/en/actions/writing-workflows/choosing-what-your-workflow-does/controlling-permissions-for-github_token). Set `permissions: {}` by default and add what is needed.

### Usage of Third Party GitHub Actions

GitHub Actions has a [large ecosystem of existing useful actions](https://github.com/marketplace) from GitHub and third parties such as other companies and individuals. While reusing existing actions avoids code duplication and maintenance effort for Camunda, it increases the _attack surface_ should any of those actions be hacked to perform malicious tasks.

**Best Practice:** To balance utility with risk, all GHA workflows must follow this policy:

* Use the same action for the same (or similar) automation task, see [recipes](https://github.com/camunda/github-actions-recipes).
* Use actions only from trusted sources (GitHub or small set of select 3rd parties, [settings](https://github.com/camunda/camunda/settings/actions)).
* Move actions from Camundi personal accounts to `camunda` for long-term maintenance, or find replacement.

For `camunda/camunda` GHA workflows we use a GitHub feature to [technically limit which actions can be used](https://docs.github.com/en/repositories/managing-your-repositorys-settings-and-features/enabling-features-for-your-repository/managing-github-actions-settings-for-a-repository#allowing-select-actions-and-reusable-workflows-to-run) to:

* Allow actions created by GitHub in the `actions` and `github` organizations.
* Allow actions in any [Camunda GitHub Enterprise organization](https://github.com/enterprises/camunda/organizations) like `camunda`, `bpmn-io`, etc.
* Allow specific actions from 3rd parties that we need (full list see below).

If you need to use a 3rd party action not on the list, [create an issue](#issue-tracking) explaining the motivation and tag the Monorepo CI DRI for further discussion.

<details>
<summary>List of allowed 3rd party actions and reusable workflows</summary>
EnricoMi/publish-unit-test-result-action@*,
YunaBraska/java-info-action@*,
asdf-vm/actions/install@*,
atomicjar/testcontainers-cloud-setup-action@*,
aws-actions/configure-aws-credentials@*,
blombard/move-to-next-iteration@*,
bobheadxi/deployments@*,
browser-actions/setup-firefox@*,
bufbuild/buf-action@*,
cloudposse/github-action-matrix-outputs-read@*,
cloudposse/github-action-matrix-outputs-write@*,
codex-/return-dispatch@*,
dcarbone/install-jq-action@*,
deadsnakes/action@*,
dlavrenuek/conventional-changelog-action@*,
docker/build-push-action@*,
docker/login-action@*,
docker/metadata-action@*,
docker/setup-buildx-action@*,
docker/setup-qemu-action@*,
dorny/paths-filter@*,
fjogeleit/http-request-action@*,
geekyeggo/delete-artifact@*,
golangci/golangci-lint-action@*,
google-github-actions/auth@*,
google-github-actions/get-gke-credentials@*,
google-github-actions/setup-gcloud@*,
hadolint/hadolint-action@*,
hashicorp/vault-action@*,
hoverkraft-tech/compose-action@*,
jamesives/github-pages-deploy-action@*,
joelanford/go-apidiff@*,
jwalton/gh-docker-logs@*,
korthout/backport-action@*,
lewagon/wait-on-check-action@*,
marocchino/sticky-pull-request-comment@*,
mavrosxristoforos/get-xml-info@*,
misiekhardcore/infra-report-action@*,
mshick/add-pr-comment@*,
mxschmitt/action-tmate@*,
ncipollo/release-action@*,
nick-fields/retry@*,
octokit/*@*,
peaceiris/actions-gh-pages@*,
peter-evans/create-or-update-comment@*,
peter-evans/find-comment@*,
peter-evans/slash-command-dispatch@*,
redhat-actions/oc-login@*,
requarks/changelog-action@*,
rodrigo-lourenco-lopes/move-to-current-iteration@*,
rossjrw/pr-preview-action@*,
s4u/maven-settings-action@*,
s4u/setup-maven-action@*,
slackapi/slack-github-action@*,
snyk/actions/setup@*,
stCarolas/setup-maven@*,
stefanzweifel/git-auto-commit-action@*,
teleport-actions/auth-k8s@*,
teleport-actions/setup@*,
test-summary/action@*,
tibdex/github-app-token@*,
wagoid/commitlint-github-action@*,
</details>

## Preview Environments

Engineers can request Preview Environments for specific Pull Requests of the C8 monorepo to be available via a designated URL, to allow more thorough testing and demonstration of the product features before the feature branches are merged into the base branch. For the C8 monorepo the components Identity, Operate, Optimize, Tasklist and Zeebe will get provisioned based on the [camunda-platform](https://github.com/camunda/camunda-platform-helm/) Helm chart.

Assign the [`deploy-preview`](https://github.com/camunda/camunda/labels/deploy-preview) label to any PR to request creation of a Preview Environment. The following base branches are supported, with their corresponding Helm chart versions:

- `main` (uses Helm chart version camunda-platform-8.8-13.x)
- `stable/8.8` (uses Helm chart version camunda-platform-8.8-13.x)
- `stable/8.7` (uses Helm chart version camunda-platform-8.7-12.x)
- `stable/optimize-8.7` (uses Helm chart version camunda-platform-8.7-12.x)

Creation may take a while and a PR comment including an URL and additional info will be sent as notification. The creation/update of a Preview Environment may fail for various reasons including:

* compilation errors on any code in the C8 monorepo
* Docker image build errors
* backwards incompatible changes in the upstream [camunda-platform](https://github.com/camunda/camunda-platform-helm/) Helm chart
* bugs preventing successful startup of any included C8 component

Preview Environments are provisioned on cheap but sometimes less reliable hardware to be cost efficient, and can get automatically stopped after inactivity.

Related resources:

* [internal documentation](https://confluence.camunda.com/display/HAN/Preview+Environments)

## Backporting Guidelines

We want crucial security, stability, cost, and other CI improvements applied to all [long-living Git branches](#git-branches) in the C8 monorepo.

### Why we need CI backports

Changes affecting the CI such as introducing new jobs, new observability features or stability fixes are usually developed first on the `main` [branch](#git-branches). We also have several `stable/*` [branches](#git-branches) living for multiple years to release maintenance updates.

Due to how Git branches work, every `stable/*` branch has its own copy of all GHA workflows at the time of forking. Those GHA workflows receive automated [Renovate updates](#renovate) for actions. But every human-made CI change  needs to be at least [considered for manual backporting](#when-to-backport-ci-changes) to ensure that crucial improvements are on all relevant branches.

### How to backport CI changes

Follow [these instructions](https://github.com/camunda/camunda/blob/main/CONTRIBUTING.md#backporting-changes) to backport PRs with CI changes.

It may be required to resolve Git conflicts when backporting CI changes.

### When to backport CI changes

If the CI change matches one of the following:

* is security-related (incl dependency updates, permissions): **MUST** backport to all `stable/*` branches
  * e.g. related are [CI Security](#ci-security), [secrets](#ci-secret-management)
* is related to cost reduction, increased reliability or observability: **SHOULD** backport to all `stable/*` branches
  * e.g. related are [CI health](#ci-health-metrics), [GHA caching](#caching-strategy), [self-hosted runners](#ci-self-hosted-runners)
* is an in-repository documentation change: **SHOULD** backport if it:
  * updates procedures, guidelines, or troubleshooting steps that AI agents need for accurate assistance on stable branches
  * fixes incorrect information that would mislead AI agents or developers working on stable versions
  * adds new knowledge about practices, tools, or procedures that apply to stable branch development
  * corrects build, test, or development instructions that affect stable branch workflows

### Documentation-specific backporting (monorepo-docs/* folders)

When touching `monorepo-docs/*` folders, use these guidelines:

**DO backport:**
- Critical error corrections in docs
- Security-related documentation updates  
- Fixes preventing user confusion about that specific version

**DON'T backport:**
- New feature documentation
- Reorganization/restructuring changes
- Style/formatting improvements

**Rationale:** Documentation in stable branches serves as AI context for developers working on that specific version. Only backport changes that fix critical errors or security issues, while avoiding feature additions that don't exist in that release.
* is a new CI job for new product feature or test cases: backport **only if** the product feature is backported
* is a new CI feature: backport **only if** required in the ticket
* is related to an `on: schedule` GHA workflow: **no need** to backport, only works on `main`
* is related to [Preview Environments](#preview-environments): **no need** to backport, only supported on `main`

## Slack Notifications

All CI workflows in the [camunda/camunda monorepo](https://github.com/camunda/camunda) must use the ["C8 Monorepo Notifications" Slack app](https://api.slack.com/apps/A086JM3CTB7/incoming-webhooks). Messages to Slack should be send via webhooks. The webhook URLs are [secrets](#ci-secret-management) and stored in [Vault](https://vault.int.camunda.com/ui/vault/secrets/secret/kv/products%2Fcamunda%2Fci%2Fgithub-actions) for each Slack channel.

If you need to send Slack messages to a channel for which no webhook URL exists yet, reach out via Slack to the Monorepo CI DRI to request one. They then will generate a new webhook URL for the ["C8 Monorepo Notifications" Slack app](https://api.slack.com/apps/A086JM3CTB7/incoming-webhooks) and store it in [Vault](https://vault.int.camunda.com/ui/vault/secrets/secret/kv/products%2Fcamunda%2Fci%2Fgithub-actions).

Webhook URL secrets can be retrieved from [Vault](https://vault.int.camunda.com/ui/vault/secrets/secret/kv/products%2Fcamunda%2Fci%2Fgithub-actions) in GitHub Actions workflows like this:

```yaml
job-with-notification:
  steps:
    - uses: actions/checkout@v4

    - name: Import Secrets
      id: secrets
      uses: hashicorp/vault-action@v3
      with:
        url: ${{ secrets.VAULT_ADDR }}
        method: approle
        roleId: ${{ secrets.VAULT_ROLE_ID }}
        secretId: ${{ secrets.VAULT_SECRET_ID }}
        exportEnv: false # we rely on step outputs, no need for environment variables
        secrets: |
          secret/data/products/camunda/ci/github-actions SLACK_MYCHANNELNAME_WEBHOOK_URL;

    - name: Send notification
      uses: slackapi/slack-github-action@v2
      with:
        webhook: ${{ steps.secrets.outputs.SLACK_MYCHANNELNAME_WEBHOOK_URL }}
        webhook-type: webhook-trigger
        # For posting a rich message using Block Kit
        payload: |
          blocks:
            - type: "section"
              text:
                type: "mrkdwn"
                text: "Hello World"
```

## ChatOps

In the [camunda/camunda monorepo](https://github.com/camunda/camunda) certain automated workflows can be triggered by posting comments with commands on GitHub Issues and/or Pull Requests. Those commands are then processed by a [GitHub Actions workflow](https://github.com/camunda/camunda/blob/main/.github/workflows/chatops-command-dispatch.yml).

Available commands:

* `/ci-problems` comment on a Pull Request:
  * Synopsis: Triggers a [script](https://github.com/camunda/camunda/blob/main/.ci/chatops-commands/ci-problems-analyze.sh) that analyzes all CI runs related to that PR for CI failures and posts summary as new PR comment.
  * Use case: Can be used by any engineer to get actionable hints on how to address CI problems in a PR.
  * Capabilities:
    * detect problems with self-hosted runners (incl links to dashboards + Kubernetes logs)
    * pipeline timeouts
    * DockerHub connection problems
    * deep links to GHA logs for generic job failures
* `/ci-disable-cache` comment on a Pull Request:
  * Synopsis: Adds a new label `ci:no-cache` to the list of labels of the Pull Request and creates a new empty commit to trigger a new CI run without cache restoration.
  * Use case: Can be used by any engineer to test workflows run from scratch without cache restoration.
* `/ci-enable-cache` comment on a Pull Request:
  * Synopsis: Removes the `ci:no-cache` label from the list of labels of the Pull Request and creates a new empty commit to trigger a new CI run.
  * Use case: Complements the `/ci-disable-cache` commands and can be used to restore CI regular cache restoration step.

## Flaky tests

Tests can be viewed as "flaky" when they are not consistenly passing although neither the source code, nor the test code, nor the environment has been meaningfully changed.

We should aim to have all tests consistently passing, avoid introducing new flaky tests and implement our [observability tooling](#ci-health-metrics) to detect and improve existing flaky tests. This allows for better developer experience and smoother processes like [automated dependency updates](#renovate).

GitHub Action workflows with Maven testing Java code should use the [flaky-test-extractor-maven-plugin](#flaky-test-extractor-maven-plugin) and report the resulting [detailed flaky test statistics](https://github.com/camunda/camunda/issues/26930) to our [CI health](#ci-health-metrics) database.

Related resources:

* [the Flaky tests dashboard (internal)](https://dashboard.int.camunda.com/d/ae2j69npxh3b4f/flaky-tests-camunda-camunda-monorepo)

### flaky-test-extractor-maven-plugin

Some Maven modules in the monorepo [rerun failing Java tests multiple times](https://maven.apache.org/surefire/maven-surefire-plugin/examples/rerun-failing-tests.html) (e.g. 3 times, configurable) when they fail and use the [flaky-test-extractor-maven-plugin](https://github.com/zeebe-io/flaky-test-extractor-maven-plugin):

* if a test succeeds at least once during the retries, it is classified as "flaky" by this plugin
  * flaky tests get reported via [CI Health metrics](#ci-health-metrics) and can be viewed on [the Flaky tests dashboard](https://dashboard.int.camunda.com/d/ae2j69npxh3b4f/flaky-tests-camunda-camunda-monorepo)
  * **flaky tests do not cause build failures**
* if a test fails on all retries, it is classified as "failed"
  * this will cause the whole build to fail
  * see [the FAQ](#faq) on how to deal with such cases

## License Checks

We use [FOSSA](https://app.fossa.com/) to check dependencies for license compliance with Camunda's policies in order to detect risks early and be aware of licenses of our BOM.

The scan is performed by a GitHub Actions workflows for:

* each tag
* each commit pushed to `main` and `stable*` (8.6+) branches
* each Pull Request opened against the above branches

## Troubleshooting

### How to deal with CI alerts that fire?

Follow the Monorepo CI medic routines and check out the available [CI runbooks](https://github.com/camunda/camunda/wiki/CI-Runbooks) for each alert.

### Why is my CI check failing?

There can be many factors influencing that and it is sometimes hard to find the root caues. Below list should provide guidance:

1. Try to rerun the failing CI check(s) at least to overcome transient problems.
2. If on a Pull Request, consider whether the code changes on that Pull Request might cause the CI check failure.
3. Check if there is an [open issue](#issue-tracking) about that failing CI check, e.g. by searching for the error message.
4. [Ask Copilot about the failure](https://github.blog/changelog/2025-01-15-copilot-users-can-ask-about-a-failed-actions-job-ga/) using the `Explain error` button
   * If this doesn't work, find and google the error message.
5. If the failing CI checks are **not** part of the [Unified CI](#unified-ci), contact their [owner](#ci-test-files) and see if those CI checks are known to be unstable or flaky.
   * Technically, failing CI checks **outside** the Unified CI do not prevent merging a PR. If the owners of those failing CI checks agree, you can still merge.
6. If your PR is removed from the merge queue, check if concurrently there was another PR merged that changes code which your code depends on (e.g. leading to compilation errors in the merge queue).
7. If a check from the Unified CI is failing on `main` or a stable branch, try to find the [first build](https://github.com/camunda/camunda/actions/workflows/ci.yml?query=branch%3Amain) with that failing check and investigate the recently merged code changes.
   * Experience shows that most CI check failures are (indirectly) caused by `camunda/camunda` code changes, and not by external factors like 3rd party services or infrastructure.
   * [CI Health metrics](#ci-health-metrics) can also be used to narrow down the time range, less precise.
8. Reach out on Slack for help!

### How to verify that a CI check is robust and stable, not flaky?

First, create a dedicated branch `YOURBRANCHNAME` which can be used as a reference for running the CI check later.

If you are working on fixing a [flaky test](#flaky-tests), push the code or build pipeline change(s) that you believe remove the flakiness onto that branch.

Is your CI check part of the [Unified CI's](#unified-ci) `ci.yml`?

1. No, but it runs on Pull Requests. Then you have to create a draft PR for `YOURBRANCHNAME` and manually trigger reruns of the check in question.

2. Yes! Then you can use the [GitHub CLI tool](https://cli.github.com/) to start repeated runs.

   _Optional: remove CI checks you are not interested in from the `ci.yml` on `YOURBRANCHNAME` to speed up the execution and save resources._

   Open a new terminal, go to your checkout of `camunda/camunda` and execute in `bash` shell:

   ```bash
   for i in {1..10}; do gh workflow run ci.yml --ref YOURBRANCHNAME; done
   ```

   This loop will take a while (1 hour or more depending on the CI check) so let it run in the background. After it finished, visit https://github.com/camunda/camunda/actions/workflows/ci.yml?query=branch%3AYOURBRANCHNAME and see if there are any failures (indicates lack of robustness).

