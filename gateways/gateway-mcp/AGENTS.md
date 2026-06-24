# MCP Gateway Module — Agent Instructions

You are working in `gateways/gateway-mcp`, the Camunda MCP (Model Context Protocol) server
implementation. Read the monorepo-wide instructions first:

@../../AGENTS.md

---

## Module Overview

This module implements two stateless synchronous MCP servers that expose Camunda 8 APIs to AI
agents. Both servers run inside the same Spring Boot application and are disabled by default.

### Servers

|    Server     |     Endpoint     |               Physical-tenant variant                |                                                       Purpose                                                        |
|---------------|------------------|------------------------------------------------------|----------------------------------------------------------------------------------------------------------------------|
| **cluster**   | `/mcp/cluster`   | `/physical-tenants/{physicalTenantId}/mcp/cluster`   | Static tools for all cluster operations (search, incidents, user tasks, variables, process definitions, instances)   |
| **processes** | `/mcp/processes` | `/physical-tenants/{physicalTenantId}/mcp/processes` | Dynamic tools generated from deployed process definitions that are configured as MCP tools, plus shared static tools |

The processes server resolves tools at request time by querying open start-event message
subscriptions that have a `toolName` set. Invoking a tool correlates a message, which starts a new
process instance. The cluster server uses static tools registered via `@CamundaMcpTool`.

### Enabling the MCP gateway

The MCP gateway is off by default. Enable it via application properties:

```yaml
camunda.mcp.enabled: true
```

The `zeebe.broker.gateway.enable` property (default `true`) also gates the MCP gateway; setting it
to `false` disables MCP as well. See `ConditionalOnMcpGatewayEnabled`.

---

## Adding or Editing a Tool

When adding a new tool, mirror the corresponding REST controller in `zeebe/gateway-rest` and the
closest existing tool implementation as much as possible. The goal is consistent schema generation,
consistent error handling / `ProblemDetail` mapping, consistent search query mapping, and
consistent testing patterns. If you encounter a pattern that is not covered here, ask the engineer
before inventing something new, and update this file afterward.

---

## Tool Structure

### Annotations

- Annotate tool classes with `@Component` and `@Validated` (enforced by ArchUnit).
- Annotate every public tool method with `@CamundaMcpTool` — **never** use Spring AI's `@McpTool`
  directly (also enforced by ArchUnit).
  - Use `annotations = @McpAnnotations(readOnlyHint = true)` for read-only tools.
  - Set a clear `description`; include `ToolDescriptions.EVENTUAL_CONSISTENCY_NOTE` when the
    corresponding REST endpoint is eventually consistent (check
    `zeebe/gateway-protocol/src/main/proto/v2/rest-api.yaml`).
  - Set `processesServer = true` to also expose a static tool in the processes MCP server.

### Parameters

- Annotate each parameter with `@McpToolParam(description = "...")`.
- Mark optional parameters with `required = false`.
- Use bean validation annotations for input constraints: `@Positive`, `@NotBlank`, `@Pattern`,
  `@Size`, and container element constraints (e.g., `Map<@NotBlank String, Object>`).
  - For numeric key parameters use `@Positive` with an explicit message, e.g.:
    `@Positive(message = "processDefinitionKey must be a positive number.")`
- When grouping related parameters into a DTO, annotate the DTO parameter with
  `@McpToolParamsUnwrapped @Valid` — the `@Valid` is required (enforced by ArchUnit) to trigger
  cascading validation.

### Authentication

Never call services directly. Obtain the service via `serviceRegistry`, passing
`PhysicalTenantContext.current()` to scope it to the active physical tenant, then pass
`authenticationProvider.getCamundaAuthentication()` into each service method call:

```java
serviceRegistry.processDefinitionServices(PhysicalTenantContext.current())
    .getByKey(key, authenticationProvider.getCamundaAuthentication())
```

---

## Error Handling and Responses

Use `CallToolResultMapper` for all tool responses:

|             Situation              |                                            Method                                             |
|------------------------------------|-----------------------------------------------------------------------------------------------|
| Structured result (object/list)    | `CallToolResultMapper.from(future, resultMapper)`                                             |
| Primitive / message                | `CallToolResultMapper.fromPrimitive(future, resultMapper)`                                    |
| Service exception                  | `CallToolResultMapper.mapErrorToResult(e)` (used inside `from`/`fromPrimitive` automatically) |
| `Either.left(problem)` from mapper | `CallToolResultMapper.mapProblemToResult(problem)`                                            |

Wrap synchronous mapping/parsing code (e.g., `SearchQueryRequestMapper`, `SimpleRequestMapper`,
input parsing) in `try/catch` and map exceptions via `CallToolResultMapper.mapErrorToResult(e)`.
If a tool only wraps an async service call with no synchronous mapping logic, explicit `try/catch`
is optional (see `ClusterTools`).

