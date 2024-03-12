# Rolling Updates

## Testing

### Continuous Integration

Rolling updates are testing mainly through `RollingUpdateTest` in the qa/update-tests module.
These tests run in three different modes, depending on the environment:

1. **Local**: Checks compatibility between the current version and the first patch of the previous minor.
2. **CI**: Checks compatibility between the current version and all patches of the current and previous minor.
3. **Full**: Checks all supported upgrade paths, i.e. upgrading from any patch to any other patch of the current or next minor.

### Full test coverage

Testing the full coverage is expensive because it tests more than 1.5k upgrade paths.
We run this test periodically through the [zeebe-version-compatibility.yml workflow].

In order to reduce the time and cost of the full test coverage, we run this test incrementally.
Each run reports a list of tests and version combinations that were tested successfully.
This list is saved as an artifact and used in the next run to skip the tests that were already successful.

#### FAQ

##### How do I check if a version combination was tested?

The full test coverage report is stored as an artifact of the last successful run of the [zeebe-version-compatibility.yml workflow].
You can download the `zeebe-version-compatibility` artifact and search for the version you are interested.
The specific version combination should appear multiple times, once for each test method.

##### How do I run the full test coverage locally?

Set the following environment variables:
- `ZEEBE_CI_CHECK_VERSION_COMPATIBILITY=true`
- `ZEEBE_CI_CHECK_VERSION_COMPATIBILITY_REPORT=~/zeebe-version-compatibility.csv`
Then run the `RollingUpdateTest`s: with `mvn verify -D it.test=io.camunda.zeebe.test.RollingUpdateTest`
If you keep the report file around, another run of this test will continue from where the previous run stopped and only test version combinations that weren't tested successfully yet.

##### I changed the `RollingUpdateTest`, do I need to do anything?

**Adding another test method** to `RollingUpdateTest` will automatically run all this test method for all supported version combinations.

**Changing an existing test method**, requires a reset of the stored coverage report to restart the incremental testing from scratch.
You can do this by navigating to the last successful test run of the [zeebe-version-compatibility.yml workflow] and deleting the `zeebe-version-compatibility` artifact.

##### How does the incremental testing work?

The `RollingUpdateTest` uses our own `CachedTestResultsExtension` JUnit extension.
This extension allows to cache the test results of the parameterized test methods by storing them in a file.
In the `zeebe-version-compatibility.yml` workflow merges the caches of all parallel test runs and stores the result as an artifact.
The next run of the `RollingUpdateTest` restores the cache from the artifact of the last successful run.
Then the `CachedTestResultsExtension` uses the cache to skip tests that already ran.

##### How can I change the parallelism of the full test coverage?

The parallelism is controlled by the `zeebe-version-compatibility.yml` workflow by using a matrix strategy.
You can change the parallelism by adding more entries to the `shards` input of the matrix strategy.
The actual values of the matrix input are not important, only the number of entries is relevant.
For each matrix job, the `RollingUpdateTest` will run with on a separate "shard" of the full set of version combinations.

You may want to increase parallelism after a reset of the coverage report to speed up the testing.
Once the report is complete, you can reduce the parallelism to save resources.

[zeebe-version-compatibility.yml workflow]: https://github.com/camunda/zeebe/actions/workflows/zeebe-version-compatibility.yml

