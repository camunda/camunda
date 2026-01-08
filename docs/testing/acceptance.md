# Acceptance tests

Acceptance tests (ATs) cover the whole Camunda 8 platform, meaning multiple or even all components at the same time in an integrated manner.

In the following document, we shortly highlight general recommendations and how-tos/examples of how to write them.
This ensures we consistently write such tests and cover all necessary supported environments.

## When to write acceptance tests

1. You should write acceptance tests when implementing a feature and validating the user flow
   1. This comes after writing unit tests and integration tests, for more details please see our [testing guide](/docs/testing.md).
   2. Integration tests might live in a different submodule, where two or more dependencies are tested together
   3. Unit tests live in a module, related to the code of the unit
2. As a regression test, when fixing a bug that impacted the user flow/behavior.

## How to write acceptance tests

With the `@MultiDbTest` annotation, we provide a consistent and easy way to write acceptance tests.

* This reduces the barrier for engineers to write ATs, which is less repetitive and has less boilerplate (especially regarding setup code).
* Tests are executed against multiple supported environments, transparent to the engineer.
* Engineers can fully focus on business logic to test.
* Tests using the `@MultiDbTest` annotation usually execute faster, as they start dependencies just once and cleanly reuse them.

While we want to make it as easy as possible for engineers to write ATs, this comes with some cost and complexity in the related extension.
Additionally, the solution is meant to be composable, reusable, and configurable to cover several use cases or even make it possible to support advanced use cases. For example, to configure secondary storage on its own (if necessary) or handle the test application lifecycle.

This means the test application and MultiDb configuration in the extension are separated on purpose (to configure different secondary storages).

### For simple cases:

* Make use of the `@MultiDbTest` annotation (if possible).
* By default, it will use the `TestStandaloneBroker` class to reduce the scope to a minimum.
* The `@MultiDbTest` annotation will ensure that your test is tagged as a test that should be executed against multiple secondary storage, such as Elasticsearch (ES), OpenSearch (OS), RDBMS, etc.
* The `@MultiDbTest` annotation will mark your test class with `@ExtendsWith` using the `CamundaMultiDBExtension`.
* The execution against different secondary storage is done on our CI.yml GitHub workflow, where separate jobs exist. A specific test property is set for the database type, allowing the extension to configure the test application correctly (specific Exporter, etc.).

### For advanced cases:

* Advanced cases might apply when you want to run the complete platform, need to configure the broker test application, or have to control the application lifecycle on your own.
* To run the complete platform, you can use `TestSimpleCamundaApplication`, which bundles all components together.
* With that, you can use `@RegisterExtension` and `CamundaMultiDBExtension` directly or annotate the test application with `@MultiDbTestApplication`
* This might also be necessary for more sophisticated broker configurations, such as testing different or specific authentications.

### For special cases:

* You might need to derail from the common standard of writing an acceptance test and not use the multi-database extension at all
* You might need direct access to the secondary storage, need to play with the application lifecycle, or not be able to support all secondary storage options.
* Best examples are backup & restore or migration tests.

> [!Important]
>
> :dragon: Be aware of the consequences when derailing from the standard
>
> 1. You need to cover all supported secondary storages for that feature yourself.
> 2. The test infrastructure will likely be more complex and may be duplicated.
> 3. You need to take care of starting dependencies on your own. Thus, the test execution might be impacted and slower than other tests.
> 4. The test will not be consistent with other tests, causing additional cognitive load for other engineers to understand it.

### Examples:

#### Simple use case

**Need:**
* We need to validate a feature end to end, with a small scope running broker, REST API, and exporter

```java
@MultiDbTest
public class ProcessDefinitionQueryTest {

  private static CamundaClient camundaClient; // <- will be injected

  // Some logic to set up tests, like BeforeAll: deploy processes

  @Test
  void shouldSearchByFromWithLimit() {
    // when
    final var resultAll = camundaClient.newProcessDefinitionSearchRequest().send().join();
    final var thirdKey = resultAll.items().get(2).getProcessDefinitionKey();

    final var resultSearchFrom =
        camundaClient.newProcessDefinitionSearchRequest().page(p -> p.limit(2).from(2)).send().join();

    // then
    assertThat(resultSearchFrom.items().size()).isEqualTo(2);
    assertThat(resultSearchFrom.items().stream().findFirst().get().getProcessDefinitionKey())
        .isEqualTo(thirdKey);
  }
```

