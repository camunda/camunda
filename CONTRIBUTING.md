# Contributing to Zeebe

* [Build Zeebe from source](#build-zeebe-from-source)
* [Report issues or contact developers](#report-issues-or-contact-developers)
* [GitHub Issue Guidelines](#github-issue-guidelines)
  * [Starting on an Issue](#starting-on-an-issue)
  * [Creating a Pull Request](#creating-a-pull-request)
  * [Reviewing a Pull Request](#reviewing-a-pull-request)
  * [Review Emoji Code](#review-emoji-code)
  * [Stale Pull Requests](#stale-pull-requests)
  * [Backporting changes](#backporting-changes)
  * [Commit Message Guidelines](#commit-message-guidelines)
* [Contributor License Agreement](#contributor-license-agreement)
* [Licenses](#licenses)
* [Code of Conduct](#code-of-conduct)

## Build Zeebe from source

Zeebe is a multi-module maven project. To **quickly** build all components,
run the command: `mvn clean install -Dquickly` in the root folder.

> [!NOTE]
> All Java modules in Zeebe are built and tested with JDK 17. Most modules use language level
> 17, exceptions are: zeebe-bpmn-model, zeebe-client-java, zeebe-gateway-protocol,
> zeebe-gateway-protocol-impl, zeebe-protocol and zeebe-protocol-jackson which use language level 8
>
> The Go client and zbctl are built and tested with Go 1.15
>
> The Java and the Go modules are built and tested with Docker 20.10.5 [with IPv6 support](https://docs.docker.com/config/daemon/ipv6/).

For contributions to Zeebe, building quickly is typically sufficient.
However, users of Zeebe are recommended to build the full distribution.

To fully build the Zeebe distribution, run the command: `mvn clean install -DskipTests` in the root folder.
This is slightly slower than building quickly, but ensures the distribution is assembled completely.
The resulting Zeebe distribution can be found in the folder `dist/target`, i.e.

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
  .
```

This is a small overview of the contents of the different modules:
- `util` contains custom implementations of building blocks like an actor scheduler, buffer allocations, metrics. Its parts are used in most of the other modules
- `protocol` contains the SBE definition of the main message protocol
- `bpmn-model` is a Java API for BPMN process definitions used for parsing etc.
- `msgpack-*` is custom msgpack implementation with extensions to evaluate json-path expressions on msgpack objects
- `dispatcher` is a custom implementation of message passing between threads
- `service-container` is a custom implementation to manage dependencies between different services
- `logstreams` is an implementation of a append only log backed by the filesystem
- `transport` is our abstraction over network transports
- `gateway` is the implementation of the gRPC gateway, using our SBE-based protocol to communicate with brokers
- `gateway-protocol` is the gRPC definitions for the Zeebe client-to-gateway protocol
- `zb-db` is our RocksDB wrapper for state management
- `engine`  is the implementation of the event stream processor
- `broker` contains the Zeebe broker which is the server side of Zeebe
- `client-java` contains the Java Zeebe client
- `atomix` contains transport, membership, and consensus algorithms
- `benchmark` contains utilities the team uses to run load tests
- `exporters/elasticsearch-exporter` contains the official Elasticsearch exporter for Zeebe
- `journal` contains the append-only log used by the consensus algorithm
- `snapshots` module abstracting how state snapshots (i.e. `zb-db`) are handled

### Test Execution

Tests can be executed via maven (`mvn verify`) or in your preferred IDE. The Zeebe Team uses mostly [Intellij IDEA](https://www.jetbrains.com/idea/), where we also [provide settings for](https://github.com/camunda/zeebe/tree/main/.idea).

> [!TIP]
> To execute the tests quickly, run `mvn verify -Dquickly -DskipTests=false`.
> The tests will be skipped when using `-Dquickly` without `-DskipTests=false`.

#### Test Troubleshooting

- If you encounter issues (like `java.lang.UnsatisfiedLinkError: failed to load the required native library`) while running the test StandaloneGatewaySecurityTest.shouldStartWithTlsEnabled take a look at https://github.com/camunda/zeebe/issues/10488 to resolve it.

### Build profiling

The development team continues to push for a performant build.
To investigate where the time is spent, you can run your maven command with the `-Dprofile` option.
This will generate a profiler report in the `target` folder.

## Report issues or contact developers

Zeebe uses GitHub issues to organize the development process. If you want to
report a bug or request a new feature feel free to open a new issue on
[GitHub][issues].

If you are reporting a bug, please help to speed up problem diagnosis by
providing as much information as possible. Ideally, that would include a small
[sample project][sample] that reproduces the problem.

If you have a general usage question please ask on the [forum](http://forum.camunda.io/) or [Slack](https://www.camunda.com/slack) channel.

## GitHub Issue Guidelines

Every issue should have a meaningful name and a description which either
describes:
- a new feature with details about the use case the feature would solve or
improve
- a problem, how we can reproduce it and what would be the expected behavior
- a change and the intention how this would improve the system

## Starting on an issue

The `main` branch contains the current in-development state of the project. To work on an issue,
follow the following steps:

1. Check that a [GitHub issue][issues] exists for the task you want to work on.
   If one does not, create one. Refer to the [issue guidelines](#github-issue-guidelines).
2. Check that no one is already working on the issue, and make sure the team would accept a pull request for this topic. Some topics are complex in nature and may touch multiple of [Camunda's Components](https://docs.camunda.io/docs/components/), requiring internal coordination.
3. Checkout the `main` branch and pull the latest changes.

   ```
   git checkout main
   git pull
   ```
4. Create a new branch with the naming scheme `issueId-description`.

   ```
   git checkout -b 123-adding-bpel-support`
   ```
5. Follow the [Google Java Format](https://github.com/google/google-java-format#intellij-android-studio-and-other-jetbrains-ides)
   and [Zeebe Code Style](https://github.com/zeebe-io/zeebe/wiki/Code-Style) while coding.
6. Implement the required changes on your branch and regularly push your
   changes to the origin so that the CI can run. Code formatting, style and
   license header are fixed automatically by running maven. Checkstyle
   violations have to be fixed manually.

   ```
   git commit -am 'feat(broker): bpel support'
   git push -u origin 123-adding-bpel-support
   ```
7. If you think you finished the issue please prepare the branch for reviewing.
   Please consider our [pull requests and code
   reviews](https://github.com/camunda/zeebe/wiki/Pull-Requests-and-Code-Reviews)
   guide, before requesting a review. In general the commits should be squashed
   into meaningful commits with a helpful message. This means cleanup/fix etc
   commits should be squashed into the related commit. If you made refactorings
   it would be best if they are split up into another commit. Rule of thumb is
   that you should think about how a reviewer can best understand your changes.
   Please follow the [commit message guidelines](#commit-message-guidelines).
8. After finishing up the squashing force push your changes to your branch.

   ```
   git push --force-with-lease
   ```

## Creating a pull request

Before opening your first pull request, please have a look at this [guide](https://github.com/camunda/zeebe/wiki/Pull-Requests-and-Code-Reviews#pull-requests).

1. To start the review process create a new pull request on GitHub from your branch to the `main` branch. Give it a meaningful name and describe your changes in the body of the pull request. Lastly add a link to the issue this pull request closes, i.e. by writing in the description `closes #123`. Without referencing the issue, our [changelog generation] will not recognize your PR as a new feature or fix and instead only include it in the list of merged PRs.
2. Assign the pull request to one developer to review, if you are not sure who should review the issue skip this step. Someone will assign a reviewer for you.
3. The reviewer will look at the pull request in the following days and give you either feedback or accept the changes. Your reviewer might use [emoji code](#review-emoji-code) during the reviewing process.
   1. If there are changes requested, address them in a new commit. Notify the reviewer in a comment if the pull request is ready for review again. If the changes are accepted squash them again in the related commit and force push. Then initiate a merge by adding your PR to the merge queue via the `Merge when ready` button.
   2. If no changes are requested, the reviewer will initiate a merge themselves.
4. If there are merge conflicts, the author of the pull request has to [update their pull request by manually rebasing](#updating-a-pull-request).
5. When a merge is initiated, a bot will merge your branch with the latest
   `main` and run the CI on it.
   1. If everything goes well the branch is merged and deleted and the issue
      and pull request are closed.
   2. If there are merge conflicts the author of the pull request has to
      manually rebase `main` into the issue branch and retrigger a merge
      attempt.
   3. If there are CI errors the author of the pull request has to check if
      they are caused by its changes and address them. If they are flaky tests, please
      have a look at this [guide](docs/ci.md#determine-flakiness) on how to handle them.
      Once the CI errors are resolved, a merge can be retried with a comment with
      the content `bors retry`.

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

Before doing your first review, please have a look at this [guide](https://github.com/camunda/zeebe/wiki/Pull-Requests-and-Code-Reviews#code-reviews).

As a reviewer, you are encouraged to use the following [emoji code](#review-emoji-code) in your comments.

The review should result in:
- approving the changes if there are only optional suggestions/minor issues 🔧, throughts 💭, or likes 👍
- requesting changes if there are major issues ❌
- commenting if there are open questions ❓

### Review emoji code

The following emojis can be used in a review to express the intention of a comment.
For example, to distinguish a required change from an optional suggestion.

- 👍 or `:+1:`: This is great! It always feels good when somebody likes your work. Show them!
- ❓ or `:question:`: I have a question. Please clarify.
- ❌ or `:x:`: This has to change. It’s possibly an error or strongly violates existing conventions.
- 🔧 or `:wrench:`: This is a well-meant suggestion or minor issue. Take it or leave it. Nothing major that blocks merging.
- 💭 or `:thought_balloon:`: I’m just thinking out loud here. Something doesn’t necessarily have to change, but I want to make sure to share my thoughts.

_Inspired by [Microsofts emoji code](https://devblogs.microsoft.com/appcenter/how-the-visual-studio-mobile-center-team-does-code-review/#introducing-the-emoji-code)._

### Stale pull requests

If there has not been any activity in your PR after a month it is automatically marked as stale. If it remains inactive we may decide to close the PR.
When this happens and you're still interested in contributing, please feel free to reopen it.

## Backporting changes

Some changes need to be copied to older versions. We use the
[backport](https://github.com/zeebe-io/backport-action) Github Action to automate this process.
Please follow these steps to backport your changes:

1. **Label the pull request** with a backport label (e.g. the label `backport stable/1.0` indicates
   that we want to backport this pull request to the `stable/1.0` branch).
   - if the pull request is _not yet_ merged, it will be automatically backported when bors has
     finished merging the pull request.
   - if the pull request is _already_ merged, create a comment on the pull request that contains
     `/backport` to trigger the automatic backporting.
2. The Github Actions bot comments on the pull request once it finishes:
   - When _successful_, a new backport pull request was automatically created. Simply **approve and
     merge it** by adding a review with a `bors merge` comment.
   - If it _failed_, please follow these **manual steps**:
     1. Locally checkout the target branch (e.g. `stable/1.0`).
     2. Make sure it's up to date with origin (i.e. `git pull`).
     3. Checkout a new branch for your backported changes (e.g. `git checkout -b
        backport-123-to-stable/1.0`).
     4. Cherry pick your changes `git cherry-pick -x <sha-1>...<sha-n>`. You may need to resolve
        conflicts.
     5. Push your cherry-picked changes `git push`.
     6. Create a pull request for your backport branch:
        - Make sure it is clear that this backports in the title (e.g. `[Backport stable/1.0] Title
          of the original PR`).
        - Make sure to change the target of the pull request to the correct branch (e.g.
          `stable/1.0`).
        - Refer to the pull request in the description to link it (e.g. `backports #123`)
        - Refer to any issues that were referenced in the original pull request (e.g. `relates to #99`).

## Commit Message Guidelines

Commit messages use [Conventional Commits](https://www.conventionalcommits.org/en/v1.0.0/#summary) format.

```
<header>
<BLANK LINE> (optional - mandatory with body)
<body> (optional)
<BLANK LINE> (optional - mandatory with footer)
<footer> (optional)
```

Zeebe uses a GitHub Actions workflow checking your commit messages when a pull request is
submitted. Please make sure to address any hints from the bot.

### Commit message header

Examples:

* `docs(reference): add start event to bpmn symbol support matrix`
* `perf(broker): reduce latency in backpressure`
* `feat(clients/go): allow more than 9000 jobs in a single call`

The commit header should match the following pattern:

```
%{type}(%{scope}): %{description}
```

The commit header should be kept short, preferably under 72 chars but we allow a max of 120 chars.

- `type` should be one of:
  - `build`: Changes that affect the build system (e.g. Maven, Docker, etc)
  - `ci`: Changes to our CI configuration files and scripts (e.g. Jenkins, Bors, etc)
  - `deps`: A change to the external dependencies (was already used by Dependabot)
  - `docs`:  A change to the documentation
  - `feat`: A new feature (both internal or user-facing)
  - `fix`: A bug fix (both internal or user-facing)
  - `perf`: A code change that improves performance
  - `refactor`: A code change that does not change the behavior
  - `style`: A change to align the code with our style guide
  - `test`: Adding missing tests or correcting existing tests
- `scope` (optional): name of the changed component (e.g. `engine`, `journal`, `README`)
- `description`: short description of the change in present tense

### Commit message body

Should describe the motivation for the change.
This is optional, but encouraged.
Good commit messages explain what changed AND why you changed it.
See [I've written a clear changelist description](https://github.com/camunda/zeebe/wiki/Pull-Requests-and-Code-Reviews#ive-written-a-clear-changelist-description).

## Contributor License Agreement

You will be asked to sign our Contributor License Agreement when you open a Pull Request. We are not
asking you to assign copyright to us, but to give us the right to distribute
your code without restriction. We ask this of all contributors in order to
assure our users of the origin and continuing existence of the code. You only
need to sign the CLA once.

## Licenses

Zeebe source files are made available under the [Zeebe Community License
Version 1.1](/licenses/ZEEBE-COMMUNITY-LICENSE-1.1.txt) except for the parts listed
below, which are made available under the [Apache License, Version
2.0](/licenses/APACHE-2.0.txt).  See individual source files for details.

Available under the [Apache License, Version 2.0](/licenses/APACHE-2.0.txt):
- Java Client ([clients/java](/clients/java))
- Go Client ([clients/go](/clients/go))
- Exporter API ([exporter-api](/exporter-api))
- Protocol ([protocol](/protocol))
- Gateway Protocol Implementation ([gateway-protocol-impl](/gateway-protocol-impl))
- BPMN Model API ([bpmn-model](/bpmn-model))

If you would like to contribute something, or simply want to hack on the code
this document should help you get started.

## Code of Conduct

This project adheres to the [Camunda Code of Conduct](https://camunda.com/events/code-conduct/).
By participating, you are expected to uphold this code. Please [report](https://camunda.com/events/code-conduct/reporting-violations/)
unacceptable behavior as soon as possible.

[issues]: https://github.com/zeebe-io/zeebe/issues
[forum]: https://forum.zeebe.io/
[slack]: https://www.camunda.com/slack
[sample]: https://github.com/zeebe-io/zeebe-test-template-java
[status]: https://github.com/zeebe-io/zeebe/labels?q=Type
[planned]: https://github.com/zeebe-io/zeebe/labels/Type%3A%20Enhancement
[ready]: https://github.com/zeebe-io/zeebe/labels/Type%3A%20Maintenance
[in progress]: https://github.com/zeebe-io/zeebe/labels/Type%3A%20Bug
[needs review]: https://github.com/zeebe-io/zeebe/labels/Type%3A%20Docs
[type]: https://github.com/zeebe-io/zeebe/labels?q=Type
[enhancement]: https://github.com/zeebe-io/zeebe/labels/Type%3A%20Enhancement
[maintenance]: https://github.com/zeebe-io/zeebe/labels/Type%3A%20Maintenance
[bug]: https://github.com/zeebe-io/zeebe/labels/Type%3A%20Bug
[docs]: https://github.com/zeebe-io/zeebe/labels/Type%3A%20Docs
[question]: https://github.com/zeebe-io/zeebe/labels/Type%3A%20Question
[scope]: https://github.com/zeebe-io/zeebe/labels?q=Scope
[broker]: https://github.com/zeebe-io/zeebe/labels/Scope%3A%20broker
[clients/java]: https://github.com/zeebe-io/zeebe/labels/Scope%3A%20clients%2Fjava
[clients/go]: https://github.com/zeebe-io/zeebe/labels/Scope%3A%20clients%2Fgo

