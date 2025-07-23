The following would replace the existing CI Test Files section in the `CI & Automation` Wiki page https://github.com/camunda/camunda/wiki/CI-&-Automation
It is a copy/paste of the whole section containing edits

# CI Test Files

## Ownership
Each CI test file has an owning team. The owning team can be found either through the `CODEOWNERS` file or on the metadata in the file itself. The `CODEOWNERS` file is organized and broken down by team, any additions to the file should follow that convention. The metadata on a GHA workflow file is used by a scraping tool so that it is easy to gather information about the current state of CI. You can look at the metadata for a quick overview of the owning team, where the tests live, how the test is called, and a description of what the file is actually testing

Metadata follows this structure and is placed at the beginning of a GHA workflow file

```
# <Description of what the GHA is running and what is being tested>
# test location: <The filepath of the tests being run>
# owner: <The name of the owning team>
```

## Legacy CI
"Legacy CI" is a name for CI tests that has not been migrated to the Unified CI. Legacy tests do not meet the [inclusion criteria for Unified CI](https://github.com/camunda/camunda/wiki/CI-&-Automation#workflow-inclusion-criteria), such as running under 10 minutes.

Tests that are marked as Legacy are to be migrated to Unified CI by the owning team in the future. Once migrated, the test should live inside the `ci.yml` file, or be part of a workflow file that is called by it. The label of "Legacy" should be removed as well

## Consolidated Unit Tests

The Consolidated Unit Test job in the Unified CI runs unit tests by team and component. (For example, Operate tests owned by the Data Layer team). These tests are run by JUnit5 Suites. Each suite selects which tests to run by package. This enables the CI job to run a sub-set of all tests in a module, so that the tests being run are relevant to the owning team. Any new package for tests should be added to the relevant suite.

Suite names must follow a naming convention of `{componentName}{team}TestSuite`. The composite of the component and and the team is used by the CI job to select which component and team to run the tests for. For example, `OperateCoreFeaturesTestSuite` is used to run Core Features tests on Operate

## Naming Conventions

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
