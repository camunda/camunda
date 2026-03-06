```yaml
---
applyTo: "**/*.test.*,**/*Test.java,**/*IT.java,**/src/test/**"
---
```
# Testing Conventions

## Frameworks and Libraries

- **Test framework**: JUnit 5 (Jupiter) v6.0.3; legacy JUnit 4 still in some engine tests
- **Assertions**: AssertJ v3.27.7 exclusively — never use JUnit or Hamcrest assertions
- **Mocking**: Mockito v5.22.0; use `@MockitoBean` for Spring test bean overrides
- **Async assertions**: Awaitility — never use `Thread.sleep`
- **Containers**: Testcontainers v2.0.3 for ES, OpenSearch, PostgreSQL, Keycloak
- **Architecture**: ArchUnit for structural validation
- **Property-based**: jqwik v1.9.3

## File Location and Naming

- Unit tests: same module as source, `src/test/java`, same package — suffix `*Test`
- Integration tests: same module or `qa/`, suffix `*IT`
- Acceptance tests: `qa/acceptance-tests/src/test/java`
- ArchUnit tests: `qa/`, suffix `*ArchTest`
- Maven Surefire runs `*Test` classes; Failsafe runs `*IT` classes

## Test Structure

Use `// given`, `// when`, `// then` comment sections in every test:
```java
@Test
void shouldCreateUser() {
  // given
  final var request = new CreateUserRequest("alice");

  // when
  final var result = service.createUser(request);

  // then
  assertThat(result.name()).isEqualTo("alice");
}
```

- Prefix test methods with `should` (e.g., `shouldRejectInvalidKey`)
- Test one behavior per method — avoid `And`/`Or` in test names
- Use `@Nested` classes to group tests by feature/tool (especially MCP tool tests)
- Repetition in tests is acceptable for readability

## Engine Tests (Zeebe)

### Processor Tests (JUnit 4 with EngineRule)
```java
@Rule public final EngineRule engine = EngineRule.singlePartition();
@Rule public final TestWatcher watcher = new RecordingExporterTestWatcher();
```
- Use `EngineRule` for in-memory engine tests (see `zeebe/engine/src/test/java/.../EngineRule.java`)
- Use `RecordingExporter` to query processed records — always add `.limit()` or `.getFirst()` to avoid slow queries
- Use custom test clients from `EngineRule` (e.g., `engine.job()`, `engine.user()`)
- See `zeebe/engine/.../JobCompleteAuthorizationTest.java`

### State/Applier Tests (JUnit 5 with ProcessingStateExtension)
- Use `ProcessingStateExtension` for testing `State` and `EventApplier` classes
- See `zeebe/engine/.../UserStateTest.java`

## MCP Tool Tests

Extend `ToolsTest` base class (`gateways/gateway-mcp/src/test/java/.../ToolsTest.java`):
```java
@ExtendWith(MockitoExtension.class)
@ContextConfiguration(classes = {ProcessDefinitionTools.class})
class ProcessDefinitionToolsTest extends ToolsTest {
  @MockitoBean private ProcessDefinitionServices service;
  @Autowired private ObjectMapper objectMapper;

  @BeforeEach
  void mockApiServices() {
    mockApiServiceAuthentication(service);
  }

  @Nested
  class GetProcessDefinition {
    @Test
    void shouldGetProcessDefinitionByKey() { ... }
  }
}
```

Validate in each `@Nested` tool class:
1. Happy-path: assert all fields from example entity via protocol model (`objectMapper.convertValue(result.structuredContent(), ...)`)
2. Error mapping: service throws → result has `ProblemDetail`
3. Bean validation: negative key → error text
4. Search filter/sort/page mapping: use `ArgumentCaptor` to verify query
5. Date range filters: verify operators and parsed `OffsetDateTime`

Create helper methods like `assertExampleProcessDefinitionResult()` to reuse assertions across get/search tests.

Run scoped: `./mvnw -pl gateways/gateway-mcp -am test -DskipITs -DskipChecks -Dtest=ProcessDefinitionToolsTest -T1C`

## Acceptance Tests (@MultiDbTest)

```java
@MultiDbTest
public class ProcessDefinitionQueryTest {
  private static CamundaClient camundaClient; // injected by extension

  @Test
  void shouldSearchByFromWithLimit() { ... }
}
```

- Use `@MultiDbTest` annotation for E2E tests — runs against ES, OpenSearch, and RDBMS transparently
- Extension provides `CamundaClient` via field or parameter injection
- Use `Awaitility.await()` to wait for data export before asserting
- For auth tests: `@UserDefinition` for user setup, `@Authenticated("user")` for scoped clients
- For advanced config: `@MultiDbTestApplication` on `TestStandaloneBroker` field
- Never hard-code `@MultiDbTest(DatabaseType.ES)` — ArchUnit test enforces this
- See `docs/testing/acceptance.md` and `qa/acceptance-tests/`

## What to Test

- Every public change needs an automated test
- Every bug fix needs a regression test (use `@RegressionTest` annotation with issue link)
- Test public behavior only; avoid testing private/implementation details
- Skip trivial getters/setters

## What NOT to Test

- Simple delegation methods
- Auto-generated code (protobuf, SBE, OpenAPI models)
- Java language features (record accessors, enum values)

## Mocking Patterns

- Mock service layer in controller/tool tests via `@MockitoBean`
- Use `mockApiServiceAuthentication(service)` in MCP tool tests to stub `withAuthentication()`
- Use `doReturn(service).when(service).withAuthentication(any())` pattern
- For engine tests: use `RecordingExporter` instead of mocks

## Test Data

- Define test entities as `static final` constants in the test class
- Use `SearchQueryResult.Builder` for search result fixtures
- Use `Bpmn.createExecutableProcess()` builder for process model fixtures
- Use `ProcessDefinitionEntity`, `IncidentEntity`, etc. from `search/search-domain`

## Exemplary Test Files

- `gateways/gateway-mcp/src/test/java/.../ProcessDefinitionToolsTest.java` — MCP tool testing pattern
- `zeebe/engine/src/test/java/.../JobCompleteAuthorizationTest.java` — engine processor test with EngineRule
- `qa/acceptance-tests/src/test/java/.../IncidentIT.java` — acceptance test with @MultiDbTest