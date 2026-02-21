# Integration Tests

> Back to [Testing Strategy](./README.md)

## Definition

An integration test verifies the interaction between two or more real components — typically your code with a real database, search engine, or message broker. External dependencies run in Docker via TestContainers.

## Rules

1. **Use TestContainers** — never depend on pre-installed infrastructure. Tests must be self-contained
2. **Use the `TestSearchContainers` factory** (`zeebe/test-util`) for all Elasticsearch/OpenSearch containers — this is the single source of truth for container image versions
3. **Prefer singleton containers** — use `static @Container` with `@Testcontainers` to share a container across all tests in a class. Starting a new container per test is wasteful (~5-10 seconds per start)
4. **Clean up between tests** — truncate data, reset indexes, or use unique prefixes. Never rely on test execution order
5. **File naming** — integration tests MUST end with `IT.java` (e.g., `UserServiceIT.java`). This ensures Failsafe (not Surefire) executes them
6. **Timeout** — individual integration tests should complete in < 30 seconds. If a test needs more, it likely tests too much
7. **Use `Awaitility`** for eventual consistency — never `Thread.sleep()`

## Gold Standard Example

From `zeebe/exporters/elasticsearch-exporter/src/test/java/io/camunda/zeebe/exporter/ElasticsearchExporterIT.java`:

```java
@Testcontainers
final class ElasticsearchExporterIT {

  @Container
  private static final ElasticsearchContainer CONTAINER =
      TestSearchContainers.createDefeaultElasticsearchContainer()
          .withEnv("action.destructive_requires_name", "false");

  @BeforeEach
  public void beforeEach(final TestInfo testInfo) {
    // Only recreate indexes when the test method changes (performance optimization)
    final String currentTestMethod = getTestMethod(testInfo);
    if (!currentTestMethod.equals(previousTestMethod)) {
      if (previousTestMethod != null) {
        deletedIndices();
      }
      setup();
      previousTestMethod = currentTestMethod;
    }
  }
}
```

**Why this is the standard:**
- Singleton container (`static @Container`) — started once for the class
- `TestSearchContainers` factory for consistent versioning
- Smart cleanup: only resets state when the test method changes
- Self-contained: no dependency on external infrastructure

## TestContainers Patterns

**Singleton container (preferred for most cases):**
```java
@Testcontainers
class MyServiceIT {
  @Container
  private static final ElasticsearchContainer ES =
      TestSearchContainers.createDefeaultElasticsearchContainer();
}
```

**Network-aware multi-container (for cross-service tests):**
```java
class CrossServiceIT {
  private Network network;
  private final List<GenericContainer<?>> containers = new ArrayList<>();

  @BeforeEach
  void setUp() {
    network = Network.newNetwork();
  }

  @AfterEach
  void tearDown() {
    containers.forEach(GenericContainer::stop);
    network.close();
  }
}
```

Reference: `dist/src/test/java/io/camunda/application/AbstractCamundaDockerIT.java`

## Spring Integration Tests

When Spring context is required, use the narrowest possible slice:

| Annotation | Use When | Context Size |
|-----------|----------|-------------|
| `@WebMvcTest(Controller.class)` | Testing a single REST controller | Minimal (web layer only) |
| `@DataJpaTest` | Testing repository/DAO layer | JPA layer only |
| `@SpringBootTest(classes = {SpecificConfig.class})` | Testing with specific beans only | Specified classes only |
| `@SpringBootTest` (full) | **Last resort** — only for acceptance tests | Full application context |
