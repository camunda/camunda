# Contract Adaptation Architecture (Spec-Driven Generation)

Tracking issue: [#48123 — Compile time data contract for data layer](https://github.com/camunda/camunda/issues/48123)

## Vision

The OpenAPI specification is the single source of truth for the HTTP API layer. From the spec, the
system generates:

- **Request DTOs** with constraint validation expressed in the spec.
- **Response DTOs** with nullability constraints and key coercion from the spec.
- **Thin controllers** that handle routing, request shape validation, and direct service delegation.

A developer defining a new endpoint writes the YAML spec (route, method, request/response schemas,
constraints), runs generation, and gets a controller with all mechanical concerns handled. The only
hand-written code is the service adapter (which calls `RequestMapper` for semantic validation and
service-layer mapping).

## Problem

The API contract and search-domain entities evolve independently. The hand-written controller layer
conflates multiple concerns — routing, DTO shape mapping, constraint validation, business
validation, service delegation, and response construction — in ad-hoc patterns that vary by
endpoint and developer.

Specific problems:

- **No compile-time data contract.** Regressions in the data layer are caught — when they are
  caught — in integration testing rather than by the compiler.
- **Business policy and DTO mapping mechanics are mixed.** `SearchQueryResponseMapper` mixes
  contract enforcement, coercion/defaulting policy, and mechanical DTO mapping in a single class.
- **Mechanical concerns are hand-maintained.** Deterministic transformations (key coercion, nested
  traversal, field projection) are replicated manually even though they are derivable from schema
  semantics.
- **Each controller is a bespoke artifact.** 28 hand-written controllers with inconsistent
  patterns for how they validate, delegate, and construct responses. Adding a new endpoint
  requires copying and adapting an existing controller.
- **The spec and the implementation drift.** There is no structural guarantee that the controller
  layer matches the spec. Changes to the spec require manual updates to controllers, mappers,
  and validators in multiple files.

## Goals

### From the tracking issue

1. **Move API regression detection to the compiler level.**
   Null-handling decisions become explicit at the mapper boundary. Contract drift is caught during
   coding, not in regression tests.

2. **Generate a strict boundary SDK.**
   Boundary models that are null-safe by construction: immutable, with explicit nullability
   semantics.

3. **Turn nullness into hard compiler/IDE errors.**
   Engineers get immediate feedback when a nullable entity field is assigned to a non-null contract
   field without handling.

4. **Make the boundary mandatory in architecture.**
   All outbound data passes through boundary mappers. Architecture tests prevent controllers from
   exposing internal entities directly.

### Discovered during implementation

5. **Separate business policy from mechanical mapping.**
   Business decisions (fail-fast, default, derive) belong in a policy layer. Shape-mapping to
   protocol DTOs is mechanical and can be generated.

6. **Eliminate hand-coded mechanical boilerplate.**
   Deterministic key coercion (`Long` → `String`), nested object/list traversal, and field
   projection are generated from the OpenAPI schema graph rather than replicated in mapper code.

7. **Guarantee schema-graph traversal completeness.**
   Generated code follows the specification graph, so nested-field handling is not limited by what
   an engineer remembered to traverse.

8. **Make endpoint creation spec-driven.**
   Defining a new endpoint means writing YAML and running generation, not hand-coding a
   controller, request mapper, response mapper, and wiring them together.

## Architecture

### Target layers

```
┌─────────────────────────────────────────────────────────────────────────────┐
│  OpenAPI Spec (YAML)          — single source of truth                     │
│  route, method, request/response shapes, constraints, key types            │
└────────────────────────────────────┬────────────────────────────────────────┘
                                     │ generation
          ┌──────────────────────────┼──────────────────────────┐
          ▼                          ▼                          ▼
┌────────────────────┐  ┌────────────────────────┐  ┌──────────────────────────┐
│  Request DTOs      │  │  Response DTOs          │  │  Thin Controller         │
│  (strict contracts)│  │  (strict contracts)     │  │  (generated — ALL ops)   │
│                    │  │                         │  │                          │
│  • Shape valid.    │  │  • @Nullable/@NonNull   │  │  • Route + method        │
│  • Constraint v.   │  │  • Key coercion         │  │  • Constraint valid→400  │
│  • Type-safe       │  │  • Step builder         │  │  • Service adapter call  │
│    builder         │  │  • requireNonNull       │  │  • Response construction │
└────────────────────┘  └────────────────────────┘  │                          │
                                                     └────────────┬─────────────┘
                                                                  │
                                                                  ▼
                                          ┌──────────────────────────────────┐
                                          │  Service Adapter                 │
                                          │  (interface per tag)             │
                                          │                                  │
                                          │  execute(request, authentication)│
                                          │    → RequestMapper (semantic     │
                                          │      validation + mapping)       │
                                          │    → service call                │
                                          │    → service result → response   │
                                          │                                  │
                                          │  Hand-written for all endpoints. │
                                          │  Scaffold generated with TODO    │
                                          │  stubs for new endpoints.        │
                                          └──────────────────────────────────┘
```

The controller layer is **unconditionally generated** for every operation in the spec. The
generator does not classify operations into patterns to decide whether a controller method can
be produced. Every operation gets the same structural treatment: routing, request
deserialization, constraint validation, service adapter delegation, response construction.

The controller delegates to a single hand-written interface:

- **Service Adapter** — request mapping to internal service DTOs, service call execution, and
  response mapping. This is where all per-endpoint execution logic lives. The adapter calls
  `RequestMapper`, which performs semantic validation (business rules not expressible in the
  spec) and maps to service-layer request types.

For existing endpoints, the logic currently in hand-written controllers is extracted into adapter
implementations. For new endpoints, the generator produces scaffold implementations with TODO
stubs showing the developer exactly where to implement behavior.

### What the spec provides vs. what humans write

|                      Concern                       |              Source              |                        Generated or hand-written                        |
|----------------------------------------------------|----------------------------------|-------------------------------------------------------------------------|
| Route, HTTP method                                 | Spec paths + operationId         | Generated                                                               |
| Request DTO shape                                  | Spec request schema              | Generated                                                               |
| Constraint validation (min/max, pattern, required) | Spec schema constraints          | Generated (→ 400)                                                       |
| Response DTO shape                                 | Spec response schema             | Generated                                                               |
| Response nullability                               | Spec required/nullable           | Generated (compile-time)                                                |
| Response key coercion (Long → String)              | Spec format: int64 + string type | Generated                                                               |
| Controller method (routing + delegation)           | Spec operation structure         | Generated                                                               |
| Semantic validation (business rules)               | Human judgment                   | Hand-written (in `RequestMapper` validators, called by service adapter) |
| Service adapter (domain mapping + service call)    | Human wiring                     | Hand-written (interface)                                                |
| Adapter scaffold for new endpoints                 | Spec operation structure         | Generated (TODO stubs)                                                  |

### Data flow: request side

```
HTTP Request
  → Spring deserializes to strict contract DTO (generated)
    → Constraint validation in compact constructor → 400 if violated
      → Controller (generated): delegate to service adapter
        → ServiceAdapter.execute(request, authentication)
          → RequestMapper.toXxx(request, config)
            → hand-written validator (semantic checks) → 400 if violated
            → map to service-layer request type
            → → ResponseEntity
```

### Data flow: response side

```
Service result
  → Service adapter (hand-written: domain mapping + null-handling policy)
    → Strict contract DTO (generated: null-safe, key-coerced, serialization-annotated)
      → returned directly from controller as ResponseEntity
```

Strict contracts are the serialization type. There is no protocol model translation layer.

### Generated artifacts

From the OpenAPI spec (`zeebe/gateway-protocol/src/main/proto/v2/*.yaml`), the generator produces:

- **Strict contract DTOs** — immutable Java records with:
  - `Objects.requireNonNull` validation for required non-nullable fields.
  - `@Nullable` annotation for nullable fields.
  - Typed step-builder that enforces required fields at compile time.
  - Key coercion encapsulated in builder accept methods.
  - Recursive nested object/list coercion from the schema graph.
- **Result mappers** — `toProtocol(strictContract)` methods that mechanically project strict
  contract fields onto protocol model setters. Generated for the transitional state (Stages 1–2)
  where controllers were typed to protocol model classes. Eliminated in the unified type pipeline
  — strict contracts are returned directly.
- **Search query request mappers** — `toXxxQuery(request)` methods that decompose search request
  DTOs into filter/sort/page components and construct domain query objects. Generated for all
  search endpoints following the standard decomposition pattern.
- **Search query response mappers** — wrapper methods that adapt
  `SearchQueryResult<Entity>` → search response DTOs via contract adapters + result mappers.
  Single-entity delegation methods that pass through to adapters.
- **Thin controllers** — complete controller classes that implement the generated API interface.
  Generated for **all** operations in the spec (except streaming). Each controller provides:
  - Routing via `@RequestMapping` + API interface method signatures.
  - Request shape validation via Jakarta `@Valid` (from API interface annotations).
  - Service delegation via injected `ServiceAdapter` interface.
  - Response construction via the service adapter's return value.
- **Service adapter interfaces** — one interface per tag, with a method per operation.
  Returns `ResponseEntity<T>`. The generator produces a scaffold `Default*ServiceAdapter`
  implementation for each tag. For existing endpoints, the scaffolds are filled in with
  extracted logic from legacy controllers. For new endpoints, the scaffolds contain TODO stubs.

### Controller generation model

The generator produces a controller method for **every** operation in the spec. Each generated
method has the same structural shape:

```java
public ResponseEntity<FooResult> operationName(final FooRequest request) {
    final var authentication = authenticationProvider.getCamundaAuthentication();
    return fooServiceAdapter.operationName(request, authentication);
}
```

The generated controller delegates to a single injected interface — the service adapter — without
encoding any knowledge of what the implementation does. The controller is a pure routing,
authentication, and delegation shell.

#### Uniform treatment — no pattern classification

The generator treats all 182 operations uniformly. There is no classification step — every
operation gets the same structural treatment in the controller (routing, request deserialization,
constraint validation, service adapter delegation, response construction).

The adapter implementations are hand-written for all operations. Common patterns exist in
practice (search endpoints decompose into filter/sort/page, GET_BY_KEY endpoints do a
service lookup by key, etc.), but these patterns are implemented in the adapter layer, not
encoded in the generator. The generator's only concern is the spec-derived controller surface.

> **History:** An earlier version of the generator classified operations into patterns
> (SEARCH, GET_BY_KEY, MUTATION_VOID, MUTATION_RESPONSE, STATISTICS, Unclassified) and used
> hand-maintained lookup maps to produce pattern-specific adapter scaffolds. This classification
> pipeline was removed in slice 4 — the lookup maps and classification logic were dead code
> that the universal controller pipeline had already superseded.

#### Special operation support

Two operations have atypical HTTP patterns that required dedicated adapter support rather than
exclusion from generation:

- **`activateJobs`** — server-sent events via SSE `SseEmitter`. The generated controller method
  delegates to the adapter, which manages the async observer lifecycle. The controller method
  itself follows the standard universal pattern.
- **`getDocument`** — binary streaming download. The adapter returns a `StreamingResponseBody`
  wrapped in `ResponseEntity<Object>`. The controller method follows the standard universal
  pattern.

Both operations have generated controllers — there are no `x-code-generation: skip` exclusions
in the spec. The complexity is contained within the adapter implementation.

#### Universal controller shape

Every generated controller method follows the same delegation pattern, regardless of the
underlying HTTP shape:

```java
public ResponseEntity<UserSearchResult> searchUsers(final UserSearchQueryRequest request) {
    final var authentication = authenticationProvider.getCamundaAuthentication();
    return userServiceAdapter.searchUsers(request, authentication);
}
```

```java
public ResponseEntity<UserCreateResult> createUser(final UserRequest request) {
    final var authentication = authenticationProvider.getCamundaAuthentication();
    return userServiceAdapter.createUser(request, authentication);
}
```

```java
public ResponseEntity<Void> deleteUser(final String username) {
    final var authentication = authenticationProvider.getCamundaAuthentication();
    return userServiceAdapter.deleteUser(username, authentication);
}
```

The controller has no knowledge of what the adapter does. Execution logic
(service calls, semantic validation, response mappers, `RequestExecutor`) lives entirely in the
adapter implementation.

#### Authentication context

Every generated controller injects `CamundaAuthenticationProvider` and resolves the
authentication context once per request before delegating to the adapter:

```java
public class GeneratedFooController {

  private final FooServiceAdapter serviceAdapter;
  private final CamundaAuthenticationProvider authenticationProvider;

  public GeneratedFooController(
      final FooServiceAdapter serviceAdapter,
      final CamundaAuthenticationProvider authenticationProvider) {
    this.serviceAdapter = serviceAdapter;
    this.authenticationProvider = authenticationProvider;
  }

  public ResponseEntity<FooResult> operationName(final FooRequest request) {
    final var authentication = authenticationProvider.getCamundaAuthentication();
    return serviceAdapter.operationName(request, authentication);
  }
}
```

The adapter interface receives `CamundaAuthentication` as its final parameter on every method:

```java
public interface FooServiceAdapter {
  ResponseEntity<FooResult> operationName(FooRequest request, CamundaAuthentication authentication);
}
```

This is a deliberate design choice — **authentication** (who is the caller?) and
**authorization** (is the caller allowed?) are distinct concerns handled at different layers:

- **Authentication** is a Spring Security filter-chain concern. The `SecurityContextHolder`
  thread-local is populated before the controller method runs. The controller simply reads it
  via `CamundaAuthenticationProvider.getCamundaAuthentication()`.
- **Authorization** is a service-layer concern. Services that enforce permissions accept
  `CamundaAuthentication` as a parameter and use it internally (e.g.,
  `userServices.createUser(request, authentication)`). Services that perform no authorization
  checks (like `TopologyServices.getTopology()`) simply don't reference the parameter.

Because the generator cannot know which operations require authorization, every adapter method
receives the authentication context uniformly. Adapter implementations for unauthenticated
endpoints (e.g., topology, status, license) simply ignore the parameter — the cost is one unused
argument, which is negligible compared to the alternative of maintaining per-endpoint metadata
about which operations need auth.

> **Future candidate:** The set of unprotected API paths is currently hand-maintained in
> `WebSecurityConfig.UNPROTECTED_API_PATHS` (e.g., `/v2/license`, `/v2/setup/user`,
> `/v2/status`). The spec already declares `security: []` on unauthenticated operations — this
> is a candidate for spec-driven generation, producing the `UNPROTECTED_API_PATHS` set directly
> from the OpenAPI source instead of duplicating path knowledge in a separate configuration class.

### Spec-driven annotations and generation control

The generator derives controller behavior from OpenAPI `x-` extensions declared at the operation
level in the spec YAML files. This keeps behavioral metadata in the spec (its single source of
truth) rather than in hardcoded lookup maps inside the generator.

#### `x-code-generation: skip` (no longer used)

This extension was originally designed for operations that could not be reduced to the standard
request/response controller pattern (server-sent event streaming, binary streaming downloads).
These operations now have generated controllers with the same universal delegate pattern — the
protocol-level complexity is contained within the adapter implementation rather than requiring
hand-written controller methods.

No operations in the spec currently declare `x-code-generation: skip`. The generator still
supports it as a safety valve for future operations that genuinely cannot be adapted, but it is
not expected to be needed.

#### `requestBody.required`

The OpenAPI `requestBody.required` field controls whether Spring enforces a non-null body. Search
endpoints declare `required: false` so callers can POST with an empty body to get default results:

```yaml
requestBody:
  required: false       # → @RequestBody(required = false)
  content:
    application/json:
      schema:
        $ref: '#/components/schemas/JobSearchQuery'
```

The generator reads the `required` field during spec parsing and emits
`@RequestBody(required = false)` when it is `false`. When `required` is `true` or absent, the
annotation is emitted without the parameter (Spring's default is `required = true`).

#### `x-requires-secondary-storage`

Operations that read from or write to secondary storage (Elasticsearch/OpenSearch) declare
`x-requires-secondary-storage: true`:

```yaml
/jobs/search:
  post:
    x-requires-secondary-storage: true
    operationId: searchJobs
```

The generator emits `@RequiresSecondaryStorage` on the controller method. This annotation
triggers a Spring AOP interceptor that returns 503 when the secondary storage is unavailable,
rather than letting the call fail deep in the service layer.

#### `x-spring-conditional` and `x-spring-profile`

Some controllers are only active under certain deployment conditions. The spec declares these
at the operation level:

```yaml
/groups:
  post:
    x-spring-conditional: io.camunda.authentication.ConditionalOnCamundaGroupsEnabled
    operationId: createGroup
```

```yaml
/authentication/me:
  get:
    x-spring-profile: consolidated-auth
    x-spring-conditional: io.camunda.spring.utils.ConditionalOnSecondaryStorageEnabled
    operationId: getAuthentication
```

The generator collects unique `x-spring-conditional` and `x-spring-profile` values across all
operations in a tag and emits them as class-level annotations on the generated controller:

```java
@CamundaRestController
@RequestMapping("/v2")
@ConditionalOnCamundaGroupsEnabled          // from x-spring-conditional
public class GeneratedGroupController { ... }
```

```java
@CamundaRestController
@RequestMapping("/v2")
@Profile("consolidated-auth")               // from x-spring-profile
@ConditionalOnSecondaryStorageEnabled       // from x-spring-conditional
public class GeneratedAuthenticationController { ... }
```

This replaces hand-maintained conditional annotations on legacy controllers with spec-driven
declarations that are consumed mechanically by the generator.

#### v1 backward compatibility (User Task)

The User Task API is the only tag that requires v1 backward compatibility. On `main`, the
hand-written `UserTaskController` serves both `/v1/user-tasks/*` and `/v2/user-tasks/*` via a
dual-path `@RequestMapping`:

```java
@RequestMapping(path = {"/v1/user-tasks", "/v2/user-tasks"})
public class UserTaskController { ... }
```

The generated controller preserves this by emitting a dual-path class-level `@RequestMapping`
when the tag is `UserTask`:

```java
@CamundaRestController
@RequestMapping(path = {"/v1", "/v2"})      // v1 backward compat — UserTask only
public class GeneratedUserTaskController { ... }
```

All other 33 generated controllers emit the standard single-path annotation:

```java
@CamundaRestController
@RequestMapping("/v2")
public class GeneratedFooController { ... }
```

This is implemented as a tag-name check in `renderUniversalController()`:

```java
if ("UserTask".equals(ctrl.tagPascal())) {
    sb.append("@RequestMapping(path = {\"/v1\", \"/v2\"})\n");
} else {
    sb.append("@RequestMapping(\"/v2\")\n");
}
```

**Why a generator special case rather than a spec extension:** Only one tag out of 34 requires
v1 support, and the v1 API is a legacy compatibility concern that will eventually be removed.
Adding a spec-level extension (`x-v1-compat: true`) would be over-engineering for a single
hard-coded case. If additional tags ever need v1 support (unlikely — the v1 API is deprecated),
the check can be extended to a set lookup or migrated to a spec extension at that point.

**Scope of v1 compatibility:** The v1 path prefix applies to all 9 User Task endpoints
(search, get, assign, unassign, complete, update, create, save draft, delete draft). The
request and response shapes are identical between v1 and v2 for User Task — the prefix is the
only difference. No per-endpoint v1/v2 routing logic is needed.

### Incompatibility detection (response DTOs) — transitional

In the transitional state (Stages 1–2), the generator detected schemas where the strict
contract's Java types could not be directly assigned to the protocol model's setter types and
skipped `toProtocol()` mapper generation for those schemas. These incompatibilities (inline enums,
`uniqueItems` arrays, map narrowing, `format: uri` fields) existed because two parallel type
hierarchies represented the same JSON shape with different Java types.

With the unified type pipeline, the protocol model is eliminated. Strict contracts are serialized
directly. The incompatibilities disappear — they were artifacts of the dual-pipeline architecture,
not of the HTTP contract.

### Generation wiring

- OpenAPI source: `zeebe/gateway-protocol/src/main/proto/v2/*.yaml`
- Generator: `gateways/gateway-mapping-http/tools/GenerateContractMappingPoc.java`
- Maven phase: `generate-sources` via `exec-maven-plugin`
- Strict contract output: `gateways/gateway-mapping-http/src/main/java/.../search/contract/generated/`
- Controller output: `zeebe/gateway-rest/src/main/java/.../controller/generated/`

## Design rationale

### What the adapter layer buys

1. **Mechanical boilerplate abstracted to a generated layer.**
   Key coercion (`Long` → `String`), nested traversal, and field projection are derived from
   schema semantics and generated. The adapter author never touches `KeyUtil` — the generated
   strict contract accepts the raw entity value and coerces internally.

2. **Null handling surfaced in the IDE via the type system.**
   The step builder forces the developer through every required field before `build()` is
   reachable. Each required step demands an explicit `FieldPolicy<T>` parameter — typically
   `ContractPolicy.requiredNonNull()`. This is a deliberate confrontation: the developer must
   acknowledge "what happens if this field is null at runtime" for every required field, at the
   moment they wire it. In the hand-mapped code, null handling is absent — the mapping compiles
   silently and NPEs surface in integration tests or production.

### The per-field tradeoff

The hand-written character budget per field is roughly constant before and after. What changes is
what that budget buys:

```java
// Before: mechanical noise, hidden null assumption
.processDefinitionKey(KeyUtil.keyToString(entity.processDefinitionKey()))

// After: automatic coercion, explicit null contract
.processDefinitionKey(entity.processDefinitionKey(), ContractPolicy.requiredNonNull())
```

The old per-field overhead is accidental complexity (how to coerce). The new per-field overhead is
semantic intent (what to do about null). The generated layer absorbs the mechanical work; the
adapter line becomes a policy declaration.

### Async write endpoints and `.join()`

The hand-written controllers return `CompletableFuture<ResponseEntity<Object>>` for ~98 write
endpoints that route through the Zeebe broker. The generated API interfaces declare synchronous
`ResponseEntity<Void>` — because the OpenAPI spec defines 204 responses with no body, and the
async/sync distinction is invisible to API consumers.

The generated controller implementations bridge this by calling `.join()` on the broker future
inside `RequestExecutor.executeSync()`:

```java
public ResponseEntity<Void> cancelProcessInstance(...) {
    return RequestExecutor.executeSync(
        () -> services.cancelProcessInstance(key, auth));
}
```

**Why this is correct:** With Java 21 virtual threads (enabled in this project), `.join()` unmounts
the virtual thread during the broker round-trip. The underlying platform thread is released — zero
platform threads are held while waiting. This is performance-equivalent to the native
`CompletableFuture` return approach.

### Type alignment: strict contracts as the serialization type

Strict contract DTOs are the types serialized to JSON by Jackson. There is no `toProtocol()`
translation layer. The pipeline is:

```
entity → adapter → GeneratedXxxStrictContract
  → StrictSearchQueryResult<GeneratedXxxStrictContract>
  → ResponseEntity<Object>
  → Jackson serializes the strict contract record directly to JSON
```

Record component names match the JSON field names defined in the OpenAPI spec (both use
camelCase). The type chain is compile-time verified for null-safety via the step builder;
runtime correctness is validated by 601 e2e API tests.

## Shared policy operations

Recurring operation classes observed in `SearchQueryResponseMapper` are now policy-layer concerns,
supported by reusable utilities in `ContractPolicy`:

- **Required non-null enforcement:** `ContractPolicy.requiredNonNull()`
- **String policy:** `ContractPolicy.isNotBlank()`, `ContractPolicy.blankToNull()`
- **Null collection normalization:** `ContractPolicy.nullToEmptyList()`,
  `ContractPolicy.nullToEmptyMap()`
- **Defaulting:** `ContractPolicy.defaultIfNull(defaultValue)`

## Current implementation status

### Generation coverage

|          Artifact type          | Count |                            Notes                             |
|---------------------------------|-------|--------------------------------------------------------------|
| Strict contract DTOs            | 357   | All schemas with properties                                  |
| Result mappers (toProtocol)     | 0     | Eliminated — strict contracts are the serialization type     |
| Search query request mappers    | 31    | Mechanical filter/sort/page decomposition                    |
| Search query response mappers   | 53    | 31 wrappers + 22 single-entity delegations                   |
| Generated controllers           | 34    | All tags — universal delegate pattern                        |
| Generated endpoints             | 182   | All spec operations — 0 skips                                |
| Service adapter interfaces      | 34    | One per tag                                                  |
| Service adapter implementations | 34    | `Default*ServiceAdapter` — extracted from legacy controllers |

### Generated controllers (34 controllers, 182 endpoints)

|               Controller                | Endpoints |
|-----------------------------------------|-----------|------------------------------------|
| GeneratedAdHocSubProcessController      | 1         |
| GeneratedAuditLogController             | 2         |
| GeneratedAuthenticationController       | 1         |
| GeneratedAuthorizationController        | 5         |
| GeneratedBatchOperationController       | 6         |
| GeneratedClockController                | 2         |
| GeneratedClusterController              | 2         |
| GeneratedClusterVariableController      | 9         |
| GeneratedConditionalController          | 1         |
| GeneratedDecisionDefinitionController   | 4         |
| GeneratedDecisionInstanceController     | 4         |
| GeneratedDecisionRequirementsController | 3         |
| GeneratedDocumentController             | 5         |
| GeneratedElementInstanceController      | 4         |
| GeneratedExpressionController           | 1         |
| GeneratedGlobalListenerController       | 5         |
| GeneratedGroupController                | 15        |
| GeneratedIncidentController             | 5         |
| GeneratedJobController                  | 11        |
| GeneratedLicenseController              | 1         |
| GeneratedMappingRuleController          | 5         |
| GeneratedMessageController              | 2         |
| GeneratedMessageSubscriptionController  | 2         |
| GeneratedProcessDefinitionController    | 8         |
| GeneratedProcessInstanceController      | 17        |
| GeneratedResourceController             | 4         |
| GeneratedRoleController                 | 17        |
| GeneratedSetupController                | 1         |
| GeneratedSignalController               | 1         |
| GeneratedSystemController               | 2         |
| GeneratedTenantController               | 20        |
| GeneratedUserController                 | 5         |
| GeneratedUserTaskController             | 9         | v1 backward compat (`/v1` + `/v2`) |
| GeneratedVariableController             | 2         |

### Runtime validation

The generated controller layer has been validated end-to-end:

- **601 Playwright API tests passed** (0 failures) from `qa/c8-orchestration-cluster-e2e-test-suite/`
- All 182 endpoints serve correct HTTP responses (status codes, response shapes)
- Server runs with H2 in-memory database, RDBMS exporter, auth disabled
- Legacy hand-written controllers remain in the codebase but are superseded at runtime
  by the generated controllers (Spring route registration precedence)

### Generation summary (182 operations in spec)

All 182 operations get generated controllers — there are no exclusions and no classification
step. Every operation is treated uniformly: the generator produces a controller method that
delegates to the service adapter interface. `activateJobs` (async observer) and `getDocument`
(binary streaming) are handled by their adapter implementations rather than requiring
hand-written controller methods.

## What this solves

- **Compile-time contract enforcement**: required fields must be explicitly handled in the adapter;
  missing handling is a compile error via the step-builder.
- **Guaranteed traversal completeness**: coverage is derived from the schema graph, not from what
  an engineer remembered to traverse.
- **Maintenance reduction**: contract evolution is handled by regeneration instead of hand-edits.
- **Lower drift risk**: generation and compilation surface schema deltas early, instead of silent
  runtime omissions.
- **Separation of concerns**: business policy (semantic validation) in `RequestMapper`
  validators, execution logic in service adapters, mechanical mapping in generated code. Each
  layer has clear boundaries.
- **Spec-driven endpoint creation**: define the YAML, regenerate, implement the scaffolded
  adapter stubs. No hand-written controller code.
- **Uniform controller patterns**: all controllers follow the same structural pattern — validate
  then delegate — regardless of endpoint shape or which developer wrote the original.
- **100% controller coverage**: every operation gets a generated controller, including
  streaming (SSE) and binary download endpoints. All 182 spec operations are covered with
  zero exclusions.
- **Clear new-endpoint workflow**: a new endpoint produces compile errors in the adapter scaffold
  (TODO stubs), guiding the developer to exactly the methods that need implementation.

## What remains to reach the target architecture

### Unified type pipeline

The generated controllers return `ResponseEntity<Object>`, with the actual runtime objects being
strict contract DTOs (`StrictSearchQueryResult<GeneratedXxxStrictContract>` for search,
`GeneratedXxxStrictContract` for single-entity responses). Jackson serializes these directly.

The `toProtocol()` result mapper layer (145 generated files) has been deleted. Adapters call
`SearchQueryResponseMapper` directly for search response mapping.

**Completed — `@JsonProperty` annotations (complete):** All strict contract record components
now carry `@JsonProperty("fieldName")` annotations for explicit field-name documentation and
protection against refactoring renames.

**Completed — `SearchQueryResponseMapper` migration (complete):** All ~22 custom methods
(statistics, `toCamundaUser`, `toDecisionInstanceGetQueryResponse`, sequence flows, usage
metrics, incident statistics, element statistics) now return strict contract types. Zero
protocol model imports remain in `SearchQueryResponseMapper` and
`DecisionInstanceContractAdapter`.

**Completed — ProtocolBridge elimination (complete):** All three adapters
(`DefaultProcessInstanceServiceAdapter`, `DefaultDecisionDefinitionServiceAdapter`,
`DefaultAuthorizationServiceAdapter`) call `RequestMapper` strict contract overloads directly.
Zero `ProtocolBridge` references remain in source.

Remaining step to complete the unified pipeline:
1. Delete superseded hand-written controllers (36 files — cleanup).

### Delete legacy hand-written controllers

36 hand-written controllers remain in the codebase. They are superseded at runtime by the
generated controllers (Spring route registration precedence). Deleting them is a cleanup task
that can be done incrementally per tag, once each generated replacement is validated.

### Eliminate service-layer coupling from the generator (Stage 4 — complete)

**Completed — dead Phase 4 classification pipeline (removed):** The 10 hand-maintained lookup
maps (`MUTATION_VOID_HINTS`, `MUTATION_RESPONSE_HINTS`, `GET_BY_KEY_HINTS`, etc.) and the
classification pipeline that consumed them were dead code. Removed (~1000 lines).

**Completed — Phase 3 search query mapper tables (removed):** The three hand-maintained tables
(`REQUEST_ENTRIES`, `RESPONSE_WRAPPER_ENTRIES`, `SINGLE_ENTITY_ENTRIES`) that encoded
service-layer implementation knowledge as string literals have been eliminated.

All mechanical methods from `GeneratedSearchQueryRequestMapper` and
`GeneratedSearchQueryResponseMapper` were inlined into the hand-written
`SearchQueryRequestMapper` and `SearchQueryResponseMapper`. The generated mapper files were
deleted and the Phase 3 generation code was removed from the generator (~562 lines).

The 22 `Default*ServiceAdapter` implementations were updated to reference the hand-written
mappers directly. All 1606 tests pass with 0 failures.

`REQUEST_ENTRIES` and the `RequestEntry` record are retained — they drive Phase 3.5 (search
query request DTO generation), which is spec-adjacent work that reads filter/sort/page
structure from the search query entries.

## Implementation roadmap

Each stage is delivered as a separate branch to allow incremental review and evaluation.

### Stage 1 — Response slice (complete)

Proves the generation pipeline and null-safety enforcement for a representative subset of response
verticals. Self-contained within `gateway-mapping-http`.

**Delivered:**
- Generated strict contract DTOs and result mappers for the POC verticals.
- Hand-written contract adapters with explicit `FieldPolicy` per required field.
- Shared policy utilities in `ContractPolicy`.
- Tests per vertical: happy-path mapping, required non-null fails when source is null, required
nullable allows null.

### Stage 2 — Complete response generation (complete)

Extended Stage 1 to full schema coverage and added JSpecify null-safety tooling.

**Delivered:**
- 381 strict contract DTOs, 145 result mappers generated.
- JSpecify annotations (`@NullMarked`, `@Nullable`) in generated output.
- Search query request and response mapper generation (Phase 3).

### Stage 3 — Universal controller generation + unified type pipeline (complete)

Extends Stage 2 to generate thin controllers for **all** spec operations, with direct
service adapter delegation, and unifies the two code generation pipelines so that strict
contracts are the serialization type.

**Delivered — controller generation (complete):**
- Universal delegate pattern: every generated controller method calls
`serviceAdapter.operationName(request, authentication)` directly.
- 34 generated controllers, 182 endpoints — 100% spec coverage, 0 skips.
- `ServiceAdapter` interfaces per tag (34).
- 34 `Default*ServiceAdapter` implementations extracted from legacy controllers.
- All 182 operations treated uniformly — no classification step in the generator.
- Sub-resource searches for parent-keyed search endpoints.
- Multipart/form-data support (3 operations: deployments, documents, user task forms).
- Binary streaming (`getDocument` via `StreamingResponseBody`).
- Async observer (`activateJobs` via SSE SseEmitter).
- All spec-driven annotations: `x-requires-secondary-storage`, `x-spring-conditional`,
`x-spring-profile`, `requestBody.required`.
- `ResponseEntity<Object>` return type for all non-Void methods (avoids `checkcast` failures
from `SearchQueryResponseMapper`'s `<T> T adaptType()` pattern).
- Runtime-validated: 601 Playwright e2e API tests passed, 0 failures.

**Delivered — unified type pipeline (complete):**
- Adapters call `SearchQueryResponseMapper` directly for search response mapping.
- 145 `toProtocol()` result mapper files deleted (dead code). Generator no longer produces them.
- Strict contracts (`GeneratedXxxStrictContract`) are the runtime serialization type — Jackson
serializes them directly to JSON. Record component names match API field names.
- `StrictSearchQueryResult<GeneratedXxxStrictContract>` flows through the full pipeline:
Entity → ContractAdapter → StrictContract → StrictSearchQueryResult → ResponseEntity → JSON.
- Re-validated: 601 Playwright e2e API tests passed, 0 failures after pipeline change.

**Delivered — polymorphic oneOf schemas (complete):**
- Generator now parses `oneOf` refs (new `oneOfRefs` field on `Node` record).
- Sealed interfaces generated for all polymorphic schemas with `@JsonTypeInfo(use = DEDUCTION)`
and `@JsonSubTypes` — Jackson natively deduces the correct branch record from unique fields.
- Branch records implement their parent sealed interface (e.g.,
`GeneratedAuthorizationIdBasedRequestStrictContract implements
GeneratedAuthorizationRequestStrictContract`).
- 50 sealed interfaces generated; 0 protocol model imports in all 34 generated controllers.
- `SearchQueryPageRequest` excluded (flat page DTO retained — pagination variants share `limit`
field, making DEDUCTION unreliable; post-deserialization logic determines variant from non-null
field pattern).

**Delivered — spec-derived constraint validation and validator deduplication (complete):**

The generator now parses OpenAPI constraint annotations (`minimum`, `maximum`, `minLength`,
`maxLength`, `pattern`, `minItems`, `maxItems`) and emits validation checks in request DTO
compact constructors. This is the first wall of validation — constraints that are expressible
in the spec are enforced at deserialization time, before any hand-written code runs.

- Generator extended: `Node` record with 7 constraint fields, `Constraints` record with
  `hasAny()` method, `resolveConstraints()` following `$ref`/`allOf` chains.
- 62 request-path strict contract DTOs emit spec-derived constraint checks in constructors.
  Response-only schemas excluded via `discoverResponseOnlySchemas()` heuristic.
- Constraint types: `isBlank` guard, `length()` bounds, `matches()` pattern, `<`/`>` range,
  `size()` bounds.
- Hand-written validators deduplicated: spec-covered checks removed from 7 validator files
  (`ResourceRequestValidator`, `EvaluateDecisionRequestValidator`,
  `ProcessInstanceRequestValidator`, `ElementRequestValidator`, `DocumentValidator`,
  `UserTaskRequestValidator`, `TagsValidator`).
- Two validators (`ResourceRequestValidator`, `EvaluateDecisionRequestValidator`) are now
  no-ops that return `Optional.empty()`.
- MCP gateway path retains spec-derivable checks in
  `validateSimpleCreateProcessInstanceRequest` because that path bypasses strict contracts.
- Validator tests updated: spec-covered test cases removed or converted to pass-through
  assertions. `RequestMapperTest` updated for changed validation behavior.
- 154 unit tests pass, BUILD SUCCESS.

**Delivered — strict contract overloads on `RequestMapper` and ProtocolBridge elimination (complete):**

`RequestMapper` now has strict contract overloads alongside the original protocol model methods.
The strict contract overloads read record fields directly and construct service-layer request
types without protocol model intermediaries. Overloads cover: resource deletion, document
operations, signal broadcast, process instance cancel/modify/migrate/create, decision evaluation,
and authorization create/update.

All three adapters (`DefaultProcessInstanceServiceAdapter`,
`DefaultDecisionDefinitionServiceAdapter`, `DefaultAuthorizationServiceAdapter`) that previously
used `ProtocolBridge.toProtocol()` (Jackson `convertValue`) now call `RequestMapper` strict
contract overloads directly. Zero `ProtocolBridge` references remain in source.

`AuthorizationMapper` has corresponding strict contract overloads for create/update authorization
requests. `AuthorizationRequestValidator` and `ProcessInstanceRequestValidator` have strict
contract validation overloads.

`RequestMapper` methods read strict contract fields directly — no Jackson `convertValue` bridge,
no protocol model types in the request path. Semantic validation (multi-tenancy rules, runtime
config limits, cross-field business rules) stays in the hand-written validators called by
`RequestMapper`, operating on fields read from the strict contract.

- The hand-written controllers remain in the tree (with `@CamundaRestController` commented out)
  as reference until the refactor is fully validated. They are not wired at runtime.

### Validation architecture: where semantic checks live

The generated architecture defines two validation layers:

1. **Spec-derived constraints** — enforced in strict contract constructors at deserialization
   time. These are mechanical checks derivable from the OpenAPI schema: required fields,
   min/max ranges, string patterns, length bounds, collection size bounds.

2. **Semantic validation** — business rules not expressible in the spec. These include:
   cross-field validation, runtime-config-dependent checks, date/duration format parsing,
   deep nested instruction validation, and domain-specific format rules. These run inside
   `RequestMapper`, called by the service adapter.

#### Request flow

```
Controller.op(strictContract)
  → ServiceAdapter.op(strictContract, auth)
    → [toProtocolModel(strictContract)]            ← some adapters, transitional
    → RequestMapper.toOp(protocolModel, config)
      → hand-written validator (semantic checks)   ← VALIDATION HAPPENS HERE
      → map to service request
```

#### Why semantic validation lives in `RequestMapper`

1. **Protocol model dependency.** The hand-written validators operate on protocol model types
   (`ProcessInstanceCreationInstructionByKey`, `ClockPinRequest`, etc.). A separate validation
   layer accepting strict contract types would require either:
   - Rewriting every check to read strict contract fields (substantial migration).
   - Converting strict contract → protocol model before validation (defeats the purpose).
2. **Runtime configuration.** Several semantic checks depend on injected configuration:
   - `MessageRequestValidator.correlationKey` uses `maxNameFieldLength` from config.
   - `MultiTenancyValidator` uses `multiTenancyEnabled` flag from config.
     Interfaces with default methods have no constructor injection.
3. **Bidirectional data flow.** Some validators return parsed values consumed by the mapper:
   - `RequestValidator.validateDate()` returns `OffsetDateTime` — the mapper uses the parsed
     value. Moving validation to a separate layer would require parsing twice or changing the
     validation return type.
4. **Nested instruction validation.** Process instance modification, migration, and move
   instructions have deeply nested validation (mapping instructions, activate/terminate/move
   with cross-field rules). These operate on list elements of protocol model types. The strict
   contract for the outer request doesn't expose the same nested type hierarchy.
5. **Two entry points.** `RequestMapper` is called by both the REST path (via service adapters)
   and the MCP path (via `SimpleRequestMapper`). Any validation placed before the adapter
   would only protect the REST path, leaving the MCP path unprotected.

Semantic validation stays in the hand-written validators called by `RequestMapper`. This is the
pragmatic architectural choice:

- The validators are thin, focused, and well-tested.
- They colocate with the mapping logic that consumes the validated data.
- They serve both entry points (REST and MCP) uniformly.

The role of each layer:

|                    Layer                     |                                 What it validates                                  |            When it runs             |
|----------------------------------------------|------------------------------------------------------------------------------------|-------------------------------------|
| Strict contract constructor                  | Spec-derived constraints (required, min/max, pattern, length, size)                | Deserialization (before controller) |
| Hand-written validators (in `RequestMapper`) | Semantic rules: cross-field, config-dependent, format parsing, nested instructions | Inside service adapter, pre-mapping |

**Delivered — dead Phase 4 pipeline removal (complete):**

The generator previously contained a "Phase 4" classification pipeline that classified operations
into patterns (SEARCH, GET_BY_KEY, MUTATION_VOID, MUTATION_RESPONSE, STATISTICS) using
`classifyEndpoint()` and 10 hand-maintained lookup maps, and built `ControllerEntry` objects —
but this pipeline wrote no files. The universal controller pipeline (Phase 5) had already
superseded it. ~1000 lines of dead code removed:
- `buildControllerEntriesFromSpec()`, `classifyEndpoint()`, all old endpoint builders and
renderers.
- `ControllerEndpoint`, `ControllerEntry`, `EndpointKind` and 5 hint record types.
- 10 lookup maps including `SKIPPED_OPERATIONS` (92 entries that gated only the dead pipeline).
- All 1606 unit tests pass after removal.

**Remaining:**
- Delete superseded hand-written controllers (36 files — cleanup after full validation).

### Stage 4 — Eliminate service-layer coupling from the generator (complete)

The Phase 4 classification pipeline (10 lookup maps, ~1000 lines of dead code) and the Phase 3
search query mapper tables (`RESPONSE_WRAPPER_ENTRIES`, `SINGLE_ENTITY_ENTRIES`) have been
removed. The generated mapper files (`GeneratedSearchQueryRequestMapper`,
`GeneratedSearchQueryResponseMapper`) have been deleted.

All mechanical methods were inlined into the hand-written `SearchQueryRequestMapper` and
`SearchQueryResponseMapper`. The 22 `Default*ServiceAdapter` files reference the hand-written
mappers directly. Phase 3 generation code removed from the generator (~562 lines).

`REQUEST_ENTRIES` and `RequestEntry` are retained for Phase 3.5 (search query request DTO
generation).

**Delivered:**
- 53 response mapper methods inlined from `GeneratedSearchQueryResponseMapper` into
`SearchQueryResponseMapper`.
- 31 non-strict request mapper methods inlined + 33 strict overloads + 1 validation helper
added to `SearchQueryRequestMapper` from `GeneratedSearchQueryRequestMapper`.
- 22 adapter files updated to import/call hand-written mappers directly.
- 2 generated mapper files deleted.
- Phase 3 generation code removed: `renderSearchQueryResponseMapper()`,
`renderSearchQueryRequestMapper()`, `ResponseWrapperEntry`, `SingleEntityEntry`,
`RESPONSE_WRAPPER_ENTRIES`, `SINGLE_ENTITY_ENTRIES` — 562 lines removed.
- Net change: -2209 lines across 27 files.
- All 1606 tests pass, 0 failures.

## Non-goals

- Do not force the data layer to use API DTOs directly.
- Do not move search-domain entity semantics into the API layer.
- Do not mix policy logic back into `SearchQueryResponseMapper`.
- Do not encode service-layer knowledge (query builders, entity types, filter mappers) in the
  generator. The generator reads only the OpenAPI spec.

## Search query mapper generation (historical — Stage 4 removed this)

> **Note:** This section documents the Phase 3 generated mapper approach that was used during
> Stage 3 development. Stage 4 inlined all generated methods into the hand-written mappers and
> removed the generation code. Retained for architectural context.

### Problem

`SearchQueryResponseMapper` (962 lines) and `SearchQueryRequestMapper` (1081 lines) contained
~80 purely mechanical methods that followed identical patterns:

- **Response wrappers**: `SearchQueryResult<Entity> → StrictSearchQueryResult<Contract>` via
  contract adapters — every method was structurally identical except for the entity, adapter, and
  contract types.
- **Single-entity delegations**: `Entity → Adapter.adapt(entity) → Contract` — one-line pass-
  throughs that added no logic.
- **Request decomposers**: `SearchQuery → page + sort + filter → buildSearchQuery()` — every method
  decomposed the request identically using `SearchQuerySortRequestMapper`,
  `SearchQueryFilterMapper`, and `SearchQueryBuilders`.

Roughly 15–20 methods per file had genuine custom logic (statistics aggregations, special
validation, `toCamundaUser`, etc.) and could not be derived from convention.

### Decision

Extend `GenerateContractMappingPoc.java` with Phase 3: generate two aggregate mapper classes
containing all mechanical methods.

|            Generated file            |             Package              |           Methods            |                        Driver                        |
|--------------------------------------|----------------------------------|------------------------------|------------------------------------------------------|
| `GeneratedSearchQueryResponseMapper` | `…contract.generated`            | 31 wrappers + 22 delegations | Registry of response wrapper / single-entity entries |
| `GeneratedSearchQueryRequestMapper`  | `…search` (same as hand-written) | 31 request decomposers       | Registry of request entries                          |

The hand-written files (`SearchQueryResponseMapper`, `SearchQueryRequestMapper`) retain only their
non-mechanical methods and delegate mechanical calls to the generated classes. This preserves
backward compatibility for callers.

> **Historical note (Stage 4 completed).** The generated mapper classes and the registry tables
> that drove them (`RESPONSE_WRAPPER_ENTRIES`, `SINGLE_ENTITY_ENTRIES`) were Stage 3 migration
> scaffolding. Stage 4 inlined all generated methods into the hand-written
> `SearchQueryRequestMapper` and `SearchQueryResponseMapper`, deleted the generated files, and
> removed the generation code from the generator. See
> [Stage 4](#stage-4--eliminate-service-layer-coupling-from-the-generator-complete).

### Why same package for request mapper?

`GeneratedSearchQueryRequestMapper` is generated into `io.camunda.gateway.mapping.http.search`
(the same package as the hand-written mappers) because it needs access to package-private methods
in `SearchQuerySortRequestMapper`, `SearchQueryFilterMapper`, and `SearchQueryRequestMapper`
(`toSearchQueryPage`, `buildSearchQuery`). The response mapper has no such requirement and lives
in the `generated` sub-package.

### Alternatives considered

1. **Make all referenced methods public** — would require changing ~85 methods across
   `SearchQuerySortRequestMapper` and `SearchQueryFilterMapper`. Rejected as too broad a visibility
   change for internal wiring methods.
2. **Generate per-entity mapper files** (one file per entity) — cleaner separation but would create
   ~30 small files. Not worth the file sprawl for simple mechanical methods.
3. **Update all callers directly** (~129 call sites) to reference generated classes and remove
   hand-written methods entirely — clean but too large a change; deferred to a follow-up.

