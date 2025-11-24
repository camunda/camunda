# Search Backend Strategy (Test Abstraction)

This package implements a lightweight Strategy pattern used by acceptance tests to run the same
schema manager + Camunda application flows against different search backends (Elasticsearch,
OpenSearch, etc.). It isolates backend bootstrap concerns (containers, credentials, schema
creation, exporter wiring) from the functional test assertions.

## Purpose

Provide a uniform contract so tests can:
- Start a backend (via Testcontainers) with security and schema preconditions.
- Configure the standalone schema manager and the Camunda test application consistently.
- Interact with the backend for verification (count documents/templates, search by key).
- Cleanly tear down resources without bespoke afterEach logic in the test class.

## Core Types

### `SearchBackendStrategy`

Interface that defines the lifecycle and interaction contract. It extends `AutoCloseable` so tests can use try-with-resources to
guarantee container shutdown.

Lifecycle executed by `initialize(...)` in the following order:
1. `startContainer()` – Start & configure the Testcontainers instance (security env, roles, etc.).
2. `createAdminClient()` – Build an administrative client (high privileges) to perform schema ops.
3. `createSchema()` – Create index templates / base schema using exporter schema managers.
4. `configureStandaloneSchemaManager(...)` – Inject properties into `TestStandaloneSchemaManager`.
5. `configureCamundaApplication(...)` – Inject properties / exporters into `TestCamundaApplication`.
6. `schemaManager.start()` + `camunda.start()` – Launch application components.

On `close()` the active container (if any) is stopped.

### Concrete Strategies

- `ElasticsearchBackendStrategy` – Boots an Elasticsearch container with x-pack security, creates
  users & roles, sets exporter + secondary storage properties.
- `OpenSearchBackendStrategy` – Boots an OpenSearch container, configures exporters & properties
  (currently simpler auth model).

## Contract: Interface Methods

```java
void startContainer() throws Exception;
GenericContainer<?> getContainer();
void createAdminClient() throws Exception;
void createSchema() throws Exception;
void configureStandaloneSchemaManager(TestStandaloneSchemaManager schemaManager);
void configureCamundaApplication(TestCamundaApplication camunda);
long countDocuments(String indexPattern) throws Exception;
long countTemplates(String namePattern) throws Exception;
long searchByKey(String indexPattern, long key) throws Exception;
```

### Responsibilities

- A concrete strategy MUST be self‑sufficient: The backend must be
  ready for test interaction (indices, templates, credentials valid).
- `startContainer()` MUST be idempotent per instance; throw if called twice.
- Where backend role assignment is eventually consistent (e.g. Elasticsearch), the strategy SHOULD
  internally await readiness (`Awaitility` usage contained in the strategy, not the test).
- `createSchema()` SHOULD only create what is required for tests; avoid heavy migrations.

## Usage in Tests

```java
@ParameterizedTest
@MethodSource("strategies")
void canUseCamunda(SearchBackendStrategy strategy) throws Exception {
  try (strategy) { // ensures container stop
    strategy.startContainer();
    strategy.createAdminClient();
    strategy.configureStandaloneSchemaManager(schemaManager);
    strategy.configureCamundaApplication(camunda);
    schemaManager.start();
    camunda.start();
    // ... perform test actions & assertions using strategy helpers
    assertThat(strategy.countDocuments("zeebe-record*")) .isGreaterThan(0);
  }
}
```

The parameter source typically returns `Stream.of(new ElasticsearchBackendStrategy(), new OpenSearchBackendStrategy())`.

## Adding a New Backend Strategy (e.g. AWS OpenSearch)

1. Create a class `AwsOpenSearchBackendStrategy implements SearchBackendStrategy`.
2. Implement `startContainer()`:
   - Reuse existing Testcontainers image or custom image (with credentials bootstrap).
   - Set any required env vars / plugins.
3. Implement `createAdminClient()` using the correct connector (may need new connector type).
4. Implement `createSchema()` via exporter schema manager (ensure version compatibility).
5. Implement property configuration methods:
   - Map backend connection details (`url`, `username`, `password`) to
     `camunda.data.secondary-storage.*` + exporter args.
6. Implement document/template/query helpers (API differences: adjust query building).
7. Provide readiness waits if security/role provisioning is eventual.
8. Add strategy to the `Stream` factory method or a new parameterized source.

## Example: Minimal Skeleton for a New Strategy

```java
public final class NewBackendStrategy implements SearchBackendStrategy {
  private GenericContainer<?> container;
  private SomeClient adminClient;
  private String url;

  @Override
  public void startContainer() {
    container = new GenericContainer<>("image:tag")
        .withEnv("VAR", "value")
        .withStartupAttempts(3);
    container.start();
    url = "http://" + container.getHost() + ":" + container.getMappedPort(1234);
  }

  @Override public GenericContainer<?> getContainer() { return container; }

  @Override
  public void createAdminClient() { /* build client & await readiness */ }

  @Override
  public void createSchema() { /* use adminClient */ }

  @Override
  public void configureStandaloneSchemaManager(TestStandaloneSchemaManager mgr) {
    mgr.withProperty("camunda.data.secondary-storage.type", "newbackend")
       .withProperty("camunda.data.secondary-storage.newbackend.url", url);
  }

  @Override
  public void configureCamundaApplication(TestCamundaApplication camunda) {
    camunda.withProperty("camunda.data.secondary-storage.type", "newbackend")
           .withProperty("camunda.data.secondary-storage.newbackend.url", url);
  }

  @Override
  public long countDocuments(String indexPattern) { return 0; }

  @Override
  public long countTemplates(String namePattern) { return 0; }

  @Override
  public long searchByKey(String indexPattern, long key) { return 0; }
}
```

## Potential Future Improvements

- Externalize `configure*` property sets into YAML test resources (`schema/*.yaml`) and load via a
  small helper to reduce imperative property chains.
- Introduce a dedicated factory or test annotation (potentially extending `@MultiDbTest`) to
  replace manual method sources. `@MultiDbTest` currently focuses on provisioning a fully initialized
  database, whereas these strategies additionally need to provision multiple users (admin + app) and
  support running schema managers against an initially blank backend.
- Add health polling abstraction (reusable Awaitility helper) to cut duplication.
- Provide an interface for mapping index/version expectations to assertions.

## FAQ

**Why use try-with-resources?**
`SearchBackendStrategy` implements `AutoCloseable`, ensuring containers are always stopped even if
assertions fail.

---

This documentation is test-scope only and not shipped with production artifacts.
