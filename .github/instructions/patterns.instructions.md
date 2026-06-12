```yaml
---
applyTo: "**"
---
```
# Code Patterns and Conventions

## Naming Conventions

- **Classes**: PascalCase. Use suffixes: `*Processor` (command handlers), `*Applier` (event appliers), `*Behavior` (reusable logic), `*Store`/`*Reader` (data access), `*Services` (business services), `*Controller` (REST), `*Tools` (MCP), `*Mapper` (mapping), `*Util` (utilities)
- **DB types**: `Db*` prefix (e.g., `DbUserState`, `DbCompositeKey`)
- **Variables/parameters**: camelCase. Use `final` for all local variables and parameters
- **Constants**: `UPPER_SNAKE_CASE` for `static final` fields
- **Enums**: Use explicit numeric values in serialized enums; never rely on ordinals
- **Records**: Java records for DTOs, configuration, and identifiers
- **Test methods**: Prefix with `should` (e.g., `shouldCreateProcessInstance`)

## Import Ordering

Imports follow Google Java Format (enforced by Spotless). Order:
1. Static imports (alphabetical)
2. `io.camunda.*` (alphabetical)
3. Other third-party packages (alphabetical)
4. `java.*` / `javax.*` (alphabetical)

Never use wildcard imports. Example from `ProcessInstanceController.java`:
```java
import static io.camunda.zeebe.gateway.rest.mapper.RestErrorMapper.mapErrorToResponse;

import io.camunda.gateway.mapping.http.RequestMapper;
import io.camunda.service.ProcessInstanceServices;
import java.util.concurrent.CompletableFuture;
import org.springframework.http.ResponseEntity;
```

## Key Design Patterns

### withAuthentication() Immutability
Services extend `ApiServices<T>` and return new instances with auth context:
```java
service.withAuthentication(authenticationProvider.getCamundaAuthentication())
```
Never call services directly without authentication wrapping. See `service/.../ApiServices.java`.

### Either Monad for Error Handling
Use `Either<L, R>` for operations that may fail. Right = success, Left = error.
```java
RequestMapper.toCreateProcessInstance(request, multiTenancyCfg.isChecksEnabled())
    .fold(RestErrorMapper::mapProblemToCompletedResponse, this::createProcessInstance);
```
See `zeebe/util/.../Either.java`.

### REST Controllers
- Annotate with `@CamundaRestController` + `@RequestMapping("/v2/...")`
- Use `@CamundaPostMapping`, `@CamundaGetMapping` (custom annotations)
- Return `CompletableFuture<ResponseEntity<Object>>`
- Convert requests via `RequestMapper`, responses via `ResponseMapper`
- Handle errors with `.fold(RestErrorMapper::mapProblemToCompletedResponse, ...)`
- See `zeebe/gateway-rest/.../ProcessInstanceController.java`

### MCP Tools
- Annotate class with `@Component` + `@Validated`
- Methods use `@McpTool` with `@McpToolParam` for parameters
- Use `CallToolResultMapper.from()` for responses, `.mapErrorToResult()` for errors
- Always authenticate: `service.withAuthentication(authenticationProvider.getCamundaAuthentication())`
- See `gateways/gateway-mcp/.../ProcessDefinitionTools.java` and `.github/instructions/gateway-mcp-tools.instructions.md`

### Event Sourcing (Engine)
- State changes only through `EventApplier` classes, never from `Processor` classes
- `Processor` writes EVENT records; `EventApplier` applies them to state
- Event appliers are frozen after release — create new versioned applier instead of modifying
- Prefer composition over inheritance: use `*Behavior` classes for shared logic
- See `zeebe/engine/README.md`

### CQRS (RDBMS)
- Write: `DbModel` records with `ExecutionQueue` batching
- Read: `DbQuery` records with keyset pagination
- Clean separation: `MutableState` (appliers only) vs `State` (read-only, processors)

## State Management

- Spring `@Conditional` for backend-specific bean loading (ES vs OS vs RDBMS)
- `SearchClientsProxy` delegates to correct search backend with security-aware decoration
- `ApiServicesExecutorProvider` manages async execution threads

## API Response Shapes

- REST: RFC 7807/9457 `ProblemDetail` for errors, domain models for success
- gRPC: `io.grpc.Status` codes via `GrpcErrorMapper`
- MCP: `CallToolResult` with `.structuredContent()` and text fallback

## Code Organization Within Files

1. License header (Camunda License 1.0 or Apache 2.0)
2. Package declaration
3. Imports (static first, then by package)
4. Class-level annotations
5. Static fields / constants
6. Instance fields
7. Constructors
8. Public methods
9. Package-private / protected methods
10. Private methods

## Comments

- Only comment non-obvious business logic
- Use `// given`, `// when`, `// then` sections in tests
- Document `@McpToolParam` descriptions for MCP tool parameters
- License header on every source file (enforced by `license-maven-plugin`)

## Anti-Patterns to Avoid

- Never use `Thread.sleep` in tests; use Awaitility
- Never use JUnit or Hamcrest assertions; use AssertJ exclusively
- Never change released `EventApplier` implementations; create a new version
- Never mutate state from a `Processor`; only from `EventApplier`
- Never trust data in a command's `RecordValue` as up-to-date; read from state
- Never use shaded dependencies in tests (e.g., Testcontainers-shaded Awaitility)
- Never use wildcard imports
- Never commit without running `spotless:apply`

## Exemplary Files

- `service/src/main/java/io/camunda/service/ApiServices.java` — base service pattern
- `zeebe/gateway-rest/.../ProcessInstanceController.java` — REST controller pattern
- `gateways/gateway-mcp/.../ProcessDefinitionTools.java` — MCP tool pattern
- `zeebe/engine/.../Engine.java` — engine record processor dispatch
- `zeebe/util/.../Either.java` — functional error handling