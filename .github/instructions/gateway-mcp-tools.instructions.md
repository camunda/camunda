---
applyTo: "gateways/gateway-mcp/**/*"
---

When adding/editing MCP tools in `gateways/gateway-mcp`, follow the established patterns in this
repository to ensure:

- consistent tool schema generation
- consistent error handling / ProblemDetail mapping
- consistent search query mapping (filter/sort/page)
- consistent testing patterns

When adding a new tool, try to mirror existing tools and the respective REST controller
implementation (in `zeebe/gateway-rest`) as much as possible. Ask the user when encountering
patterns which are not obvious and update these instructions accordingly.

## Tool structure and annotations

- Tools are Spring beans: annotate tool classes with `@Component`.
- Enable parameter validation: annotate tool classes with `@Validated`.
- Each tool method is annotated with `@McpTool`.
  - Use `@McpTool(annotations = @McpAnnotations(readOnlyHint = true))` for read-only tools (
    search/get).
  - Set a clear `description`, including the shared eventual consistency note if the respective
    endpoint is eventually consistent:
    - `io.camunda.gateway.mcp.tool.ToolDescriptions.EVENTUAL_CONSISTENCY_NOTE`
  - Find the API spec in `zeebe/gateway-protocol/src/main/proto/v2/rest-api.yaml` to check for
    eventual consistency of an endpoint.

## Parameters and schema

- Use `@McpToolParam` for each parameter.
  - Provide `description`.
  - Mark optional params with `required = false`.
- For numeric key parameters, use bean validation annotations:
  - Example: `@Positive(message = "... must be a positive number.")`
- Use additional bean validation annotations where relevant:
  - `@NotBlank`, `@Pattern`, `@Size`, etc.
  - container element constraints (e.g., `Map<@NotBlank String, Object>`, `List<@NotBlank String>`)

## Authentication

- Do not call services directly; use the authenticated service variant:
  - `service.withAuthentication(authenticationProvider.getCamundaAuthentication())`

## Error handling and responses

- Prefer using `CallToolResultMapper` consistently:
  - `CallToolResultMapper.from(...)` for structured content
  - `CallToolResultMapper.fromPrimitive(...)` when returning primitives / messages
  - `CallToolResultMapper.mapErrorToResult(e)` for exceptions
- Always map request mapping/validation errors (`Either.left(problem)`) to:
  - `CallToolResultMapper.mapProblemToResult(problem)`
- Use `try/catch` around **synchronous** request building/mapping code that may throw (e.g.
  `SearchQueryRequestMapper`, `SimpleRequestMapper`, parsing inputs) and map exceptions via
  `CallToolResultMapper.mapErrorToResult(e)`.
- If a tool method only wraps an authenticated service call returning a `CompletableFuture` with
  `CallToolResultMapper.from/fromPrimitive` (no synchronous mapping logic), explicit `try/catch`
  is optional (see `ClusterTools`).

## Search tools: filter/sort/page

- Search tools accept three main inputs:
  - `filter`: MCP-facing filter model (see next section)
  - `sort`: list of sort requests (use the generated advanced sort request types)
  - `page`: MCP-facing page request model
- Construct the query using `SearchQueryRequestMapper` overloads that accept the MCP-facing
  (simple) filter/page/sort (mirror `IncidentTools`, `ProcessInstanceTools`, `VariableTools`).
- Convert the service response using `SearchQueryResponseMapper`.

## Write/command tools

- Prefer reusing existing request mappers (e.g., `SimpleRequestMapper`) to shape/validate requests.
  - For request mapping returning `Either.left(problem)`, return
    `CallToolResultMapper.mapProblemToResult(problem)`.
- For tools that depend on multi-tenancy checks, pass `MultiTenancyConfiguration.isChecksEnabled()`
  through to the request mapper (see `ProcessInstanceTools.createProcessInstance`).

## Optional parameter defaults

- If optional parameters influence response shape/size (e.g., booleans), make defaults explicit:
  - document the default in `@McpToolParam(description=...)`
  - implement defaulting in the tool method (e.g., `truncateValues == null || truncateValues`).

## MCP-facing models (schema shaping)

Sometimes the generated OpenAPI models include fields that should not be exposed via MCP.

Pattern:

- Create an MCP-specific model class in
  `gateways/gateway-mcp/src/main/java/io/camunda/gateway/mcp/model/`.
- Extend the generated *simple* model.
- Hide fields from the MCP JSON schema by overriding getters and annotating with `@JsonIgnore`.
  - Keep the underlying mapping code intact (mapping may support more than MCP exposes).

Example:

- `io.camunda.gateway.mcp.model.McpIncidentFilter`

## Mapping: simple → advanced

- MCP tools typically accept the generated *simple* request models for filters/pages.
- Convert them into the advanced query model in the mapping layer (`gateways/gateway-mapping-http`).
- Keep mapping code centralized in mappers such as:
  - `io.camunda.gateway.mapping.http.search.SimpleSearchQueryMapper`
  - `io.camunda.gateway.mapping.http.search.SearchQueryRequestMapper`
- If a simple model contains a nested `$or` list of filter-field groups, map each group into the
  corresponding advanced filter-field type.
- When fields are ignored in the MCP tool, still map all the available fields in the mapper as the
  simple model and mapper might also be consumed by other integrations.

## Simple model generation (do not edit API spec)

- If MCP needs a simplified JSON shape (e.g., represent a complex filter property as a scalar),
  update the *simple* model generation config in:
  - `gateways/gateway-model/pom.xml` (openapi-generator execution `id=simple`)
- Do not edit the API spec to achieve MCP-specific schema changes.
- After updating generation config, regenerate models by running a Maven build that includes
  `gateways/gateway-model`.

## Tests

- Add tool tests in `gateways/gateway-mcp/src/test/java/...` mirroring `IncidentToolsTest`.

Testing pattern:

- Extend `ToolsTest` and use `@ContextConfiguration(classes = {YourTools.class})`.
- Mock services with `@MockitoBean` and call `mockApiServiceAuthentication(service)` in
  `@BeforeEach`.
- Validate:
  1) happy-path structured content mapping
  2) error mapping to `ProblemDetail` when service throws
  3) bean validation (e.g., negative key)
  4) search mapping: verify captured query filter/sort/page
  5) date range filter mapping: verify operators and parsed `OffsetDateTime`

Reference:

- `IncidentToolsTest.shouldSearchIncidentsWithCreationTimeDateRangeFilter`

## Build / verification (scoped)

To validate a single tool test while avoiding unrelated reactor failures, run a scoped test
invocation:

`./mvnw -pl gateways/gateway-mcp -am test -DskipITs -DskipChecks -Dtest=<TestClassName> -Dsurefire.failIfNoSpecifiedTests=false -DfailIfNoTests=false -T1C`
