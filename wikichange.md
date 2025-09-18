## Naming Conventions

Names for CI tests are composed by Github Actions, which is a combination of CI job names. The composed name is shown on PRs and when viewing an individual test run in the Github UI. The composed name should follow the below naming convention to ensure consistency, clarity across the CI system, make it easy to identify the owning team, and which component is being tested.

Names for tests are used to properly sort tests on PRs, making tests easy to find. Both on the PR and the TR test summary page.

For Names of tests in the Unified CI, the name should be structured as follows:
```
CI / <componentName> / <testType> / <testName>
```

- `CI` is the identifier of the Unified CI, and comes from being included in the Unified CI architecture.
- `componentName` is the name of the product, component, or tool being tested or used. This should be things like `Tasklist`, `Operate`, `Optimize`, `Identity`, `Zeebe`, `Lint`, `QA`, `Schema Manager`, or `Docker`. `General` is used when the test is not specific to a single component. `Camunda` is used when the test is specific to the whole Camunda Platform.
- `testType` refers to the type of test being run. It should be descriptive of the nature of the tests being run. This should be things like `Unit`, `Integration`, `E2E`, `Smoke`, `Performance`, or `Build`. `Tool` is used when a 3rd part tool is running a check or test
- `testName` is a descriptive name of what the test is doing. It should be descriptive enough to understand what the test is doing at a glance.
  - This is the last part of the name, and it will show on the Github Test Summary page. use a `-` delimited to separate parts of the name

Here are examples of proper names with this convention:

- Zeebe QA Integration Tests
```
CI / Zeebe / Integration / Zeebe QA ITs
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
