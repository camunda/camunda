```yaml
---
applyTo: "zeebe/gateway-rest/src/**"
---
```
# Gateway REST Module — Copilot Instructions

## Module Purpose

The `zeebe/gateway-rest` module implements the Camunda 8 REST API v2 (served on port 8080 under `/v2/`). It is the HTTP translation layer between external clients and the `service/` business logic layer, responsible for request deserialization, authentication delegation, request/response mapping, error handling, and OpenAPI/Swagger documentation. All controllers are conditionally enabled via `@ConditionalOnRestGatewayEnabled`.

## Architecture

### Layer Diagram

```
HTTP Request → Controller → RequestMapper/SearchQueryRequestMapper (Either<ProblemDetail, Request>)
  → .fold(RestErrorMapper::mapProblemToCompletedResponse, service call)
  → service.withAuthentication(auth).method(request) → CompletableFuture<BrokerResponse>
  → ResponseMapper/SearchQueryResponseMapper → ResponseEntity<ProtocolModel>
```

### Key Components

- **`controller/`** — ~30 Spring MVC controllers annotated with `@CamundaRestController` and `@RequestMapping("/v2/...")`. Each injects a `*Services` class from `service/` and a `CamundaAuthenticationProvider`. Organized into subpackages: `usermanagement/`, `tenant/`, `system/`, `setup/`, `authentication/`, `auditlog/`.
- **`mapper/`** — `RestErrorMapper` (exception → RFC 7807 `ProblemDetail` → `ResponseEntity`) and `RequestExecutor` (async service call wrapper with error handling).
- **`deserializer/`** — ~30 custom Jackson deserializers for OpenAPI-generated filter types. Two base classes: `FilterDeserializer<T,E>` (implicit scalar → advanced filter) and `AbstractRequestDeserializer<T>` (polymorphic instruction types).
- **`annotation/`** — Custom mapping annotations (`@CamundaPostMapping`, `@CamundaGetMapping`, etc.) that set default `produces`/`consumes` media types. `@RequiresSecondaryStorage` gates endpoints needing ES/OS/RDBMS.
- **`config/`** — Spring `@Configuration` classes: `JacksonConfig` (deserializer registration, strict coercion), `ApiFiltersConfiguration` (conditional endpoint disabling), `SecondaryStorageConfig` + `SecondaryStorageInterceptor`, `OpenApiResourceConfig` (Swagger UI).
- **`interceptor/`** — `SecondaryStorageInterceptor` checks `@RequiresSecondaryStorage` and rejects with 403 when `camunda.database.type=none`.
- **`validation/`** — `ResponseValidationAdvice` (opt-in via `camunda.rest.response-validation.enabled=true`) validates response bodies against OpenAPI-generated bean constraints.
- **`impl/filters/`** — `FilterRepository` loads external servlet `Filter` implementations from JARs.

## Controller Conventions

- Annotate class with `@CamundaRestController` (combines `@RestController` + `@ConditionalOnRestGatewayEnabled`).
- Use `@RequestMapping("/v2/<resource>")` at class level.
- Use `@CamundaPostMapping`, `@CamundaGetMapping`, `@CamundaPatchMapping`, `@CamundaPutMapping`, `@CamundaDeleteMapping` instead of Spring's `@PostMapping` etc. These set default `produces` to `[application/json, application/vnd.camunda.api.keys.string+json, application/problem+json]`.
- Return `CompletableFuture<ResponseEntity<Object>>` for write/command operations.
- Return `ResponseEntity<SpecificType>` for synchronous search/get operations.
- Always call `service.withAuthentication(authenticationProvider.getCamundaAuthentication())` before any service method — never call services directly.
- Mark search/get endpoints that need a secondary database with `@RequiresSecondaryStorage`.
- Some controllers have additional conditional annotations (e.g., `@ConditionalOnInternalUserManagement` on `UserController`).

## Request/Response Flow Patterns

### Command Operations (async, returns `CompletableFuture`)
```java
// Pattern 1: RequestMapper returns Either
RequestMapper.toCreateProcessInstance(request, multiTenancyCfg.isChecksEnabled())
    .fold(RestErrorMapper::mapProblemToCompletedResponse, this::createProcessInstance);

// Pattern 2: Direct service call via RequestExecutor
RequestExecutor.executeServiceMethodWithNoContentResult(
    () -> service.withAuthentication(auth).deleteResource(key));

// Pattern 3: Service call with response mapping
RequestExecutor.executeServiceMethod(
    () -> service.withAuthentication(auth).create(request),
    ResponseMapper::toCreateResponse,
    HttpStatus.CREATED);
```

### Search/Get Operations (synchronous)
```java
// Search: mapper returns Either, then search + response mapping in try/catch
SearchQueryRequestMapper.toProcessInstanceQuery(query)
    .fold(RestErrorMapper::mapProblemToResponse, this::search);

// Get by key: direct call wrapped in try/catch
try {
  return ResponseEntity.ok().body(
      SearchQueryResponseMapper.toProcessInstance(
          service.withAuthentication(auth).getByKey(key)));
} catch (final Exception e) {
  return mapErrorToResponse(e);
}
```

## Error Handling