---

## Search Tools

Search tools accept three inputs: `filter`, `sort`, and `page`.

- Build the query using `SearchQueryRequestMapper` overloads that accept the MCP-facing (simple)
  filter/page/sort — mirror `IncidentTools`, `ProcessInstanceTools`, or `VariableTools`.
- Convert the service response using `SearchQueryResponseMapper`.
- See the MCP-facing filter models in `io.camunda.gateway.mcp.model` (e.g., `McpIncidentFilter`).

### Mapping: simple → advanced

Keep all new mapping code centralized in:

- `io.camunda.gateway.mapping.http.search.SimpleSearchQueryMapper`
- `io.camunda.gateway.mapping.http.search.SearchQueryRequestMapper`

If a simple filter model contains a nested `$or` list of filter-field groups, map each group into
the corresponding advanced filter-field type in the mapper (not in the tool).

When a field is hidden from MCP via `@JsonIgnore` on an MCP-facing model override, still map that
field in the mapper — the simple model and mapper are consumed by other integrations beyond MCP.

## Write/Command Tools

- Reuse existing request mappers (e.g., `SimpleRequestMapper`) to shape and validate requests.
- For `Either.left(problem)` results from request mapping, return
  `CallToolResultMapper.mapProblemToResult(problem)`.
- For tools that depend on multi-tenancy, pass `MultiTenancyConfiguration.isChecksEnabled()`
  through to the request mapper (see `ProcessInstanceTools.createProcessInstance`).

---

## MCP-Facing Models (Schema Shaping)

When the generated OpenAPI models include fields that must not be exposed via MCP:

1. Create an MCP-specific model in `io.camunda.gateway.mcp.model`.
2. Extend the generated *simple* model.
3. Override getters and annotate them `@JsonIgnore` to hide fields from the MCP JSON schema.
4. Keep the underlying mapping code intact, and ensure all model fields are mapped — even fields
   hidden from MCP — because the simple model and mapper are consumed by other integrations.

Reference: `McpIncidentFilter`, `McpProcessDefinitionFilter`, `McpProcessInstanceFilter`.

---

## Simple Model Generation

MCP tools use the *simple* request models generated from the API spec:

- Do **not** edit `zeebe/gateway-protocol/src/main/proto/v2/rest-api.yaml` to achieve MCP-specific
  schema changes.
- To simplify a complex filter property (e.g., represent it as a scalar), update the generation
  config in `gateways/gateway-model/pom.xml` (openapi-generator execution `id=simple`).
- Regenerate models by rebuilding `gateways/gateway-model`.

---

## Optional Parameter Defaults

When optional parameters influence response shape or size, make defaults explicit:

- Document the default in `@McpToolParam(description = "...")`.
- Implement defaulting in the tool method body (e.g., `truncateValues == null || truncateValues`).

---

## Testing

### Unit tests (module-level)

Tool unit tests live in `gateways/gateway-mcp/src/test/java/`.

**Cluster-server tools** extend `OperationalToolsTest` (endpoint: `/mcp/cluster`).  
**Processes-server tools** extend `ProcessesToolsTest` (endpoint: `/mcp/processes`).

Both base classes extend `ToolsTest`, which:

- Starts a full Spring Boot context with a random port.
- Provides a live `McpSyncClient` connected to the running server (`mcpClient` field).
- Provides `@MockitoBean protected CamundaAuthenticationProvider authenticationProvider` and
  `@MockitoBean protected ServiceRegistry serviceRegistry`.
- Stubs `authenticationProvider.getCamundaAuthentication()` in `@BeforeEach`.

Add `@ContextConfiguration(classes = {YourTools.class})` to load only the tool class under test.
Wire the service by stubbing `serviceRegistry.<domain>Services(any())` in `@BeforeEach`.

**Test structure pattern** (one `@Nested` class per tool method):

```java
@ExtendWith(MockitoExtension.class)
@ContextConfiguration(classes = {MyTools.class})
class MyToolsTest extends OperationalToolsTest {

  @MockitoBean private MyServices myServices;
  @Captor private ArgumentCaptor<MyQuery> queryCaptor;

  @BeforeEach
  void wireServiceRegistry() {
    when(serviceRegistry.myServices(any())).thenReturn(myServices);
  }

  @Nested
  class GetMyThing {
    @Test
    void shouldGetMyThingByKey() { /* ... */ }

    @Test
    void shouldFailOnMissingKey() { /* ... */ }
  }

  @Nested
  class SearchMyThings {
    @Test
    void shouldSearchWithFilterSortAndPaging() { /* ... */ }
  }
}
```

**What to validate per test:**

1. Happy-path: assert all fields in the example entity that the tool exposes (prefer a shared
   `assertExample<Thing>(...)` helper method used in both get and search tests). For search
   results, also assert the `page` metadata: `totalItems`, `hasMoreTotalItems`, `startCursor`,
   and `endCursor`.
