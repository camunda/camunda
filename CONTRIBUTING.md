# Contributing to Camunda

We welcome new contributions. We take pride in maintaining and encouraging a friendly, welcoming, and collaborative community.

Anyone is welcome to contribute to Camunda! The best way to get started is to choose an existing [issue](#starting-on-an-issue).

For community-maintained Camunda projects, please visit the [Camunda Community Hub](https://github.com/camunda-community-hub). For connectors and process blueprints, please visit [Camunda Marketplace](https://marketplace.camunda.com/en-US/home) instead.

- [Prerequisites](#prerequisites)
  - [Contributor License Agreement](#contributor-license-agreement)
  - [Code of Conduct](#code-of-conduct)
- [GitHub issue guidelines](#github-issue-guidelines)
  - [Starting on an issue](#starting-on-an-issue)
- [Build and run Camunda from source](#build-and-run-camunda-from-source)
  - [Build](#build)
  - [Run](#run)
  - [Test execution](#test-execution)
    - [Test troubleshooting](#test-troubleshooting)
  - [Build profiling](#build-profiling)
- [Creating a pull request](#creating-a-pull-request)
- [Reviewing a pull request](#reviewing-a-pull-request)
  - [Review emoji code](#review-emoji-code)
  - [Stale pull requests](#stale-pull-requests)
- [Backporting changes](#backporting-changes)
- [Commit message guidelines](#commit-message-guidelines)
  - [Commit message header](#commit-message-header)
  - [Commit message body](#commit-message-body)

## Prerequisites

### Contributor License Agreement

You will be asked to sign our [Contributor License Agreement](https://cla-assistant.io/camunda-community-hub/community) when you open a Pull Request. We are not asking you to assign copyright to us but to give us the right to distribute your code without restriction. We ask this of all contributors to assure our users of the origin and continuing existence of the code.

> [!NOTE]
> In most cases, you will only need to sign the CLA once.

### Code of Conduct

This project adheres to the [Camunda Code of Conduct](https://camunda.com/events/code-conduct/). By participating, you are expected to uphold this code. Please [report](https://camunda.com/events/code-conduct/reporting-violations/) unacceptable behavior as soon as possible.

## GitHub issue guidelines

If you want to report a bug or request a new feature, feel free to open a new issue on [GitHub][issues].

If you report a bug, please help speed up problem diagnosis by providing as much information as possible. Ideally, that would include a small [sample project][sample] that reproduces the problem.

> [!NOTE]
> If you have a general usage question, please ask on the [forum](forum).

Every issue should have a meaningful name and a description that either describes:
- A new feature with details about the use case the feature would solve or
improve
- A problem, how we can reproduce it, and what the expected behavior would be
- A change and the intention of how this would improve the system

Severity and Likelihood (bugs):
To help us prioritize, please also determine the severity and likelihood of the bug. To help you with this, here are the definitions for the options:

Severity:
- *Low:* Having little to no noticeable impact on usage for the user (e.g. log noise)
- *Mid:* Having a noticeable impact on production usage, which does not lead to data loss, or for which there is a known configuration workaround.
- *High:* Having a noticeable impact on production usage, which does not lead to data loss, but for which there is no known workaround, or the workaround is very complex. Examples include issues which lead to regular crashes and break the availability SLA.
- *Critical:* Stop-the-world issue with a high impact that can lead to data loss (e.g. corruption, deletion, inconsistency, etc.), unauthorized privileged actions (e.g. remote code execution, data exposure, etc.), and for which there is no existing configuration workaround.

Likelihood:
- *Low:* rarely observed issue/ rather unlikely edge-case
- *Mid:* occasionally observed
- *High:* recurring issue

### Starting on an issue

The `main` branch contains the current in-development state of the project. To work on an issue, follow these steps:

1. Check that a [GitHub issue][issues] exists for the task you want to work on.
   If one does not, create one. Refer to the [issue guidelines](#github-issue-guidelines).
2. Check that no one is already working on the issue, and make sure the team would accept a pull request for this topic. Some topics are complex and may touch multiple of [Camunda's Components](https://docs.camunda.io/docs/components/), requiring internal coordination.
3. Checkout the `main` branch and pull the latest changes.

   ```
   git checkout main
   git pull
   ```
4. Create a new branch with the naming scheme `issueId-description`.

   ```
   git checkout -b 123-adding-bpel-support
   ```
5. Follow the [Google Java Format](https://github.com/google/google-java-format#intellij-android-studio-and-other-jetbrains-ides)
   and [Zeebe Code Style](https://github.com/camunda/camunda/wiki/Code-Style) while coding.
6. Implement the required changes on your branch and regularly push your
   changes to the origin so that the CI can run. Code formatting, style, and
   license header are fixed automatically by running Maven. Checkstyle
   violations have to be fixed manually.

   ```
   git commit -am 'feat: add BPEL execution support'
   git push -u origin 123-adding-bpel-support
   ```
7. If you think you finished the issue, please prepare the branch for review. Please consider our [pull requests and code reviews](https://github.com/camunda/camunda/wiki/Pull-Requests-and-Code-Reviews) guide, before requesting a review. In general, the commits should be squashed into meaningful commits with a helpful message. This means cleanup/fix etc. commits should be squashed into the related commit. If you made refactorings it would be best if they are split up into another commit. Think about how a reviewer can best understand your changes. Please follow the [commit message guidelines](#commit-message-guidelines).
8. After finishing up the squashing, force push your changes to your branch.

   ```
   git push --force-with-lease
   ```

## Build and run Camunda from source

We are currently working on [architecture streamlining](https://camunda.com/blog/2024/04/simplified-deployment-options-accelerated-getting-started-experience/) to simplify the deployment and build process. While this is in progress, the build instructions are subject to change. The most recent build instructions will always be in this document.

This is a small overview of the contents of this repository:
- `authentication` - configures authentication for Camunda 8
- `bom` - bill of materials (BOM) for importing Zeebe dependencies
- `build-tools` - Zeebe build tools
- `clients` - client libraries
- `dist` - provides the Camunda 8 distributions
- `identity` - component within self-managed Camunda 8 responsible for authentication and authorization
- `licenses` - the Camunda 8 licenses
- `monitor` - Monitoring for self-managed Camunda 8
- `operate` - Monitoring tool for monitoring and troubleshooting processes running in Zeebe
- `parent` - Parent POM for all Zeebe projects
- `qa` - quality assurance for Camunda 8
- `search` - the search clients for Camunda 8 data
- `service` - internal services for Camunda 8
- `spring-boot-starter-sdk` - official SDK for Spring Boot
- `tasklist` - graphical and API application to manage user tasks in Zeebe
- `testing` - testing libraries for processes and process applications
- `webapps-common` - shared code between the Camunda 8 web apps
- `zeebe` - the process automation engine powering Camunda 8

### Build

> [!NOTE]
> All Camunda core modules are built and tested with JDK 21. Most modules use language level 21, exceptions are: camunda-client-java, camunda-process-test-java, zeebe-bpmn-model, zeebe-build-tools, camunda-client-java, zeebe-gateway-protocol zeebe-gateway-protocol-impl, zeebe-protocol, and zeebe-protocol-jackson which use language level 8.

* **Quick build:** To **quickly** build all components for development, run the command: `mvn clean install -Dquickly` in the root folder. This flag is also used to skip Optimize, when building Camunda.
* **Full build:** To build the full distribution for local usage (skipping tests and checks), run the command `mvn clean install -DskipChecks -DskipTests`.
* **Full build without frontends:** To build the full distribution for local usage without frontends (skipping tests), run the command `mvn clean install -DskipChecks -DskipTests -PskipFrontendBuild`.
* **Full build and test:** To fully build and test the Camunda distribution, run the command: `mvn clean install` in the root folder.

If you built a distribution, it can be found in the folder `dist/target`, i.e.

```
dist/target/camunda-zeebe-X.Y.Z-SNAPSHOT.tar.gz
dist/target/camunda-zeebe-X.Y.Z-SNAPSHOT.zip
```

This distribution can be containerized with Docker (i.e. build a Docker image) by running:

```
docker build \
  --tag camunda/zeebe:local \
  --build-arg DISTBALL='dist/target/camunda-zeebe*.tar.gz' \
  --target app \
  --file ./camunda.Dockerfile
  .
```

### Run

Operate, Tasklist, and Optimize use Elasticsearch as its underlying data store. Therefore you have to download and run Elasticsearch. (For information on what version of Elasticsearch is needed, refer to the [Supported Environments documentation](https://docs.camunda.io/docs/next/reference/supported-environments/#component-requirements)).

To run Elasticsearch:
* Download and unzip [Elasticsearch](https://www.elastic.co/downloads/elasticsearch).
* For non-production cases, disable Elasticsearch's security packages by setting the `xpack.security.*` configuration options to false in `ELASTICSEARCH_HOME/config/elasticsearch.yml`.
* Start Elasticsearch by running `ELASTICSEARCH_HOME/bin/elasticsearch` (macOS/Linux) (or `ELASTICSEARCH_HOME\bin\elasticsearch.bat` on Windows).

To start Camunda:
* Extract the `dist/target/camunda-zeebe-X.Y.Z-SNAPSHOT.tar.gz` (or `dist/target/camunda-zeebe-X.Y.Z-SNAPSHOT.zip`) distribution package
* Run `CAMUNDA_HOME/bin/camunda` (macOS/Linux) (or `CAMUNDA_HOME\bin\camunda.bat` on Windows).

If you need to change any of the default configuration values for Camunda, the configuration files are available in `CAMUNDA_HOME/config`. After updating the configuration you need to restart any running services for the changes to take effect.

### Test execution

Tests can be executed via Maven (`mvn verify`) or in your preferred IDE. The Zeebe Team uses mostly [Intellij IDEA](https://www.jetbrains.com/idea/), which we also [provide settings for](https://github.com/camunda/camunda/tree/main/.idea).

> [!TIP]
> To execute the tests quickly, run `mvn verify -Dquickly -DskipTests=false`.
> The tests will be skipped when using `-Dquickly` without `-DskipTests=false`.

#### Test troubleshooting

- If you encounter issues (like `java.lang.UnsatisfiedLinkError: failed to load the required native library`) while running the test StandaloneGatewaySecurityTest.shouldStartWithTlsEnabled take a look at https://github.com/camunda/camunda/issues/10488 to resolve it.

### Build profiling

The development team continues to push for a performant build.
To investigate where the time is spent, you can run your Maven command with the `-Dprofile` option.
This will generate a profiler report in the `target` folder.

## Creating a pull request

Before opening your first pull request, please have a look at this [guide](https://github.com/camunda/camunda/wiki/Pull-Requests-and-Code-Reviews#pull-requests).

1. To start the review process create a new pull request on GitHub from your branch to the `main` branch. Give it a meaningful name and describe your changes in the body of the pull request. Lastly add a link to the issue this pull request closes, i.e. by writing in the description `closes #123`. Without referencing the issue, our [changelog generation] will not recognize your PR as a new feature or fix and instead only include it in the list of merged PRs.
2. Assign the pull request to one developer to review, if you are not sure who should review the issue skip this step. Someone will assign a reviewer for you.
3. The reviewer will look at the pull request in the following days and give you either feedback or accept the changes. Your reviewer might use [emoji code](#review-emoji-code) during the reviewing process.
   1. If there are changes requested, address them in a new commit. Notify the reviewer in a comment if the pull request is ready for review again. If the changes are accepted squash them again in the related commit and force push. Then initiate a merge by adding your PR to the merge queue via the `Merge when ready` button.
   2. If no changes are requested, the reviewer will initiate a merge themselves.
4. If there are merge conflicts, the author of the pull request has to [update their pull request by manually rebasing](#updating-a-pull-request).
5. When a merge is initiated, a bot will merge your branch with the latest
   `main` and run the CI on it.
   1. If everything goes well, the branch is merged and deleted and the issue and pull request are closed.
   2. If there are CI errors, the author of the pull request has to check if they are caused by its changes and address them. If they are flaky tests, please have a look at this [guide](docs/ci.md#determine-flakiness) on how to handle them. Once the CI errors are resolved, a merge can be retried by simply enqueueing the PR again.

## Updating a pull request

If there are merge conflicts on a pull request, the author of the pull request has to update it with the latest changes and resolve the conflicts manually.
You can do this by rebasing your branch onto the target branch (often `main`) and force push.

First fetch the latest changes, and start rebasing on the target branch:

```sh
# Example: target is origin/main
git fetch origin
git rebase origin/main
```

After resolving the conflicts, you can continue the rebase:

```sh
git rebase --continue
```

After the rebase is completed, you can force push your branch:

```sh
git push --force-with-lease
```

We require rebasing instead of using merge commits to update a pull request to:
- keep the history easy to follow,
- allow [automated porting of the pull request](#backporting-changes),
- and avoid automatically requesting reviews from unrelated users due to [CODEOWNERS](https://docs.github.com/en/repositories/managing-your-repositorys-settings-and-features/customizing-your-repository/about-code-owners).

We encourage contributors to regularly rebase their pull requests to minimize the accumulation of merge conflicts.

## Reviewing a pull request

Before doing your first review, please have a look at this [guide](https://github.com/camunda/camunda/wiki/Pull-Requests-and-Code-Reviews#code-reviews).

As a reviewer, you are encouraged to use the following [emoji code](#review-emoji-code) in your comments.

The review should result in:
- **Approving** the changes if there are only optional suggestions/minor issues 🔧, throughts 💭, or likes 👍.
</br > In cases where ❌ suggestions are straightforward to apply from the reviewers perspective, e.g. "a one-liner"-change for which they don't consider another review needed, the reviewer can pre-approve a PR. This unblocks the author to merge right away when they addressed the required changes. In doubt the author can still decide to require another review, or proactively clarify disagreement with the suggestion. The main point here is that pre-approval puts the author back in charge to make a responsible decision on requiring another review or not and if not get the change merged without further delay.
- **Requesting changes** if there are major issues ❌
- **Commenting** if there are open questions ❓

### Review emoji code

The following emojis can be used in a review to express the intention of a comment. For example, to distinguish a required change from an optional suggestion.

- 👍 or `:+1:`: This is great! It always feels good when somebody likes your work. Show them!
- ❓ or `:question:`: I have a question. Please clarify.
- ❌ or `:x:`: This has to change. It’s possibly an error or strongly violates existing conventions.
- 🔧 or `:wrench:`: This is a well-meant suggestion or minor issue. Take it or leave it. Nothing major that blocks merging.
- 💭 or `:thought_balloon:`: I’m just thinking out loud here. Something doesn’t necessarily have to change, but I want to make sure to share my thoughts.

_Inspired by [Microsoft's emoji code](https://devblogs.microsoft.com/appcenter/how-the-visual-studio-mobile-center-team-does-code-review/#introducing-the-emoji-code)._

### Stale pull requests

If there has not been any activity in your PR after a month, we may decide to close the PR.
When this happens and you're still interested in contributing, please feel free to reopen it.

## Backporting changes

Some changes need to be copied to other (often older) versions. We use the [backport](https://github.com/zeebe-io/backport-action) Github Action to automate this process. Please follow these steps to port your changes:

1. **Label the pull request** with a backport label (e.g. the label `backport stable/1.0` indicates that we want to port this pull request to the `stable/1.0` branch).
   - if the pull request is _not yet_ merged, it will be automatically ported when it gets merged.
   - if the pull request is _already_ merged, create a comment on the pull request that contains
     `/backport` to trigger the action.
   - a pull request can have multiple backport labels, in which case the action ports the pull request to each of those branches.
2. The GitHub actions bot comments on the pull request once it finishes:
   - When _successful_, a new backport pull request was automatically created. A bot will automatically approve and merge it when it passes the CI. If it doesn't, you'll need to fix the problems and request a new review.
   - If it _fails_, the action provides instructions in a comment that you need to follow. Once ready, please request a new review.

## Commit message guidelines

Commit messages use [Conventional Commits](https://www.conventionalcommits.org/en/v1.0.0/#summary) format.

```
<header>
<BLANK LINE> (optional - mandatory with body)
<body> (optional)
<BLANK LINE> (optional - mandatory with footer)
<footer> (optional)
```

Camunda uses a GitHub Actions workflow to check your commit messages when a pull request is submitted. Please make sure to address any hints from the bot, otherwise the PR cannot be merged.

**Exception:** In some situations it is not possible to avoid having commits that violate above guidelines, e.g. when merging another PR into the branch of your PR via merge commit or when merging back a release branch. Only in those cases you should explain the motivation and add the `ci:ignore-commitlint` label to your PR to disable the commit message checks.

### Commit message header

Examples:

* `docs: add start event to bpmn symbol support matrix`
* `perf: reduce latency in backpressure`
* `feat: allow more than 9000 jobs in a single call`

The commit header should match the following pattern:

```
%{type}: %{description}
```

The commit header should be kept short, preferably under 72 chars but we allow a max of 120 chars.

- `type` should be one of:
  - `build`: Changes that affect the build system (e.g. Maven, Docker, etc)
  - `ci`: Changes to our CI configuration files and scripts (e.g. GitHub Actions, etc)
  - `deps`: A change to the external dependencies (was already used by Dependabot)
  - `docs`:  A change to the documentation
  - `feat`: A new feature (both internal or user-facing)
  - `fix`: A bug fix (both internal or user-facing)
  - `perf`: A code change that improves performance
  - `refactor`: A code change that does not change the behavior
  - `style`: A change to align the code with our style guide
  - `test`: Adding missing tests or correcting existing tests
- `description`: short description of the change in present tense

### Commit message body

Should describe the motivation for the change. This is optional but encouraged. Good commit messages explain what changed AND why you changed it. See [I've written a clear changelist description](https://github.com/camunda/camunda/wiki/Pull-Requests-and-Code-Reviews#ive-written-a-clear-changelist-description).

[issues]: https://github.com/camunda/camunda/issues
[forum]: https://forum.camunda.io/
[sample]: https://github.com/zeebe-io/zeebe-test-template-java
[clients/java]: https://github.com/camunda/camunda/labels/scope%2Fclients-java
[changelog generation]: https://github.com/camunda/zeebe-changelog