- `RestErrorMapper.mapErrorToResponse(Throwable)` — converts any exception to RFC 7807 `ProblemDetail` via `GatewayErrorMapper.mapErrorToProblem()`, then wraps in `ResponseEntity` with `application/problem+json` content type.
- `RestErrorMapper.mapProblemToCompletedResponse(ProblemDetail)` — wraps `ProblemDetail` in `CompletableFuture<ResponseEntity>` for use in `Either.fold()`.
- `GlobalControllerExceptionHandler` — `@ControllerAdvice` that catches `HttpMessageNotReadableException` (with specialized handling for missing body, mismatched input, unknown enum, unknown type ID, array deserialization errors), `ServiceException`, `CompletionException`, `ResponseValidationException`, and generic `Exception`.
- `GlobalErrorController` — Spring's `ErrorController` implementation, converts unhandled errors to `CamundaProblemDetail`.
- All error responses use `CamundaProblemDetail.wrap()` to ensure consistent response shape.

## Deserializer System

Two base classes support the OpenAPI-generated filter/instruction model types:

- **`FilterDeserializer<T, E>`** — enables implicit value shorthand (e.g., `"name": "foo"` → `AdvancedStringFilter` with `$eq: "foo"`). Subclasses define `getFinalType()`, `getImplicitValueType()`, `createFromImplicitValue()`. See `StringFilterPropertyDeserializer`.
- **`AbstractRequestDeserializer<T>`** — handles polymorphic request types with field-based type discrimination (e.g., `ProcessInstanceCreationInstruction`). Validates exactly one discriminating field is present.

All deserializers are registered in `JacksonConfig.gatewayRestObjectMapperCustomizer()`. When adding a new filter type, create a deserializer extending `FilterDeserializer` and register it in that method.

## Testing Patterns

- Extend `RestControllerTest` (which extends `RestTest`) — provides `WebTestClient`, mocked `SecondaryStorageInterceptor`, and `JacksonConfig` import.
- Use `@WebMvcTest(value = YourController.class)` + `@ExtendWith(MockitoExtension.class)`.
- Mock the service with `@MockitoBean` and stub `withAuthentication()` to return the service itself.
- Use `webClient.post().uri(url).bodyValue(json).exchange()` to make requests.
- **ArchUnit rule**: `ControllerStrictJsonCompareArchTest` enforces that tests use `JsonCompareMode.STRICT` — never use `BodyContentSpec.json(String)` without a mode parameter.
- Helper methods in `RestControllerTest` generate parameterized filter operation test cases (`stringOperationTestCases`, `dateTimeOperationTestCases`, `keyOperationTestCases`, etc.).
- Run scoped: `./mvnw -pl zeebe/gateway-rest -am test -DskipITs -DskipChecks -Dtest=<TestClass> -T1C`

## Extension Points

- **New endpoint**: Create controller in `controller/`, annotate with `@CamundaRestController`, inject the corresponding `*Services` from `service/`. Use `RequestMapper`/`SearchQueryRequestMapper` from `gateways/gateway-mapping-http` for request conversion. Use `ResponseMapper`/`SearchQueryResponseMapper` for response conversion. Follow the Either fold pattern.
- **New filter deserializer**: Extend `FilterDeserializer`, register in `JacksonConfig.gatewayRestObjectMapperCustomizer()`.
- **New polymorphic request type**: Extend `AbstractRequestDeserializer`, register in `JacksonConfig`.
- **Conditional endpoint disabling**: Add `FilterRegistrationBean<EndpointAccessErrorFilter>` in `ApiFiltersConfiguration`.

## Common Pitfalls

- Never use plain `@PostMapping`/`@GetMapping` — always use the `@Camunda*Mapping` variants to get correct media types.
- Never call a service method without `withAuthentication()` — this is the immutability pattern that attaches auth context.
- Never use `@RestController` directly — use `@CamundaRestController` which includes `@ConditionalOnRestGatewayEnabled`.
- Never return plain error responses — always go through `RestErrorMapper` or `GlobalControllerExceptionHandler` to ensure RFC 7807 format with `CamundaProblemDetail`.
- Search endpoints must be annotated with `@RequiresSecondaryStorage` or they will fail silently when no secondary storage is configured.
- `JacksonConfig` configures strict coercion (`FAIL_ON_UNKNOWN_PROPERTIES`, no scalar coercion) — be aware of this when designing request DTOs.
- The `ControllerStrictJsonCompareArchTest` will fail if you use `.json(string)` without `JsonCompareMode.STRICT` in tests.

## Key Files

| File | Role |
|------|------|
| `controller/CamundaRestController.java` | Meta-annotation combining `@RestController` + `@ConditionalOnRestGatewayEnabled` |
| `controller/ProcessInstanceController.java` | Exemplary controller showing all patterns (create, search, get, batch ops) |
| `mapper/RestErrorMapper.java` | Central error-to-ResponseEntity mapping |
| `mapper/RequestExecutor.java` | Async service call execution with error handling |
| `GlobalControllerExceptionHandler.java` | Global exception handler producing `CamundaProblemDetail` |
| `config/JacksonConfig.java` | Deserializer registration and strict ObjectMapper config |
| `interceptor/SecondaryStorageInterceptor.java` | Gates `@RequiresSecondaryStorage` endpoints |
| `deserializer/FilterDeserializer.java` | Base class for implicit/explicit filter deserialization |
| `annotation/CamundaPostMapping.java` | Custom mapping annotation with default media types |
| `RestControllerTest.java` (test) | Base test class with filter operation test helpers |