#### Advanced use case

**Need:**

* We need to configure the broker for specific authentication. For that, we need to make use of `TestStandaloneBroker` or `TestSimpleCamundaApplication`.
* We need to annotate the test applications with `MultiDbTestApplication` to mark the application managed by `CamundaMultiDbExtension`.

**Optional:**

* We might not want to run the test for all secondary storage, as it is not yet supported. We can use the `@DisabledIfSystemProperty` annotation
* We might want to disable the lifecycle management of the annotated test application. This can be done via `@MultiDbTestApplication(managedLifecycle = false)`.
  * This is especially useful if we want to start and stop the test application inside the test (to validate restarts or something similar)

```java
@MultiDbTest
@DisabledIfSystemProperty(named = "test.integration.camunda.database.type", matches = "rdbms")
class SomeIT {

  @MultiDbTestApplication
  static final TestStandaloneBroker BROKER =
      new TestStandaloneBroker().withBasicAuth().withAuthorizationsEnabled();

  private static CamundaClient camundaClient; // <- will be injected

  @Test
  void someTest() {
    // when
    final var processDefinitions = client.newProcessDefinitionSearchRequest().send().join().items();

    // then
    assertThat(processDefinitions).hasSize(2);
    assertThat(processDefinitions.stream().map(p -> p.getProcessDefinitionId()).toList())
        .containsExactlyInAnyOrder("service_tasks_v1", "service_tasks_v2");
  }
}
```

##### Authentication E2E tests

We want to highlight some special features for the authentication tests here.

* For authentication-related tests, users (who are used in tests) can be predefined and annotated with `@UserDefinition`. This allows the extension to collect the related user definitions.
* Authenticated clients can be created via `@Authenticated` annotation, specifying a user to be used.
  * When a client annotated with `Authenticated` is injected (as field or parameter), the respective/referenced user is created.

**Example:**

```java
@MultiDbTest
class ProcessAuthorizationIT {

  @MultiDbTestApplication
  static final TestStandaloneBroker BROKER =
      new TestStandaloneBroker().withBasicAuth().withAuthorizationsEnabled();

  private static final String ADMIN = "admin";
  private static final String RESTRICTED = "restricted-user";

  @UserDefinition
  private static final User ADMIN_USER =
      new User(
          ADMIN,
          "password",
          List.of(
              new Permissions(RESOURCE, CREATE, List.of("*")),
              new Permissions(PROCESS_DEFINITION, READ_PROCESS_DEFINITION, List.of("*"))));

  @UserDefinition
  private static final User RESTRICTED_USER =
      new User(
          RESTRICTED,
          "password",
          List.of(
              new Permissions(
                  PROCESS_DEFINITION,
                  READ_PROCESS_DEFINITION,
                  List.of("service_tasks_v1", "service_tasks_v2"))));

  @BeforeAll
  static void setUp(@Authenticated(ADMIN) final CamundaClient adminClient) {
    final List<String> processes =
        List.of("service_tasks_v1.bpmn", "service_tasks_v2.bpmn", "incident_process_v1.bpmn");
    processes.forEach(process -> deployResource(adminClient, String.format("process/%s", process)));
    waitForProcessesToBeDeployed(adminClient, processes.size());
  }

  @Test
  void searchShouldReturnAuthorizedProcessDefinitions(
      @Authenticated(RESTRICTED) final CamundaClient userClient) {
    // when
    final var processDefinitions = userClient.newProcessDefinitionSearchRequest().send().join().items();

    // then
    assertThat(processDefinitions).hasSize(2);
    assertThat(processDefinitions.stream().map(p -> p.getProcessDefinitionId()).toList())
        .containsExactlyInAnyOrder("service_tasks_v1", "service_tasks_v2");
  }
}
```

