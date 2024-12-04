# Contributing to Camunda

We welcome new contributions. We take pride in maintaining and encouraging a friendly, welcoming, and collaborative community.

Anyone is welcome to contribute to Camunda! The best way to get started is to choose an existing [issue](#starting-on-an-issue).

For community-maintained Camunda projects, please visit the [Camunda Community Hub](https://github.com/camunda-community-hub). For connectors and process blueprints, please visit [Camunda Marketplace](https://marketplace.camunda.com/en-US/home) instead.

- [Prerequisites](#prerequisites)
- [GitHub issue guidelines](#github-issue-guidelines)
- [Build and run Camunda from source](#build-and-run-camunda-from-source)
- [Creating a pull request](#creating-a-pull-request)
- [Reviewing a pull request](#reviewing-a-pull-request)
- [Backporting changes](#backporting-changes)
- [Commit message guidelines](#commit-message-guidelines)

## Prerequisites

### Contributor License Agreement

You will be asked to sign our [Contributor License Agreement](https://cla-assistant.io/camunda-community-hub/community) when you open a Pull Request. We are not asking you to assign copyright to us but to give us the right to distribute your code without restriction. We ask this of all contributors to assure our users of the origin and continuing existence of the code.

> [!NOTE]
> In most cases, you will only need to sign the CLA once.

### Code of Conduct

This project adheres to the [Camunda Code of Conduct](https://camunda.com/events/code-conduct/). By participating, you are expected to uphold this code. Please [report](https://camunda.com/events/code-conduct/reporting-violations/) unacceptable behavior as soon as possible.

## GitHub issue guidelines

You can find more details about the issue guidelines in the [GitHub issue guidelines](docs/github-issue-guidelines.md).

## Build and run Camunda from source

We are currently working on [architecture streamlining](https://camunda.com/blog/2024/04/simplified-deployment-options-accelerated-getting-started-experience/) to simplify the deployment and build process. While this is in progress, the build instructions are subject to change. The most recent build instructions will always be in this document.

This is a small overview of the contents of this repository:

- `clients` - client libraries
- `search` - the search clients for Camunda 8 data
- [dist](dist/README.md) - provides the Camunda 8 distributions
- [identity](identity/README.md) - component within self-managed Camunda 8 responsible for authentication and authorization
- [operate](operate/README.md) - Monitoring tool for monitoring and troubleshooting processes running in Camunda 8
- [tasklist](tasklist/README.md) - graphical and API application to manage user tasks in Camunda 8
- [optimize](optimize/README.md) - tool to analyze and optimize processes running in Camunda 8
- [zeebe](zeebe/README.md) - the process automation engine powering Camunda 8
- `qa` - quality assurance for Camunda 8
- [testing](testing/README.md) - testing libraries for processes and process applications

### Build

> [!NOTE]
> All Camunda core modules are built and tested with JDK 21. Most modules use language level 21, exceptions are: camunda-client-java, camunda-process-test-java, zeebe-bpmn-model, zeebe-build-tools, zeebe-client-java, zeebe-gateway-protocol zeebe-gateway-protocol-impl, zeebe-protocol, and zeebe-protocol-jackson which use language level 8.

To **quickly** build all components for development, run the command: `./mvnw clean install -Dquickly` in the root folder.

To build the full distribution for local usage (skipping tests), run the command `./mvnw clean install -DskipChecks -DskipTests`.

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

Tests can be executed via Maven (`./mvnw verify`) or in your preferred IDE. The Zeebe Team uses mostly [Intellij IDEA](https://www.jetbrains.com/idea/), which we also [provide settings for](https://github.com/camunda/camunda/tree/main/.idea).

> [!TIP]
> To execute the tests quickly, run `./mvnw verify -Dquickly -DskipTests=false`.
> The tests will be skipped when using `-Dquickly` without `-DskipTests=false`.

#### Test troubleshooting

- If you encounter issues (like `java.lang.UnsatisfiedLinkError: failed to load the required native library`) while running the test StandaloneGatewaySecurityTest.shouldStartWithTlsEnabled take a look at https://github.com/camunda/camunda/issues/10488 to resolve it.

### Build profiling

The development team continues to push for a performant build.
To investigate where the time is spent, you can run your Maven command with the `-Dprofile` option.
This will generate a profiler report in the `target` folder.

## Pull Request guidelines

Please have a look at the [create pull request guide](docs/pull-request-guidelines.md#creating-a-pull-request) for more information.

For how to review a pull request, please refer to the [review pull request guide](docs/pull-request-guidelines.md#reviewing-a-pull-request).

## Backporting changes

Some changes need to be copied to older versions. We use the [backport](https://github.com/zeebe-io/backport-action) Github Action to automate this process. Please follow these steps to backport your changes:

1. **Label the pull request** with a backport label (e.g. the label `backport stable/1.0` indicates that we want to backport this pull request to the `stable/1.0` branch).
   - if the pull request is _not yet_ merged, it will be automatically backported when it gets merged.
   - if the pull request is _already_ merged, create a comment on the pull request that contains
     `/backport` to trigger the automatic backporting.
2. The GitHub actions bot comments on the pull request once it finishes:
   - When _successful_, a new backport pull request was automatically created. Simply approve the PR
     and enqueue it to the merge queue by clicking the `Merge when ready` button.
   - If it _failed_, please follow these **manual steps**:
     1. Locally checkout the target branch (e.g. `stable/1.0`).
     2. Make sure it's up to date with the origin (i.e. `git pull`).
     3. Checkout a new branch for your backported changes (e.g. `git checkout -b
        backport-123-to-stable/1.0`).
     4. Cherry-pick your changes `git cherry-pick -x <sha-1>...<sha-n>`. You may need to resolve
        conflicts.
     5. Push your cherry-picked changes `git push`.
     6. Create a pull request for your backport branch:
        - Make sure it is clear that this backports in the title (e.g. `[Backport stable/1.0] Title of the original PR`).
        - Make sure to change the target of the pull request to the correct branch (e.g. `stable/1.0`).
        - Refer to the pull request in the description to link it (e.g. `backports #123`)
        - Refer to any issues that were referenced in the original pull request (e.g. `relates to #99`).

## Commit message guidelines

More information on this topic can be found at [Commit message guidelines](docs/commit-message-guidelines.md).

[issues]: https://github.com/camunda/camunda/issues
[forum]: https://forum.camunda.io/
[sample]: https://github.com/zeebe-io/zeebe-test-template-java
[clients/java]: https://github.com/camunda/camunda/labels/scope%2Fclients-java
[changelog generation]: https://github.com/zeebe-io/zeebe-changelog
