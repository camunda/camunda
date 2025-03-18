# Acceptance tests

This module contains acceptance tests for the whole Camunda 8 platform, covering multiple or even all components at the same time in an integrated manner.

In the following document, we shortly highlight general recommendations and how-tos/examples of how to write them.
This ensures we consistently write such tests and cover all necessary supported environments.

## When to write acceptance tests

1. You should write acceptance tests when implementing a feature and validating the user flow
   1. This comes after writing unit tests and integration tests, for more details please see our [testing guide](../docs/testing.md).
   2. Integration tests might live in a different submodule, where two or more dependencies are tested together
   3. Unit tests live in a module, related to the code of the unit
2. As a regression test, when fixing a bug that impacted the user flow/behavior.

## How to write acceptance tests

### For simple cases:

* Make use of the `@MultiDbTest` annotation (if possible).
* By default, it will use the `TestStandaloneBroker` class to reduce the scope to a minimum.
* The `@MutliDbTest` annotation will ensure that your test is tagged as a test that should be executed against multiple secondary storage, such as Elasticsearch (ES), OpenSearch (OS), RDBMS, etc.
* The `@MutliDbTest` annotation will mark your test class with `@ExtendsWith` using the `CamundaMultiDBExtension`.
* The execution against different secondary storage is done on our CI.yml GitHub workflow, where separate jobs exist. A specific test property is set for the database type, allowing the extension to configure the test application correctly (specific Exporter, etc.).

### For advanced cases:

* Advanced cases might apply when you want to run the complete platform or need to configure the broker test application.
* To run the complete platform, you can use `TestSimpleCamundaApplication`, which bundles all components together.
* With that, you can use `@RegisterExtension` and `CamundaMultiDBExtension` directly.
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
    final var resultAll = camundaClient.newProcessDefinitionQuery().send().join();
    final var thirdKey = resultAll.items().get(2).getProcessDefinitionKey();

    final var resultSearchFrom =
        camundaClient.newProcessDefinitionQuery().page(p -> p.limit(2).from(2)).send().join();

    // then
    assertThat(resultSearchFrom.items().size()).isEqualTo(2);
    assertThat(resultSearchFrom.items().stream().findFirst().get().getProcessDefinitionKey())
        .isEqualTo(thirdKey);
  }
```

#### Advanced use case

**Need:**

* We need to configure the broker for specific authentication
* We need to make use of the `@RegisterExtension` to pass in the Broker, pre-configured
* We need to make sure that the test is tagged with `@Tag("multi-db-test")`

**Optional:**

* We might not want to run the test for all secondary storage, as it is not yet supported. We can use the `@DisabledIfSystemProperty` annotation

```java
@Tag("multi-db-test")
@DisabledIfSystemProperty(named = "test.integration.camunda.database.type", matches = "rdbms")
class SomeIT {

  static final TestStandaloneBroker BROKER =
      new TestStandaloneBroker().withBasicAuth().withAuthorizationsEnabled();

  @RegisterExtension
  static final CamundaMultiDBExtension EXTENSION = new CamundaMultiDBExtension(BROKER);

  private static CamundaClient camundaClient; // <- will be injected

  @Test
  void someTest() {
    // when
    final var processDefinitions = client.newProcessDefinitionQuery().send().join().items();

    // then
    assertThat(processDefinitions).hasSize(2);
    assertThat(processDefinitions.stream().map(p -> p.getProcessDefinitionId()).toList())
        .containsExactlyInAnyOrder("service_tasks_v1", "service_tasks_v2");
  }
}
```

##### Authentication E2E tests

We should highlight some special features for the authentication tests here.

* For the tests, users (that are used in tests) can be predefined and annotated with `@UserDefinition`. As such, they are created, and an authenticated client is created later.
* Authenticated clients can be created via `@Authenticated` annotation, specifying a user to be used.
* Mostly, authentication tests can't use the `@MultiDbTest` annotation as of now, as they need to configure the broker or Camunda application specifically
  * As a consequence, they need to be tagged with `@Tag("multi-db-test")` separately to ensure we run tests against all environments
  * They need to use the `@RegisterExtension` to pass in the Broker, pre-configured

**Example:**

```java
@Tag("multi-db-test")
@DisabledIfSystemProperty(named = "test.integration.camunda.database.type", matches = "rdbms")
class ProcessAuthorizationIT {

  static final TestStandaloneBroker BROKER =
      new TestStandaloneBroker().withBasicAuth().withAuthorizationsEnabled();

  @RegisterExtension
  static final CamundaMultiDBExtension EXTENSION = new CamundaMultiDBExtension(BROKER);

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
    final var processDefinitions = userClient.newProcessDefinitionQuery().send().join().items();

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

In Intellij, you can edit the `Run/Debug configuration` (see this [guide](https://www.jetbrains.com/help/idea/run-debug-configuration-junit.html)) and pass in additional properties and environment variables.

To specify the database type, use the following property: `test.integration.camunda.database.type`. We also provide specific Maven profiles to make this easier.

* For available maven profiles, take a look at the `qa/integration-test/pom.xml`.
* For available database types, look at `io.camunda.qa.util.multidb.CamundaMultiDBExtension#DatabaseType`

> [!Important]
>
> If you want to run against a different secondary storage, you need to spin it up manually, for example, via docker.
> Ensure the container is available under `:9200` for ES or OS.

### Example to run against OpenSearch:

Spin up an OpenSearch container via docker (or podman):

```shell
docker run -d -p 9200:9200 \
              -p 9600:9600 \
              -e "discovery.type=single-node" \
              -e "OPENSEARCH_INITIAL_ADMIN_PASSWORD=yourStrongPassword123!" \
              -e "OPENSEARCH_PASSWORD=changeme" \
              -e "DISABLE_SECURITY_PLUGIN=true" \
              opensearchproject/opensearch:latest
```

Define the following property in your run configuration

`-Dtest.integration.camunda.database.type=os`

Alternatively you can also specify the respective maven profile `-Pe2e-opensearch-test`

Run the test.