2. Error mapping: service throws → result is `isError()` with a `ProblemDetail`.
3. Bean validation: negative key, blank required string, etc.
4. Search mapping: capture the query with `ArgumentCaptor` and verify filter/sort/page fields.
5. Date range filters: verify operators and parsed `OffsetDateTime`.

For happy-path assertions, convert `result.structuredContent()` via
`objectMapper.convertValue(..., ProtocolModel.class)` and assert each field individually. Avoid
full-object recursive comparison unless you build an explicit expected protocol model. Do not assert
against the mapping layer (`SearchQueryResponseMapper`, etc.) in tests.

References:
- `ProcessDefinitionToolsTest` — preferred reference for new tool tests
- `IncidentToolsTest.shouldSearchIncidentsWithCreationTimeDateRangeFilter` — date range filter

### Schema regression test

`ToolsSchemaRegressionTest` compares the tool list schema against a stored snapshot
(`src/test/resources/schema/tools-schema-snapshot.json`). After intentional schema changes, update
the snapshot by running a local Camunda cluster with MCP enabled and executing:

```bash
./gateways/gateway-mcp/src/test/resources/schema/update-tools-schema-snapshot.sh \
  > gateways/gateway-mcp/src/test/resources/schema/tools-schema-snapshot.json
```

### QA acceptance tests

Integration tests in `qa/acceptance-tests/src/test/java/io/camunda/it/mcp/`:

|         Test class         |                                                What it covers                                                |
|----------------------------|--------------------------------------------------------------------------------------------------------------|
| `McpServerConfigurationIT` | MCP enabled/disabled via `camunda.mcp.enabled`; both servers initialize correctly; physical-tenant endpoints |
| `McpAuditLogIT`            | Audit logging of MCP tool calls                                                                              |
| `authentication/`          | OIDC, basic auth, and authentication-disabled scenarios                                                      |

Use `McpServerTest` base class for test helper utilities (`createMcpClient`, `createPhysicalTenantMcpClient`, `createBasicAuthCustomizer`).

### ArchUnit tests

`qa/archunit-tests/src/test/java/io/camunda/gateway/mcp/McpToolsAnnotationArchTest` enforces:

- All `*Tools` classes in `io.camunda.gateway.mcp.tool..` are `@Component`
- All `*Tools` classes in `io.camunda.gateway.mcp.tool..` are `@Validated`
- All public methods in `*Tools` classes use `@CamundaMcpTool`, not `@McpTool`
- All `@McpToolParamsUnwrapped` parameters also carry `@Valid`

### E2E tests

`qa/c8-orchestration-cluster-e2e-test-suite/tests/identity/mcp-processes.spec.ts` covers the
processes MCP server end-to-end (process exposure as tools, message subscription lifecycle).

---

## Build Commands

```bash
# Build with dependencies
./mvnw install -pl gateways/gateway-mcp -am -Dquickly -T1C

# Run all unit tests
./mvnw verify -pl gateways/gateway-mcp -DskipTests=false -DskipITs -Dquickly

# Run a single test class (fast inner loop)
./mvnw -pl gateways/gateway-mcp test -DskipITs -DskipChecks \
  -Dtest=<TestClassName> \
  -Dsurefire.failIfNoSpecifiedTests=false -DfailIfNoTests=false -T1C

# Before committing
./mvnw license:format spotless:apply -T1C && \
./mvnw verify -pl gateways/gateway-mcp -DskipTests=false -Dquickly
```

---

## Key Classes Reference

|                Class                 |                                 Location                                  |
|--------------------------------------|---------------------------------------------------------------------------|
| `CamundaMcpTool`                     | `config/tool/CamundaMcpTool.java` — custom tool annotation                |
| `McpToolParamsUnwrapped`             | `config/tool/McpToolParamsUnwrapped.java` — unwrap DTO parameters         |
| `CallToolResultMapper`               | `mapper/CallToolResultMapper.java` — map results and errors               |
| `CamundaMcpServersAutoConfiguration` | `config/CamundaMcpServersAutoConfiguration.java` — server/transport setup |
| `ConditionalOnMcpGatewayEnabled`     | `ConditionalOnMcpGatewayEnabled.java` — feature flag                      |
| `ProcessesToolRepository`            | `processes/ProcessesToolRepository.java` — dynamic process tools          |
| `ToolDescriptions`                   | `tool/ToolDescriptions.java` — shared description constants               |
| `ToolsTest`                          | test base for cluster-server tool tests                                   |
| `OperationalToolsTest`               | test base for cluster-server tool tests (extends `ToolsTest`)             |
| `ProcessesToolsTest`                 | test base for processes-server tool tests (extends `ToolsTest`)           |

