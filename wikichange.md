## Ownership
Each CI test file has an owning team. The owning team can be found either through the `CODEOWNERS` file or on the metadata in the file itself. The `CODEOWNERS` file is organized and broken down by team, any additions to the file should follow that convention. The metadata on a GHA workflow file is used by a scraping tool so that it is easy to gather information about the current state of CI. You can look at the metadata for a quick overview of the owning team, where the tests live, how the test is called, and a description of what the file is actually testing

Metadata follows this structure and is placed at the beginning of a GHA workflow file

```
# <Description of what the GHA is running and what is being tested>
# test location: <The filepath of the tests being run>
# owner: <The name of the owning team>
```

Each testing job sound also print out the ownership information in the logs when it runs. This is to make it easy to identify the owning team when looking at individual test runs.

The log messages should follow this structure:

```
      - name: Log Test Details
        run: echo "This is a ${{ env.TEST_TYPE}} test for ${{ env.TEST_PRODUCT}} owned by ${{ env.TEST_OWNER}}"
```
The `env` variables should be set at the job or at the workflow level for reusability. The values should match the metadata at the top of the file. `TEST_OWNER` should be specific team names. If a test would be owned by all teams, then `General` should be the owner.
```
    env:
      TEST_PRODUCT: Optimize
      TEST_OWNER: Core Features
      TEST_TYPE: Build
```

## Naming Conventions

Names for CI tests are composed by Github Actions, which is a combination of CI job names. The composed name is shown on PRs and when viewing an individual test run in the Github UI. The composed name should follow the below naming convention to ensure consistency, clarity across the CI system, make it easy to identify the owning team, and which component is being tested.

Names for tests are used to properly sort tests on PRs, making tests easy to find. Both on the PR and the TR test summary page.

For Names of tests in the Unified CI, the name should be structured as follows:
```
CI / <componentName> / <testType> / <testName>
```

- `CI` is the identifier of the Unified CI, and comes from being included in the Unified CI architecture.
- `componentName` is the name of the product, component, or tool being tested or used. This should be one of the following: `Tasklist`, `Operate`, `Optimize`, `Identity`, `Zeebe`, `Schema Manager`, or `Docker`. `General` is used when the test is not specific to a single component. `Camunda` is used when the test is specific to the whole Camunda Platform.
- `testType` refers to the type of test being run. It should be descriptive of the nature of the tests being run. This should be things like `Unit`, `Integration`, `E2E`, `Smoke`, `Performance`, or `Build`. `Tool` is used when a 3rd party tool is running a check, test, or static analysis
- `testName` is a descriptive two word name of what the test is doing. It should be descriptive enough to understand what the test is doing at a glance.
  - This is the last part of the name, and it will show on the Github Test Summary page. use a `-` delimited to separate parts of the name
  - The test name should include `Front End` or `Back End` when applicable to clarify which part of the codebase is being tested

Here are examples of proper names with this convention:

- Zeebe QA Integration Tests
```
CI / Zeebe / Integration / Zeebe QA
```
- Operate Front End Linting
```
CI / Operate / Tool / Front End - ESLint
```
- Optimize Back End Unit Tests
  - `(CoreFeatures)` is used as this is the value of the Github Action matrix variable
```
Optimize / Unit (CoreFeatures) / Unit - Back End - Core Features
```

For Names for Legacy tests should be prefixed with `[Legacy] <componentName>` so that Legacy tests are organized and appear together when run on a PR. The rest of the name should try to follow the same convention as above.