## How to run the acceptance tests locally

When running the multi-database acceptance tests locally, they will default to Elasticsearch. The corresponding extension
will [spin up an Elasticsearch test container for local execution](https://github.com/camunda/camunda/blob/2a51cc662a6c59b65a5f455220145017d6be2a1c/qa/util/src/main/java/io/camunda/qa/util/multidb/CamundaMultiDBExtension.java#L212).

Sometimes, we need to validate different secondary storages locally, for example, to reproduce a failure. For that, we can configure the test execution.

If you simply want to test with a specific database, you can just edit the annotation in your IDE, e.g. `@MultiDbTest(DatabaseType.OS)`. Then
run the test as usual, e.g. from the IDE or via Maven.

Alternatively, you can specify the System property `test.integration.camunda.database.type`, which is what the CI jobs do.
In Intellij, you can edit the `Run/Debug configuration` (see this [guide](https://www.jetbrains.com/help/idea/run-debug-configuration-junit.html)) and pass in additional properties and environment variables.
We also provide specific Maven profiles to make this easier.

* For available maven profiles, take a look at the `qa/integration-test/pom.xml`.
* For available database types, look at `io.camunda.qa.util.multidb.CamundaMultiDBExtension#DatabaseType`

> [!Important]
>
> If you want to run against a different secondary storage, you need to spin it up manually, for example, via docker.
> Ensure the container is available under `:9200` for ES or OS. RDBMS uses an embedded H2 database, so no setup is necessary.
>
> [!Note]
> Setting an explicit database type in the `@MultiDbTest` annotation is for local testing only. There is an ArchUnit test which runs in the
> acceptance test module that ensures no test class is annotated with an explicit database type.
>
> Note that setting the system property `test.integration.camunda.database.type` will override the
> database type defined in the annotation.

### Example to run against OpenSearch

Spin up an OpenSearch container via docker (or podman):

```shell
docker run -d -p 9200:9200 \
              -p 9600:9600 \
              -e "discovery.type=single-node" \
              -e "OPENSEARCH_INITIAL_ADMIN_PASSWORD=yourStrongPassword123!" \
              -e "OPENSEARCH_PASSWORD=changeme" \
              -e "DISABLE_SECURITY_PLUGIN=true" \
              opensearchproject/opensearch:2.17.0
```

To run with OpenSearch from your IDE, you have two options:

1. Edit the annotation directly in your IDE, e.g. `@MultiDbTest(DatabaseType.OS)`.
2. Define the following property in your run configuration: `-Dtest.integration.camunda.database.type=os`

Alternatively, if you're running the test via Maven, you can also specify the respective maven
profile `-Pe2e-opensearch-test`.

### Example to run against RDBMS

Our RDBMS set up uses a local H2 database, so there is no database to set up.

To run with RDBMS from your IDE, you have two options:

1. Edit the annotation directly in your IDE, e.g. `@MultiDbTest(DatabaseType.RDBMS)`.
2. Define the following property in your run configuration: `-Dtest.integration.camunda.database.type=rdbms`

## Update tests

Updates tests ensure that the upgrade paths between different versions are compatible.

> [!Note]
> At the moment, only Zeebe supports rolling update tests. With the new unified Orchestration
> Cluster, this means only the primary storage is part of the tests - the secondary storage path
> still does not support rolling updates.

Update tests are implemented in the `zeebe/qa/update-tests` module and are executed in parts with the CI
pipeline, and with some nightly workflows.

### Scenario tests

The bulk of the update tests are implemented as scenarios, and divided in multiple test classes.
The decision to split the tests into multiple classes is based on different set up (e.g. with a
snapshot from an older version, without a snapshot, with an older gateway, etc.), and for
performance purposes: these tests tend to be fairly expensive, so splitting them into multiple
classes allows us to run them in parallel, forked processes.

Most of these classes
([RollingUpdateTest](/zeebe/qa/update-tests/src/test/java/io/camunda/zeebe/test/RollingUpdateTest.java)
excluded) use the [UpdateTestCaseProvider](/zeebe/qa/update-tests/src/test/java/io/camunda/zeebe/test/UpdateTestCaseProvider.java),
which provides a list of scenarios to run per test.

A scenario consists of setting up some original state, running some operations before the upgrade,
and then again after, and verifying the final state.

For example, you might deploy a process definition, update, and then ensure you can create a
process instance from that definition. Or you would create a process instance before, execute it
until some specific point, update, and then ensure you can continue the execution until its
completion.

In general, it's recommended to add new update tests when adding new features in the engine (e.g.
new BPMN symbols).

### Rolling update tests

Rolling updates are tested mainly through [RollingUpdateTest](/zeebe/qa/update-tests/src/test/java/io/camunda/zeebe/test/RollingUpdateTest.java)
in the `qa/update-tests` module. These tests run in three different modes, depending on the environment:

1. **Local**: Checks compatibility between the current version and the first patch of the previous minor.
2. **CI**: Checks compatibility between the current version and all patches of the current and previous minor.
3. **Full**: Checks all supported upgrade paths, i.e. upgrading from any patch to any other patch of the current or next minor.

#### Full test coverage

Testing the full coverage is expensive because it tests more than 1.5k upgrade paths.
We run this test periodically through the [zeebe-version-compatibility.yml workflow].

In order to reduce the time and cost of the full test coverage, we run this test incrementally.
Each run reports a list of tests and version combinations that were tested successfully.
This list is saved as an artifact and used in the next run to skip the tests that were already successful.

##### FAQ

###### How do I check if a version combination was tested?

The full test coverage report is stored as an artifact of the last successful run of the [zeebe-version-compatibility.yml workflow].
You can download the `zeebe-version-compatibility` artifact and search for the version you are interested.
The specific version combination should appear multiple times, once for each test method.

###### How do I run the full test coverage locally?

Set the following environment variables:
- `ZEEBE_CI_CHECK_VERSION_COMPATIBILITY=true`
- `ZEEBE_CI_CHECK_VERSION_COMPATIBILITY_REPORT=~/zeebe-version-compatibility.csv`
Then run the `RollingUpdateTest`s: with `mvn verify -D it.test=io.camunda.zeebe.test.RollingUpdateTest`
If you keep the report file around, another run of this test will continue from where the previous run stopped and only test version combinations that weren't tested successfully yet.

###### I changed the `RollingUpdateTest`, do I need to do anything?

**Adding another test method** to `RollingUpdateTest` will automatically run all this test method for all supported version combinations.

**Changing an existing test method**, requires a reset of the stored coverage report to restart the incremental testing from scratch.
You can do this by navigating to the last successful test run of the [zeebe-version-compatibility.yml workflow] and deleting the `zeebe-version-compatibility` artifact.

###### How does the incremental testing work?

The `RollingUpdateTest` uses our own `CachedTestResultsExtension` JUnit extension.
This extension allows to cache the test results of the parameterized test methods by storing them in a file.
In the `zeebe-version-compatibility.yml` workflow, the system merges the caches of all parallel test runs and stores the result as an artifact.
The next run of the `RollingUpdateTest` restores the cache from the artifact of the last successful run.
Then the `CachedTestResultsExtension` uses the cache to skip tests that already ran.

###### How can I change the parallelism of the full test coverage?

The parallelism is controlled by the `zeebe-version-compatibility.yml` workflow by using a matrix strategy.
You can change the parallelism by adding more entries to the `shards` input of the matrix strategy.
The actual values of the matrix input are not important, only the number of entries is relevant.
For each matrix job, the `RollingUpdateTest` will run with on a separate "shard" of the full set of version combinations.

You may want to increase parallelism after a reset of the coverage report to speed up the testing.
Once the report is complete, you can reduce the parallelism to save resources.

[zeebe-version-compatibility.yml workflow]: https://github.com/camunda/camunda/actions/workflows/zeebe-version-compatibility.yml

