The following would be added to the `CI & Automation` Wiki page as a new section: https://github.com/camunda/camunda/wiki/CI-&-Automation


# CI Test Files

## CI Ownership
Each CI test file has an owning team. The owning team can be found either through the `CODEOWNERS` file or on the metadata in the file itself. The `CODEOWNERS` file is organized and broken down by team, any additions to the file should follow that convention. The metadata on a CI file is used by a scraping tool so that it is easy to gather information about the current state of CI. You can look at the metadata for a quick overview of the owning team, where the tests live, how the test is called, and a description of what the file is actually testing

## Legacy CI
"Legacy CI" is a name for CI tests that has not been migrated to the Unified CI. Legacy tests do not meet the inclusion criteria for Unified CI, such as running under 10 minutes.

Tests that are marked as Legacy are to be migrated to Unified CI by the owning team in the future. Once migrated, the test should live inside the `ci.yml` file, or be part of a workflow file that is called by it. The label of "Legacy" should be removed as well

Names for Legacy tests should be prefixed with `[Legacy] <componentName>` so that Legacy tests are organized and appear together when run on a PR

## Consolidated Unit Tests

The Consolidated Unit Test job in the Unified CI runs unit tests by team and component. (For example, Operate tests owned by the Data Layer team). These tests are run by JUnit5 Suites. Each suite selects which tests to run by package. This enables the CI job to run a sub-set of all tests in a module, so that the tests being run are relevant to the owning team. Any new package for tests should be added to the relevant suite.

Suite names must follow a naming convention of `{componentName}{team}TestSuite`. The composite of the component and and the team is used by the CI job to select which component and team to run the tests for. For example, `OperateCoreFeaturesTestSuite` is used to run Core Features tests on Operate
